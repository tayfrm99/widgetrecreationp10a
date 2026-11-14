package com.example.bentowidgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils

object MapRenderer {
    private val arrow = Path().apply {
        moveTo(0f, -1f)
        lineTo(0.72f, 0.90f)
        lineTo(0f, 0.45f)
        lineTo(-0.72f, 0.90f)
        close()
    }

    fun frame(s: Cfg, pal: Palette, base: Bitmap?, w: Int, h: Int, dpW: Float, now: Long): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val view = MapView(s, w, h, dpW)

        val layer = c.saveLayer(0f, 0f, wf, hf, null)

        // 1. map (placeholder tint until tiles arrive)
        if (base != null) c.drawBitmap(base, 0f, 0f, null) else c.drawColor(pal.mapTint)

        // 2. sky
        if (s.clouds) SkyRenderer.clouds(c, w, h, now, pal.night)
        if (s.planes) SkyRenderer.planes(c, view, s, w, h, now)

        // 3. legibility scrim behind the text
        val scrimBase = if (pal.night) Color.BLACK else pal.mapTint
        val scrim = Paint()
        scrim.shader = LinearGradient(
            0f, hf * 0.55f, 0f, hf,
            ColorUtils.setAlphaComponent(scrimBase, 0), ColorUtils.setAlphaComponent(scrimBase, 150),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, hf * 0.55f, wf, hf, scrim)

        // 4. location dot with soft pulse
        val px = (view.ax * w).toFloat()
        val py = (view.ay * h).toFloat()
        val r = wf * 0.034f
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        if (s.mapAnim != 0) {
            val t = (now % 2400L) / 2400f
            dot.color = ColorUtils.setAlphaComponent(pal.dot, (90 * (1f - t)).toInt())
            c.drawCircle(px, py, r * (1f + 2.4f * t), dot)
        }
        dot.color = pal.ring
        dot.setShadowLayer(r * 0.35f, 0f, r * 0.15f, 0x55000000)
        c.drawCircle(px, py, r * 1.38f, dot)
        dot.clearShadowLayer()
        dot.color = pal.dot
        c.drawCircle(px, py, r, dot)

        // 5. text
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val left = wf * 0.08f
        val labelBase = hf * 0.825f
        if (s.label.isNotBlank()) {
            tp.typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            tp.textSize = wf * 0.068f
            tp.color = pal.text
            val a = wf * 0.036f
            c.save()
            c.translate(left + a * 0.9f, labelBase - tp.textSize * 0.34f)
            c.rotate(45f)
            c.scale(a * 0.9f, a * 0.9f)
            val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.text }
            c.drawPath(arrow, ap)
            c.restore()
            val tx = left + a * 2.6f
            val lab = TextUtils.ellipsize(s.label, tp, wf - tx - wf * 0.06f, TextUtils.TruncateAt.END).toString()
            c.drawText(lab, tx, labelBase, tp)
        }
        if (s.sublabel.isNotBlank()) {
            tp.typeface = Typeface.create(Typeface.DEFAULT, 400, false)
            tp.textSize = wf * 0.052f
            tp.color = pal.subText
            val sub = TextUtils.ellipsize(s.sublabel, tp, wf - left - wf * 0.06f, TextUtils.TruncateAt.END).toString()
            c.drawText(sub, left, hf * 0.915f, tp)
        }
        if (s.attribution) {
            tp.typeface = Typeface.DEFAULT
            tp.textSize = wf * 0.027f
            tp.color = ColorUtils.setAlphaComponent(pal.subText, 175)
            c.drawText("© OpenStreetMap © CARTO", wf * 0.08f, hf * 0.085f, tp)
        }

        // 6. cut to the chosen shape (anti-aliased)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG)
        mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawPath(WidgetShapes.path(s.shape, RectF(0f, 0f, wf, hf)), mask)
        c.restoreToCount(layer)
        return bmp
    }
}
