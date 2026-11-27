package com.phonehunger.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.phonehunger.app.MainActivity
import com.phonehunger.app.R
import com.phonehunger.app.battery.BatterySnapshot
import com.phonehunger.app.battery.BatteryState

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, BatteryState.initial(context))
    }
    companion object {
        fun update(context: Context, snapshot: BatterySnapshot?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java))
            val view = RemoteViews(context.packageName, R.layout.battery_widget)
            view.setTextViewText(R.id.widget_level, snapshot?.let { "${it.level}% ${if (it.plugged) "⚡" else if (it.level <= 20) "☹" else "☺"}" } ?: "—")
            view.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, view)
        }
    }
}
