package com.example.bentowidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WSize(val wPx: Int, val hPx: Int, val dpW: Float, val dpH: Float)

object Widgets {
    private val initialised = HashSet<Int>()

    fun ids(ctx: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    fun any(ctx: Context): Boolean =
        ids(ctx, ClockWidget::class.java).isNotEmpty() || ids(ctx, MapWidget::class.java).isNotEmpty()

    /** Render size: the widget's real size, capped so every frame stays small enough to ship over binder. */
    fun size(ctx: Context, mgr: AppWidgetManager, id: Int): WSize {
        val o = mgr.getAppWidgetOptions(id)
        val dpW = max(60, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)).toFloat()
        val dpH = max(60, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)).toFloat()
        val d = ctx.resources.displayMetrics.density
        var wp = dpW * d
        var hp = dpH * d
        val k = min(1f, 440f / max(wp, hp))
        wp *= k
        hp *= k
        return WSize(max(2, wp.roundToInt()), max(2, hp.roundToInt()), dpW, dpH)
    }

    fun push(ctx: Context, id: Int, bmp: Bitmap) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_image)
            rv.setImageViewBitmap(R.id.img, bmp)
            val first = synchronized(this) { initialised.add(id) }
            if (first) {
                val open = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                rv.setOnClickPendingIntent(R.id.root, open)
                mgr.updateAppWidget(id, rv)
            } else {
                mgr.partiallyUpdateAppWidget(id, rv)
            }
        } catch (e: RuntimeException) {
            // e.g. TransactionTooLargeException on a very large widget: skip this frame
        }
    }
}

/** One-shot renderers shared by the live service, the providers and the in-app preview. */
object Frames {
    fun clock(ctx: Context, s: Cfg, now: Long, tick: Boolean) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in Widgets.ids(ctx, ClockWidget::class.java)) {
            val z = Widgets.size(ctx, mgr, id)
            Widgets.push(ctx, id, ClockRenderer.render(ctx, s, z.wPx, z.hPx, now, tick))
        }
    }

    fun map(ctx: Context, s: Cfg, now: Long, onBase: () -> Unit) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val pal = Theme.palette(ctx, s)
        for (id in Widgets.ids(ctx, MapWidget::class.java)) {
            val z = Widgets.size(ctx, mgr, id)
            val base = MapBase.get(ctx, s, pal, z.wPx, z.hPx, z.dpW, onBase)
            Widgets.push(ctx, id, MapRenderer.frame(s, pal, base, z.wPx, z.hPx, z.dpW, now))
        }
    }

    /** Largest map widget width in dp (used to size the aircraft query). */
    fun mapDp(ctx: Context): Float {
        val mgr = AppWidgetManager.getInstance(ctx)
        return Widgets.ids(ctx, MapWidget::class.java).maxOfOrNull { Widgets.size(ctx, mgr, it).dpW } ?: 160f
    }
}
