# Bento Widgets

Two Android home-screen widgets in one app, styled after the screenshots you sent:

* **Location map** – Bento-style "Based in France / Working remotely" card with a blue dot, live drifting clouds and plane trails.
* **Analogue clock** – dark square dial, ticks following the outline, white hour/minute hands, red second hand with tail and dot.

## Build

1. Open this folder in **Android Studio** (Narwhal or newer). Let it sync (it downloads Gradle 8.14.3, AGP 8.13, Kotlin 2.2.20, Compose BOM 2025.09.01; accept the upgrade assistant if it offers newer ones).
2. Plug in your Pixel (USB debugging on) and press **Run**.
3. Long-press the home screen -> **Widgets** -> **Bento Widgets** -> drag out *Analogue clock* and *Location map*.
4. Open the app to change colours, shape, location and animation. Changes apply to the widgets within a second.

Requires Android 12+ (minSdk 31).

## How the animation works

Android widgets can't animate themselves, so a small foreground service (`LiveService`) redraws them while the screen is on and sleeps when it's off.

| | Normal | Battery Saver |
|---|---|---|
| Clock second hand | mechanical beat, 8 steps/s | one 6 deg tick per second ("click, click") |
| Map | 4 fps (Calm) or 10 fps (Live) | 1 fps |

You can force either clock mode in the app (Auto / Mechanical / Tick).

## Live data

* Cloud density, drift direction and speed come from **Open-Meteo** (`cloud_cover`, `wind_speed_10m`, `wind_direction_10m`) for your chosen location, refreshed every 15 min. Offline it falls back to calm defaults.
* **Real aircraft** come from the community **adsb.lol** feed once a minute and are dead-reckoned between polls. When fewer than 3 are in view, decorative Bento-style flyovers (a plane crossing every ~26 s with a fading contrail) fill in.
* Map tiles are **CARTO Voyager / Dark Matter** (OpenStreetMap data), cached on disk for 14 days. Tinted with the colour scheme. The credit is shown on the map (you can hide it in settings, but check the providers' terms for your use).

These are free public endpoints with no key. They are fine for a personal widget, but their availability and terms are outside this app's control.

## Colour schemes

* **Reference** – matches your screenshots.
* **Material You** – tonal colours from your wallpaper (`system_accent1_*`, `system_neutral1_*`).
* **Custom** – pick a seed colour, a tonal set is derived from it.

Light / dark can follow the system or be forced. Shapes: Rounded, Squircle, Circle, Cookie, Clover.

## Notes

* Keep the app set to **Unrestricted** battery use if Android stops the live updates.
* Android 13+ will ask for notification permission; the service notification is minimum-priority and can be hidden.
* Android may refuse to start the service from the background (for example right after a reboot); opening the app or tapping a widget starts it.
