@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bentowidgets

import android.Manifest
import android.graphics.Bitmap
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { SettingsScreen() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(32.dp),
            extraLarge = RoundedCornerShape(40.dp),
        ),
        content = content,
    )
}

private val SEEDS = listOf(
    0xFF6750A4, 0xFF006C51, 0xFF0061A4, 0xFFBA1A1A, 0xFF8B5000, 0xFF984061, 0xFF006A6A, 0xFF5C5F00,
).map { it.toInt() }

@Suppress("DEPRECATION")
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Prefs.load(ctx)) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf("") }

    // save every change and nudge the live service (debounced)
    LaunchedEffect(s) {
        Prefs.save(ctx, s)
        delay(300)
        LiveService.refresh(ctx)
    }

    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        LiveService.start(ctx)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Bento Widgets", style = MaterialTheme.typography.headlineLarge)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ClockPreview(s)
                MapPreview(s)
            }

            Section("Look") {
                Text("Colour scheme")
                Seg(listOf("Reference", "Material You", "Custom"), s.scheme) { s = s.copy(scheme = it) }
                if (s.scheme == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SEEDS.forEach { sd ->
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(sd))
                                    .then(
                                        if (s.seed == sd) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier,
                                    )
                                    .clickable { s = s.copy(seed = sd) },
                            )
                        }
                    }
                }
                Text("Light / dark")
                Seg(listOf("System", "Light", "Dark"), s.darkMode) { s = s.copy(darkMode = it) }
                Text("Shape")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetShapes.names.forEachIndexed { i, n ->
                        FilterChip(selected = s.shape == i, onClick = { s = s.copy(shape = i) }, label = { Text(n) })
                    }
                }
            }

            Section("Clock") {
                Text("Second hand")
                Seg(listOf("Auto", "Mechanical", "Tick"), s.clockMode) { s = s.copy(clockMode = it) }
                Text(
                    "Mechanical beats 8 times a second. Tick jumps once a second (click, click). " +
                        "Auto switches to Tick whenever Battery Saver is on.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("Location map") {
                OutlinedTextField(
                    value = s.label, onValueChange = { s = s.copy(label = it) },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.sublabel, onValueChange = { s = s.copy(sublabel = it) },
                    label = { Text("Subtitle") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Search a place") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        scope.launch {
                            val a = withContext(Dispatchers.IO) {
                                runCatching { Geocoder(ctx).getFromLocationName(query, 1) }.getOrNull()?.firstOrNull()
                            }
                            if (a != null) {
                                val name = a.countryName ?: query
                                s = s.copy(lat = a.latitude, lon = a.longitude, label = "Based in $name")
                                found = listOfNotNull(a.locality, a.countryName).joinToString(", ").ifBlank { query }
                            } else {
                                found = "No match found"
                            }
                        }
                    }) { Text("Find") }
                }
                Text(
                    (if (found.isNotBlank()) "$found  ·  " else "") + "%.3f, %.3f".format(s.lat, s.lon),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Zoom  ${"%.1f".format(s.zoom)}")
                Slider(value = s.zoom, onValueChange = { s = s.copy(zoom = it) }, valueRange = 3f..12f)
                SwitchRow("Drifting clouds", s.clouds) { s = s.copy(clouds = it) }
                SwitchRow("Plane trails", s.planes) { s = s.copy(planes = it) }
                SwitchRow("Live wind + cloud cover (Open-Meteo)", s.liveWeather) { s = s.copy(liveWeather = it) }
                SwitchRow("Real aircraft nearby (adsb.lol)", s.liveAircraft) { s = s.copy(liveAircraft = it) }
                SwitchRow("Map labels", s.mapLabels) { s = s.copy(mapLabels = it) }
                SwitchRow("Offset dot like Bento", s.offsetDot) { s = s.copy(offsetDot = it) }
                SwitchRow("Show map credit", s.attribution) { s = s.copy(attribution = it) }
                Text("Animation")
                Seg(listOf("Static", "Calm", "Live"), s.mapAnim) { s = s.copy(mapAnim = it) }
            }

            Section("Add to home screen") {
                Text(
                    "Long-press the home screen, tap Widgets, then pick Bento Widgets and drag the " +
                        "Analogue clock or Location map out. While the screen is on, a quiet notification " +
                        "keeps them moving. If Android stops them, set this app to Unrestricted in " +
                        "Settings > Apps > Bento Widgets > Battery.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun Seg(options: List<String>, selected: Int, onSel: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSel(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(o, maxLines = 1) }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ClockPreview(s: Cfg) {
    val ctx = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(s) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        while (true) {
            val tick = when (s.clockMode) { 1 -> false; 2 -> true; else -> pm.isPowerSaveMode }
            val now = System.currentTimeMillis()
            bmp = withContext(Dispatchers.Default) { ClockRenderer.render(ctx, s, 400, 400, now, tick) }
            val step = if (tick) 1000L else 125L
            delay((step - System.currentTimeMillis() % step).coerceAtLeast(20L))
        }
    }
    bmp?.let { Image(it.asImageBitmap(), contentDescription = "Clock preview", modifier = Modifier.size(150.dp)) }
}

@Composable
fun MapPreview(s: Cfg) {
    val ctx = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(s) {
        val pal = Theme.palette(ctx, s)
        while (true) {
            val now = System.currentTimeMillis()
            bmp = withContext(Dispatchers.Default) {
                val base = MapBase.get(ctx, s, pal, 400, 400, 150f) {}
                MapRenderer.frame(s, pal, base, 400, 400, 150f, now)
            }
            delay(if (s.mapAnim == 2) 100L else 250L)
        }
    }
    bmp?.let { Image(it.asImageBitmap(), contentDescription = "Map preview", modifier = Modifier.size(150.dp)) }
}
