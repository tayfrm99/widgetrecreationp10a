package com.example.bentowidgets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Widgets can't animate by themselves, so this foreground service redraws them while the screen is on:
 *  - clock: 8 beats/s (mechanical) or 1 tick/s (battery saver / "tick" mode)
 *  - map:   4 fps calm, 10 fps live, 1 fps in battery saver
 * Everything stops when the screen turns off.
 */
class LiveService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var h: Handler
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var pm: PowerManager

    @Volatile private var s = Cfg()
    @Volatile private var screenOn = true
    @Volatile private var saver = false

    private val clockLoop = Runnable { runClock() }
    private val mapLoop = Runnable { runMap() }
    private val dataLoop = Runnable { runData() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> saver = pm.isPowerSaveMode
            }
            kick()
        }
    }

    override fun onCreate() {
        super.onCreate()
        pm = getSystemService(PowerManager::class.java)
        thread = HandlerThread("bento-frames").also { it.start() }
        h = Handler(thread.looper)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        s = Prefs.load(this)
        if (intent?.action == ACTION_REFRESH) {
            MapBase.clear()
            SkyData.invalidate()
        }
        saver = pm.isPowerSaveMode
        screenOn = pm.isInteractive
        if (!Widgets.any(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        kick()
        return START_STICKY
    }

    private fun kick() {
        h.removeCallbacks(clockLoop); h.post(clockLoop)
        h.removeCallbacks(mapLoop); h.post(mapLoop)
        h.removeCallbacks(dataLoop); h.post(dataLoop)
    }

    private fun idle() = !Widgets.any(this)

    // ---- clock --------------------------------------------------------------------------------

    private fun tickMode() = when (s.clockMode) {
        1 -> false
        2 -> true
        else -> saver
    }

    private fun runClock() {
        h.removeCallbacks(clockLoop)
        if (idle()) { stopSelf(); return }
        if (!screenOn || Widgets.ids(this, ClockWidget::class.java).isEmpty()) return
        val tick = tickMode()
        Frames.clock(this, s, System.currentTimeMillis(), tick)
        val step = if (tick) 1000L else 125L
        val now = System.currentTimeMillis()
        val next = (now / step + 1) * step
        h.postDelayed(clockLoop, (next - now).coerceAtLeast(15L) + 4L)
    }

    // ---- map ----------------------------------------------------------------------------------

    private fun runMap() {
        h.removeCallbacks(mapLoop)
        if (idle()) { stopSelf(); return }
        if (!screenOn || Widgets.ids(this, MapWidget::class.java).isEmpty()) return
        Frames.map(this, s, System.currentTimeMillis()) { h.post(mapLoop) }
        val period = when {
            saver -> 1000L
            s.mapAnim == 0 -> 60_000L
            s.mapAnim == 2 -> 100L
            else -> 250L
        }
        h.postDelayed(mapLoop, period)
    }

    // ---- live data (weather, aircraft) ----------------------------------------------------------

    private fun runData() {
        h.removeCallbacks(dataLoop)
        if (!screenOn || Widgets.ids(this, MapWidget::class.java).isEmpty()) return
        val cfg = s
        val dp = Frames.mapDp(this)
        io.execute { SkyData.refresh(cfg, dp) }
        h.postDelayed(dataLoop, 30_000L)
    }

    // ---- plumbing -----------------------------------------------------------------------------

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("live", "Widget live updates", NotificationManager.IMPORTANCE_MIN),
        )
        val n = Notification.Builder(this, "live")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Widgets are live")
            .setContentText("Keeps the clock and map moving while the screen is on")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        h.removeCallbacksAndMessages(null)
        thread.quitSafely()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_REFRESH = "com.example.bentowidgets.REFRESH"

        /** Starts the service only if there is a widget to animate. Safe to call from anywhere. */
        fun start(ctx: Context) = launch(ctx, null)

        fun refresh(ctx: Context) = launch(ctx, ACTION_REFRESH)

        private fun launch(ctx: Context, action: String?) {
            val app = ctx.applicationContext
            if (!Widgets.any(app)) return
            try {
                val i = Intent(app, LiveService::class.java)
                if (action != null) i.action = action
                ContextCompat.startForegroundService(app, i)
            } catch (e: Exception) {
                // Background start was refused by Android; opening the app or tapping a widget starts it.
            }
        }
    }
}
