package com.example.bentowidgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class Aircraft(val lat: Double, val lon: Double, val track: Float, val speedMs: Float, val t0: Long)

/** Live inputs for the sky layer. Defaults are used until the first fetch (or when offline). */
object SkyData {
    @Volatile var cloudCover = 40f      // %
    @Volatile var windDir = 270f        // degrees the wind blows FROM
    @Volatile var windSpeed = 12f       // km/h
    @Volatile var aircraft: List<Aircraft> = emptyList()
    @Volatile var aircraftTime = 0L
    @Volatile var weatherTime = 0L

    fun invalidate() {
        weatherTime = 0L
        aircraftTime = 0L
    }

    /** Called from a background thread. Cheap when nothing is due. */
    fun refresh(s: Cfg, viewDp: Float) {
        val now = System.currentTimeMillis()
        if (s.liveWeather && now - weatherTime > 15 * 60_000L) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${s.lat}&longitude=${s.lon}" +
                    "&current=cloud_cover,wind_speed_10m,wind_direction_10m"
                val cur = JSONObject(String(Net.get(url))).getJSONObject("current")
                cloudCover = cur.getDouble("cloud_cover").toFloat()
                windSpeed = cur.getDouble("wind_speed_10m").toFloat()
                windDir = cur.getDouble("wind_direction_10m").toFloat()
                weatherTime = now
            } catch (e: Exception) {
                weatherTime = now - 10 * 60_000L // retry in ~5 minutes
            }
        }
        if (s.planes && s.liveAircraft && now - aircraftTime > 60_000L) {
            try {
                val mPerDp = 156543.03 * cos(Math.toRadians(s.lat)) / 2.0.pow(s.zoom.toDouble())
                val radiusNm = (viewDp * mPerDp / 1852.0).coerceIn(30.0, 250.0).roundToInt()
                val url = "https://api.adsb.lol/v2/point/${s.lat}/${s.lon}/$radiusNm"
                val arr = JSONObject(String(Net.get(url, 10_000))).optJSONArray("ac")
                val out = ArrayList<Aircraft>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val lat = o.optDouble("lat", Double.NaN)
                        val lon = o.optDouble("lon", Double.NaN)
                        val track = o.optDouble("track", Double.NaN)
                        val gs = o.optDouble("gs", Double.NaN)
                        if (lat.isNaN() || lon.isNaN() || track.isNaN() || gs.isNaN()) continue
                        if (o.optString("alt_baro") == "ground") continue
                        out.add(Aircraft(lat, lon, track.toFloat(), (gs * 0.5144).toFloat(), now))
                    }
                }
                aircraft = out
                aircraftTime = now
            } catch (e: Exception) {
                // keep whatever we had; decorative flyovers fill in
            }
        }
    }
}

object SkyRenderer {
    private val puff: Bitmap by lazy {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            48f, 48f, 48f,
            intArrayOf(Color.WHITE, 0x66FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        Canvas(b).drawCircle(48f, 48f, 48f, p)
        b
    }

    private val planePath = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.16f, -0.85f, 0.16f, -0.45f, 0.14f, -0.10f)
        lineTo(1f, 0.38f); lineTo(1f, 0.56f); lineTo(0.14f, 0.30f)
        lineTo(0.12f, 0.72f); lineTo(0.42f, 0.92f); lineTo(0.42f, 1.05f)
        lineTo(0f, 0.95f)
        lineTo(-0.42f, 1.05f); lineTo(-0.42f, 0.92f); lineTo(-0.12f, 0.72f)
        lineTo(-0.14f, 0.30f); lineTo(-1f, 0.56f); lineTo(-1f, 0.38f); lineTo(-0.14f, -0.10f)
        cubicTo(-0.16f, -0.45f, -0.16f, -0.85f, 0f, -1f)
        close()
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // ---- clouds -------------------------------------------------------------------------------

    /** Soft streaky clouds drifting downwind. Density follows live cloud cover, direction follows live wind. */
    fun clouds(c: Canvas, w: Int, h: Int, now: Long, night: Boolean) {
        val cover = SkyData.cloudCover.coerceIn(0f, 100f)
        val count = (3 + cover / 100f * 11f).roundToInt()
        val rnd = java.util.Random(42)
        val to = Math.toRadians(SkyData.windDir + 180.0)
        val ux = sin(to)
        val uy = -cos(to)
        val speed = (2f + SkyData.windSpeed * 0.35f).coerceIn(3f, 16f) * w / 400f
        val t = now / 1000.0
        val ang = Math.toDegrees(atan2(uy, ux)).toFloat()
        val m = w * 0.6
        val spanX = w + 2 * m
        val spanY = h + 2 * m
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        val dim = if (night) 0.55f else 1f
        repeat(count) {
            val bx = rnd.nextFloat()
            val by = rnd.nextFloat()
            val size = w * (0.09f + rnd.nextFloat() * 0.12f)
            val sp = 0.7 + rnd.nextFloat() * 0.7
            val a = 0.16f + rnd.nextFloat() * 0.22f
            val tail = 3 + rnd.nextInt(3)
            val travel = t * speed * sp
            val x = (((bx * w + ux * travel + m) % spanX) + spanX) % spanX - m
            val y = (((by * h + uy * travel + m) % spanY) + spanY) % spanY - m
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            c.rotate(ang)
            for (k in 0 until tail) {
                val f = 1f - k * 0.17f
                val rx = size * 1.5f * f
                val ry = size * 0.55f * f
                val ox = -k * size * 0.7f
                p.alpha = (255 * a * f * dim).toInt().coerceIn(0, 255)
                c.drawBitmap(puff, null, RectF(ox - rx, -ry, ox + rx, ry), p)
            }
            c.restore()
        }
    }

    // ---- planes -------------------------------------------------------------------------------

    fun planes(c: Canvas, view: MapView, s: Cfg, w: Int, h: Int, now: Long) {
        var shown = 0
        val fresh = now - SkyData.aircraftTime < 5 * 60_000L
        if (s.liveAircraft && fresh) {
            for (ac in SkyData.aircraft) {
                if (shown >= 12) break
                val dist = ac.speedMs * (now - ac.t0) / 1000.0
                val br = Math.toRadians(ac.track.toDouble())
                val lat = ac.lat + dist * cos(br) / 111_320.0
                val lon = ac.lon + dist * sin(br) / (111_320.0 * cos(Math.toRadians(ac.lat)))
                val pt = view.toPx(lat, lon)
                if (pt.x < -20 || pt.x > w + 20 || pt.y < -20 || pt.y > h + 20) continue
                drawPlane(c, pt.x, pt.y, ac.track, w * 0.034f, w * 0.16f, 0.75f)
                shown++
            }
        }
        if (shown < 3) flyovers(c, w, h, now)
    }

    /** Bento-style decorative flyovers: a plane crosses the card every ~26 s and leaves a fading contrail. */
    private fun flyovers(c: Canvas, w: Int, h: Int, now: Long) {
        val period = 26_000L
        val life = 34_000L
        val slot = now / period
        val diag = hypot(w.toFloat(), h.toFloat())
        for (k in 0..1) {
            val sl = slot - k
            val r = java.util.Random(sl * 7919L + 13L)
            if (r.nextFloat() > 0.8f) continue
            val heading = r.nextFloat() * 360f
            val off = (r.nextFloat() - 0.5f) * 0.7f * w
            val elapsed = now - sl * period
            if (elapsed < 0 || elapsed > life) continue
            val u = elapsed / 20_000f // 0..1 crossing, then the trail shrinks away
            val len = diag * 1.25f
            val hr = Math.toRadians(heading.toDouble())
            val dx = sin(hr).toFloat()
            val dy = -cos(hr).toFloat()
            val px = -dy
            val py = dx
            val sx = w / 2f - dx * len / 2f + px * off
            val sy = h / 2f - dy * len / 2f + py * off
            val head = minOf(u, 1f) * len
            val tailPos = maxOf(0f, u * len - diag * 0.5f)
            if (head - tailPos > 4f) {
                trail(c, sx + dx * head, sy + dy * head, sx + dx * tailPos, sy + dy * tailPos, w * 0.040f, 0.85f)
            }
            if (u <= 1f) drawPlane(c, sx + dx * head, sy + dy * head, heading, w * 0.040f, 0f, 0f)
        }
    }

    private fun drawPlane(c: Canvas, x: Float, y: Float, heading: Float, size: Float, trailLen: Float, a: Float) {
        if (trailLen > 0f) {
            val hr = Math.toRadians(heading.toDouble())
            val dx = sin(hr).toFloat()
            val dy = -cos(hr).toFloat()
            trail(c, x - dx * size * 0.8f, y - dy * size * 0.8f, x - dx * trailLen, y - dy * trailLen, size, a)
        }
        // soft shadow then the white silhouette
        c.save()
        c.translate(x + size * 0.10f, y + size * 0.14f)
        c.rotate(heading)
        c.scale(size * 0.85f, size)
        fill.color = 0x40000000
        c.drawPath(planePath, fill)
        c.restore()
        c.save()
        c.translate(x, y)
        c.rotate(heading)
        c.scale(size * 0.85f, size)
        fill.color = Color.WHITE
        c.drawPath(planePath, fill)
        c.restore()
    }

    private fun trail(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, size: Float, a: Float) {
        val strong = (255 * a).toInt().coerceIn(0, 255)
        val soft = (strong * 0.28f).toInt()
        line.shader = LinearGradient(x0, y0, x1, y1, (soft shl 24) or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        line.strokeWidth = size * 0.55f
        c.drawLine(x0, y0, x1, y1, line)
        line.shader = LinearGradient(x0, y0, x1, y1, (strong shl 24) or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        line.strokeWidth = size * 0.18f
        c.drawLine(x0, y0, x1, y1, line)
        line.shader = null
    }
}
