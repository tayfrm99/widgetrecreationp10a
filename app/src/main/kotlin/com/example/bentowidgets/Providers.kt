package com.example.bentowidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class ClockWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        LiveService.start(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // draw immediately so the widget is never blank, then make sure the live service is running
        Frames.clock(context, Prefs.load(context), System.currentTimeMillis(), false)
        LiveService.start(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        Frames.clock(context, Prefs.load(context), System.currentTimeMillis(), false)
        LiveService.refresh(context)
    }
}

class MapWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        LiveService.start(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Frames.map(context, Prefs.load(context), System.currentTimeMillis()) {}
        LiveService.start(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        Frames.map(context, Prefs.load(context), System.currentTimeMillis()) {}
        LiveService.refresh(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LiveService.start(context)
    }
}
