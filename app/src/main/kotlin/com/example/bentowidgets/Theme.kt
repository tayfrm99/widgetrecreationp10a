package com.example.bentowidgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Resolved colours for one render. */
class Palette(
    val night: Boolean,
    val face: Int,      // clock face
    val onFace: Int,    // ticks + hour/minute hands
    val second: Int,    // red second hand (or tonal accent)
    val mapTint: Int,   // multiplied over map tiles
    val text: Int,
    val subText: Int,
    val dot: Int,
    val ring: Int,
)

object Theme {
    fun isNight(ctx: Context, s: Cfg): Boolean = when (s.darkMode) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun palette(ctx: Context, s: Cfg): Palette {
        val night = isNight(ctx, s)
        return when (s.scheme) {
            1 -> dynamic(ctx, night)
            2 -> seeded(s.seed, night)
            else -> reference(night)
        }
    }

    /** Matches the two screenshots: dark iOS-style dial, green Google-style map, Google blue dot. */
    private fun reference(night: Boolean): Palette =
        if (!night) Palette(
            false,
            face = 0xFF1C1C1E.toInt(), onFace = Color.WHITE, second = 0xFFE5322D.toInt(),
            mapTint = 0xFFCDEBC8.toInt(), text = 0xFF101010.toInt(), subText = 0xFF6F7A6F.toInt(),
            dot = 0xFF1E88FF.toInt(), ring = Color.WHITE,
        ) else Palette(
            true,
            face = 0xFF1C1C1E.toInt(), onFace = Color.WHITE, second = 0xFFFF5A52.toInt(),
            mapTint = 0xFFB9D6B6.toInt(), text = Color.WHITE, subText = 0xFFB7C2B7.toInt(),
            dot = 0xFF4DA3FF.toInt(), ring = 0xFF1C1C1E.toInt(),
        )

    /** Material You: pulls tonal colours from the wallpaper-derived system palette (Android 12+). */
    private fun dynamic(ctx: Context, night: Boolean): Palette {
        fun c(id: Int) = ctx.getColor(id)
        return if (!night) Palette(
            false,
            face = c(android.R.color.system_neutral1_50),
            onFace = c(android.R.color.system_neutral1_900),
            second = c(android.R.color.system_accent1_600),
            mapTint = c(android.R.color.system_accent1_100),
            text = c(android.R.color.system_neutral1_900),
            subText = c(android.R.color.system_neutral2_600),
            dot = c(android.R.color.system_accent1_600),
            ring = c(android.R.color.system_neutral1_0),
        ) else Palette(
            true,
            face = c(android.R.color.system_neutral1_900),
            onFace = c(android.R.color.system_neutral1_50),
            second = c(android.R.color.system_accent1_300),
            mapTint = c(android.R.color.system_accent1_300),
            text = c(android.R.color.system_neutral1_50),
            subText = c(android.R.color.system_neutral2_300),
            dot = c(android.R.color.system_accent1_300),
            ring = c(android.R.color.system_neutral1_900),
        )
    }

    /** Tonal palette generated from any seed colour (simple HSL approximation of M3 tones). */
    private fun seeded(seed: Int, night: Boolean): Palette {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        val h = hsl[0]
        val sat = hsl[1].coerceIn(0.35f, 0.85f)
        fun t(l: Float, s: Float = sat) = ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l))
        return if (!night) Palette(
            false,
            face = t(0.96f, 0.30f), onFace = t(0.12f, 0.25f), second = t(0.42f),
            mapTint = t(0.88f, 0.50f), text = t(0.10f, 0.30f), subText = t(0.38f, 0.15f),
            dot = t(0.45f), ring = Color.WHITE,
        ) else Palette(
            true,
            face = t(0.12f, 0.25f), onFace = t(0.94f, 0.20f), second = t(0.72f),
            mapTint = t(0.72f, 0.40f), text = t(0.95f, 0.15f), subText = t(0.78f, 0.15f),
            dot = t(0.72f), ring = t(0.12f, 0.25f),
        )
    }
}

/** Widget outlines, including M3-Expressive-style cookie and clover shapes. */
object WidgetShapes {
    val names = listOf("Rounded", "Squircle", "Circle", "Cookie", "Clover")

    fun path(kind: Int, r: RectF): Path {
        val p = Path()
        val cx = r.centerX()
        val cy = r.centerY()
        val rx = r.width() / 2f
        val ry = r.height() / 2f
        when (kind) {
            0 -> {
                val rad = 0.14f * minOf(r.width(), r.height())
                p.addRoundRect(r, rad, rad, Path.Direction.CW)
            }
            2 -> p.addOval(r, Path.Direction.CW)
            1 -> {
                val steps = 360
                for (i in 0..steps) {
                    val t = i * 2.0 * PI / steps
                    val c = cos(t)
                    val s = sin(t)
                    val x = cx + rx * (sign(c) * abs(c).pow(0.5)).toFloat()
                    val y = cy + ry * (sign(s) * abs(s).pow(0.5)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            else -> {
                val steps = 540
                for (i in 0..steps) {
                    val t = i * 2.0 * PI / steps
                    val k = if (kind == 3) 0.93 + 0.07 * cos(9 * t) else 0.84 + 0.16 * cos(4 * t)
                    val x = cx + rx * (k * cos(t)).toFloat()
                    val y = cy + ry * (k * sin(t)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
        }
        return p
    }

    /** Distance from centre to the outline along a clock angle (0 = 12 o'clock, clockwise). */
    fun boundary(kind: Int, th: Double, half: Float): Float {
        val ct = abs(cos(th))
        val st = abs(sin(th))
        val t = th - PI / 2.0 // screen angle used by the polar shapes
        return when (kind) {
            0, 1 -> {
                val n = if (kind == 0) 4.6 else 4.0
                (half / (ct.pow(n) + st.pow(n)).pow(1.0 / n)).toFloat()
            }
            3 -> (half * (0.93 + 0.07 * cos(9 * t))).toFloat()
            4 -> (half * (0.84 + 0.16 * cos(4 * t))).toFloat()
            else -> half
        }
    }
}
