package de.flexy.stundenplan.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.flexy.stundenplan.MainActivity
import de.flexy.stundenplan.R
import de.flexy.stundenplan.data.TimetableRepository
import de.flexy.stundenplan.widget.NextLectureWidget
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Erinnerung X Minuten vor jeder Veranstaltung.
 * Es ist immer nur EIN Alarm gesetzt (für den nächsten Termin); nach dem Auslösen wird der nächste geplant.
 */
object Reminders {
    private const val CHANNEL = "lecture_reminders"
    private const val REQUEST_ALARM = 4711
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun reschedule(context: Context) {
        val repo = TimetableRepository(context)
        val minutes = repo.reminderMinutes
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        am.cancel(pending)
        if (minutes <= 0) return

        val now = LocalDateTime.now()
        val next = repo.upcomingActive(now)
            .filter { it.start.minusMinutes(minutes.toLong()).isAfter(now) }
            .minByOrNull { it.start } ?: return
        val triggerAt = next.start.minusMinutes(minutes.toLong())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_ALARM,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun notifyUpcoming(context: Context) {
        val repo = TimetableRepository(context)
        val minutes = repo.reminderMinutes
        if (minutes <= 0) return
        val now = LocalDateTime.now()
        // Alle Termine, die in den nächsten (minutes + 2) Minuten beginnen
        val soon = repo.upcomingActive(now)
            .filter { it.start.isAfter(now.minusMinutes(1)) && !it.start.isAfter(now.plusMinutes(minutes + 2L)) }
        if (soon.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        for (l in soon) {
            val inMin = java.time.Duration.between(now, l.start).toMinutes().coerceAtLeast(0)
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lecture)
                .setContentTitle(if (inMin <= 1) "${l.title} beginnt jetzt" else "${l.title} in $inMin min")
                .setContentText(listOf(l.location, "${l.start.format(TIME)}–${l.end.format(TIME)}", l.changeInfo).filter { it.isNotBlank() }.joinToString(" · "))
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setTimeoutAfter(java.time.Duration.between(now, l.end).toMillis().coerceAtLeast(60_000))

            try {
                nm.notify(l.id.hashCode(), builder.build())
            } catch (_: SecurityException) {
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Erinnerungen vor der Stunde", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Kurz vor Beginn einer Stunde, mit Raum"
                }
            )
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.notifyUpcoming(context)
        Reminders.reschedule(context)
        NextLectureWidget.updateAll(context)
    }
}

/** Nach Neustart / App-Update / Zeitänderung Alarm & Widget neu setzen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.reschedule(context)
        Digest.reschedule(context)
        NextLectureWidget.updateAll(context)
    }
}
