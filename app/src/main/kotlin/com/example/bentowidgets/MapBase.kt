package com.example.bentowidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** Maps lat/lon to widget pixels. Tiles are 512 px (@2x) so 1 dp = 2 tile px at integer zoom. */
class MapView(s: Cfg, val w: Int, val h: Int, viewWdp: Float) {
    val z = floor(s.zoom).toInt().coerceIn(1, 17)
    private val n = 2.0.pow(z)
    val srcW = viewWdp * 2.0 * 2.0.pow((s.zoom - z).toDouble())
    val scale = w / srcW
    val srcH = h / scale
    val ax = if (s.offsetDot) 0.36 else 0.50
    val ay = if (s.offsetDot) 0.41 else 0.46
    private val cx = wx(s.lon)
    private val cy = wy(s.lat)
    val left = cx - ax * srcW
    val top = cy - ay * srcH

    fun wx(lon: Double) = (lon + 180.0) / 360.0 * T * n

    fun wy(lat: Double): Double {
        val sn = sin(Math.toRadians(lat.coerceIn(-85.0, 85.0)))
        return (0.5 - ln((1 + sn) / (1 - sn)) / (4 * PI)) * T * n
    }

    fun toPx(lat: Double, lon: Double) =
        PointF(((wx(lon) - left) * scale).toFloat(), ((wy(lat) - top) * scale).toFloat())

    val tilesN: Int get() = n.toInt()

    companion object {
        const val T = 512.0
    }
}

/** Builds (and caches) the static map bitmap behind the animated layers. Tiles come from CARTO / OpenStreetMap. */
object MapBase {
    private class Entry(val bmp: Bitmap, val complete: Boolean, val at: Long)

    private val cache = HashMap<String, Entry>()
    private val inflight = HashSet<String>()
    private val exec = Executors.newSingleThreadExecutor()

    fun clear() = synchronized(this) { cache.clear() }

    /** Returns the cached map (maybe null while loading) and starts a background build when needed. */
    fun get(ctx: Context, s: Cfg, pal: Palette, w: Int, h: Int, dpW: Float, onReady: () -> Unit): Bitmap? {
        val key = "${s.lat}|${s.lon}|${s.zoom}|${s.mapLabels}|${s.offsetDot}|${pal.night}|${pal.mapTint}|$w|$h|$dpW"
        val app = ctx.applicationContext
        val e = synchronized(this) { cache[key] }
        val stale = e == null || (!e.complete && System.currentTimeMillis() - e.at > 20_000)
        if (stale && synchronized(this) { inflight.add(key) }) {
            exec.execute {
                try {
                    val (bmp, ok) = build(app, s, pal, w, h, dpW)
                    synchronized(this) {
                        cache[key] = Entry(bmp, ok, System.currentTimeMillis())
                        if (cache.size > 6) cache.keys.filter { it != key }.forEach { cache.remove(it) }
                    }
                    onReady()
                } catch (e: Throwable) {
                    // leave the placeholder in place; retried on the next settings change
                } finally {
                    synchronized(this) { inflight.remove(key) }
                }
            }
        }
        return e?.bmp
    }

    private fun build(app: Context, s: Cfg, pal: Palette, w: Int, h: Int, dpW: Float): Pair<Bitmap, Boolean> {
        val view = MapView(s, w, h, dpW)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(pal.mapTint)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(pal.mapTint, PorterDuff.Mode.MULTIPLY)
        }
        val style = when {
            pal.night && s.mapLabels -> "dark_all"
            pal.night -> "dark_nolabels"
            s.mapLabels -> "voyager"
            else -> "voyager_nolabels"
        }
        val T = MapView.T
        val x0 = floor(view.left / T).toInt()
        val x1 = floor((view.left + view.srcW) / T).toInt()
        val y0 = floor(view.top / T).toInt()
        val y1 = floor((view.top + view.srcH) / T).toInt()
        var allowNet = true
        var complete = true
        val n = view.tilesN
        val size = (T * view.scale).toFloat()
        for (ty in y0..y1) {
            if (ty < 0 || ty >= n) continue
            for (tx in x0..x1) {
                val wrapped = ((tx % n) + n) % n
                val tile = try {
                    loadTile(app, style, view.z, wrapped, ty, allowNet)
                } catch (e: IOException) {
                    allowNet = false
                    null
                }
                if (tile == null) {
                    complete = false
                    continue
                }
                val dl = ((tx * T - view.left) * view.scale).toFloat()
                val dt = ((ty * T - view.top) * view.scale).toFloat()
                c.drawBitmap(tile, null, RectF(dl, dt, dl + size, dt + size), paint)
            }
        }
        return Pair(bmp, complete)
    }

    private fun loadTile(app: Context, style: String, z: Int, x: Int, y: Int, allowNet: Boolean): Bitmap? {
        val f = File(app.cacheDir, "tiles/$style-$z-$x-$y.png")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 14L * 86_400_000L
        if (fresh || (!allowNet && f.exists())) {
            BitmapFactory.decodeFile(f.path)?.let { return it }
        }
        if (!allowNet) return null
        return try {
            val bytes = Net.get("https://basemaps.cartocdn.com/rastertiles/$style/$z/$x/$y@2x.png")
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: IOException) {
            // fall back to an expired copy if we have one
            if (f.exists()) BitmapFactory.decodeFile(f.path) else throw e
        }
    }
}
