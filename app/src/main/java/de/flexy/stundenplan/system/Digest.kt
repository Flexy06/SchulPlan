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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tagesvorschau: am Vorabend (20 Uhr) oder morgens (7 Uhr) eine Benachrichtigung
 * mit allen Terminen des (nächsten) Uni-Tags – nur an Tagen, an denen auch etwas ist.
 */
object Digest {
    const val OFF = 0
    const val EVENING = 1
    const val MORNING = 2

    private const val CHANNEL = "day_digest"
    private const val REQUEST = 4712
    private const val NOTIFICATION_ID = 9100
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

    private fun timeFor(mode: Int): LocalTime = if (mode == EVENING) LocalTime.of(20, 0) else LocalTime.of(7, 0)

    fun reschedule(context: Context) {
        val mode = TimetableRepository(context).digestMode
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, DigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pending)
        if (mode == OFF) return

        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(timeFor(mode))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Ein paar Minuten Ungenauigkeit sind hier egal → kein exakter Alarm nötig
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    internal fun show(context: Context) {
        val repo = TimetableRepository(context)
        val mode = repo.digestMode
        if (mode == OFF) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val day: LocalDate = if (mode == EVENING) LocalDate.now().plusDays(1) else LocalDate.now()
        val f = repo.filter
        val lectures = repo.loadCachedSync()?.lectures.orEmpty()
            .filter { it.date == day && f.shows(it) }
            .sortedBy { it.start }
        val active = lectures.filter { !it.cancelled }
        val cancelled = lectures.filter { it.cancelled }
        if (active.isEmpty() && cancelled.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Tagesvorschau", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Überblick über die Stunden des Tages"
                    }
                )
            }
        }

        val label = if (mode == EVENING) "Morgen" else "Heute"
        val title = if (active.isEmpty()) {
            "$label fällt alles aus"
        } else {
            "$label: ${active.size} ${if (active.size == 1) "Stunde" else "Stunden"} · " +
                "${active.first().start.format(TIME)}–${active.maxOf { it.end }.format(TIME)}"
        }
        val first = active.firstOrNull()
        val text = first?.let { "Los geht's um ${it.start.format(TIME)}: ${it.title}" + if (it.location.isNotBlank()) " · ${it.location}" else "" }
            ?: day.format(DAY)

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        for (l in lectures.take(7)) {
            val prefix = if (l.cancelled) "Entfällt: " else ""
            style.addLine("${l.start.format(TIME)}  $prefix${l.title}" + if (l.location.isNotBlank()) " · ${l.location}" else "")
        }
        style.setSummaryText(day.format(DAY))

        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lecture)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Digest.show(context)
        Digest.reschedule(context)
    }
}
