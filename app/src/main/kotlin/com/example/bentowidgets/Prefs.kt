package com.example.bentowidgets

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** All user settings in one immutable bundle. */
data class Cfg(
    val scheme: Int = 0,            // 0 = Reference look, 1 = Material You (wallpaper), 2 = custom seed colour
    val seed: Int = 0xFF6750A4.toInt(),
    val darkMode: Int = 0,          // 0 = follow system, 1 = light, 2 = dark
    val shape: Int = 0,             // index into WidgetShapes.names
    // location widget
    val label: String = "Based in France",
    val sublabel: String = "Working remotely",
    val lat: Double = 48.2,
    val lon: Double = 1.9,
    val zoom: Float = 5.2f,
    val clouds: Boolean = true,
    val planes: Boolean = true,
    val liveWeather: Boolean = true,
    val liveAircraft: Boolean = true,
    val mapLabels: Boolean = true,
    val offsetDot: Boolean = true,
    val attribution: Boolean = true,
    val mapAnim: Int = 1,           // 0 = static, 1 = calm (4 fps), 2 = live (10 fps)
    // clock widget
    val clockMode: Int = 0,         // 0 = auto (battery saver -> tick), 1 = mechanical 8 Hz, 2 = tick 1 Hz
)

object Prefs {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("bento", Context.MODE_PRIVATE)

    fun load(c: Context): Cfg {
        val p = sp(c)
        val d = Cfg()
        return Cfg(
            scheme = p.getInt("scheme", d.scheme),
            seed = p.getInt("seed", d.seed),
            darkMode = p.getInt("darkMode", d.darkMode),
            shape = p.getInt("shape", d.shape),
            label = p.getString("label", d.label) ?: d.label,
            sublabel = p.getString("sublabel", d.sublabel) ?: d.sublabel,
            lat = p.getString("lat", null)?.toDoubleOrNull() ?: d.lat,
            lon = p.getString("lon", null)?.toDoubleOrNull() ?: d.lon,
            zoom = p.getFloat("zoom", d.zoom),
            clouds = p.getBoolean("clouds", d.clouds),
            planes = p.getBoolean("planes", d.planes),
            liveWeather = p.getBoolean("liveWeather", d.liveWeather),
            liveAircraft = p.getBoolean("liveAircraft", d.liveAircraft),
            mapLabels = p.getBoolean("mapLabels", d.mapLabels),
            offsetDot = p.getBoolean("offsetDot", d.offsetDot),
            attribution = p.getBoolean("attribution", d.attribution),
            mapAnim = p.getInt("mapAnim", d.mapAnim),
            clockMode = p.getInt("clockMode", d.clockMode),
        )
    }

    fun save(c: Context, v: Cfg) {
        sp(c).edit()
            .putInt("scheme", v.scheme)
            .putInt("seed", v.seed)
            .putInt("darkMode", v.darkMode)
            .putInt("shape", v.shape)
            .putString("label", v.label)
            .putString("sublabel", v.sublabel)
            .putString("lat", v.lat.toString())
            .putString("lon", v.lon.toString())
            .putFloat("zoom", v.zoom)
            .putBoolean("clouds", v.clouds)
            .putBoolean("planes", v.planes)
            .putBoolean("liveWeather", v.liveWeather)
            .putBoolean("liveAircraft", v.liveAircraft)
            .putBoolean("mapLabels", v.mapLabels)
            .putBoolean("offsetDot", v.offsetDot)
            .putBoolean("attribution", v.attribution)
            .putInt("mapAnim", v.mapAnim)
            .putInt("clockMode", v.clockMode)
            .apply()
    }
}

object Net {
    fun get(url: String, timeoutMs: Int = 8000): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", "BentoWidgets/1.0 (personal Android widget)")
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
