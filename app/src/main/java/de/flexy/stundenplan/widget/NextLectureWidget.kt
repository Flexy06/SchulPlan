package de.flexy.stundenplan.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import de.flexy.stundenplan.MainActivity
import de.flexy.stundenplan.R
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.data.TimetableRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Homescreen-Widget: die nächsten Termine des aktuellen (bzw. nächsten) Uni-Tags. */
class NextLectureWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = buildViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY = DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN)

        private val ROWS = listOf(
            intArrayOf(R.id.row1, R.id.row1_time, R.id.row1_title, R.id.row1_room),
            intArrayOf(R.id.row2, R.id.row2_time, R.id.row2_title, R.id.row2_room),
            intArrayOf(R.id.row3, R.id.row3_time, R.id.row3_title, R.id.row3_room),
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NextLectureWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next)
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val now = LocalDateTime.now()
            val upcoming = runCatching { TimetableRepository(context).upcomingActive(now) }.getOrDefault(emptyList())
            val day: LocalDate? = upcoming.minByOrNull { it.start }?.date
            val list: List<Lecture> = if (day == null) emptyList() else upcoming.filter { it.date == day }.sortedBy { it.start }

            if (day == null) {
                views.setTextViewText(R.id.widget_header, "Stundenplan")
                views.setTextViewText(R.id.widget_empty, "Keine anstehenden Termine")
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            } else {
                val today = now.toLocalDate()
                val label = when (ChronoUnit.DAYS.between(today, day)) {
                    0L -> "Heute"
                    1L -> "Morgen"
                    else -> day.format(DAY)
                }
                views.setTextViewText(R.id.widget_header, label)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
            }

            ROWS.forEachIndexed { i, ids ->
                val l = list.getOrNull(i)
                if (l == null) {
                    views.setViewVisibility(ids[0], View.GONE)
                } else {
                    views.setViewVisibility(ids[0], View.VISIBLE)
                    val running = !now.isBefore(l.start) && now.isBefore(l.end)
                    views.setTextViewText(ids[1], if (running) "jetzt" else l.start.format(TIME))
                    views.setTextViewText(ids[2], l.title)
                    views.setTextViewText(ids[3], l.location)
                }
            }
            val more = list.size - ROWS.size
            if (more > 0) {
                views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                views.setTextViewText(R.id.widget_more, "+$more weitere")
            } else {
                views.setViewVisibility(R.id.widget_more, View.GONE)
            }
            return views
        }
    }
}
