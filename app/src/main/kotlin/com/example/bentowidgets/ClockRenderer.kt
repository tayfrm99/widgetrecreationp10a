package com.example.bentowidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object ClockRenderer {

    /**
     * @param tick false = mechanical movement (second hand beats 8 times a second, 0.75 deg per beat)
     *             true  = quartz-style tick: one 6 deg jump per second ("click, click")
     */
    fun render(ctx: Context, s: Cfg, w: Int, h: Int, nowMs: Long, tick: Boolean): Bitmap {
        val pal = Theme.palette(ctx, s)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = min(w, h).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val half = d / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // face
        p.style = Paint.Style.FILL
        p.color = pal.face
        c.drawPath(WidgetShapes.path(s.shape, RectF(cx - half, cy - half, cx + half, cy + half)), p)

        // time
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val sec = cal.get(Calendar.SECOND)
        val ms = cal.get(Calendar.MILLISECOND)
        val secQ = if (tick) sec.toDouble() else sec + (ms / 125) * 0.125
        val minF = cal.get(Calendar.MINUTE) + sec / 60.0
        val hourF = (cal.get(Calendar.HOUR) % 12) + minF / 60.0

        // ticks follow the outline of the shape, like the reference
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val th = Math.toRadians(i * 6.0)
            val r = WidgetShapes.boundary(s.shape, th, half) * 0.90f
            val quarter = i % 15 == 0
            val hour = i % 5 == 0
            val len = d * (if (quarter) 0.095f else if (hour) 0.055f else 0.030f)
            p.strokeWidth = d * (if (quarter) 0.020f else if (hour) 0.016f else 0.007f)
            p.color = if (hour) pal.onFace else ColorUtils.setAlphaComponent(pal.onFace, 110)
            val ux = sin(th).toFloat()
            val uy = -cos(th).toFloat()
            c.drawLine(cx + ux * (r - len), cy + uy * (r - len), cx + ux * r, cy + uy * r, p)
        }

        // hour + minute hands
        hand(c, p, cx, cy, hourF * 30.0, d * 0.26f, d * 0.03f, d * 0.042f, pal.onFace)
        hand(c, p, cx, cy, minF * 6.0, d * 0.37f, d * 0.03f, d * 0.036f, pal.onFace)

        // red second hand with tail and lollipop dot
        val sa = Math.toRadians(secQ * 6.0)
        val ux = sin(sa).toFloat()
        val uy = -cos(sa).toFloat()
        hand(c, p, cx, cy, secQ * 6.0, d * 0.37f, d * 0.10f, d * 0.012f, pal.second)
        p.style = Paint.Style.FILL
        p.color = pal.second
        c.drawCircle(cx + ux * d * 0.30f, cy + uy * d * 0.30f, d * 0.022f, p)

        // hub
        p.color = pal.onFace
        c.drawCircle(cx, cy, d * 0.036f, p)
        p.color = pal.second
        c.drawCircle(cx, cy, d * 0.022f, p)
        p.color = pal.face
        c.drawCircle(cx, cy, d * 0.008f, p)
        return bmp
    }

    private fun hand(
        c: Canvas, p: Paint, cx: Float, cy: Float, angleDeg: Double,
        len: Float, tail: Float, width: Float, color: Int,
    ) {
        val th = Math.toRadians(angleDeg)
        val ux = sin(th).toFloat()
        val uy = -cos(th).toFloat()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width
        p.color = color
        c.drawLine(cx - ux * tail, cy - uy * tail, cx + ux * len, cy + uy * len, p)
    }
}
