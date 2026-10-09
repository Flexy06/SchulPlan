package de.flexy.stundenplan.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Eine erkannte Änderung im Stundenplan. */
data class ScheduleChange(val kind: Kind, val lecture: Lecture, val text: String) {
    enum class Kind { CANCELLED, ROOM, TEACHER, MOVED, ADDED, REMOVED }
}

/**
 * Vergleicht den alten mit dem neuen Stundenplan und liefert die Änderungen,
 * die in den nächsten 3 Wochen relevant sind (ausgeblendete Fächer werden ignoriert).
 */
object ChangeDetector {
    private val WHEN = DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", Locale.GERMAN)

    fun diff(old: List<Lecture>, new: List<Lecture>, filter: LectureFilter, now: LocalDateTime): List<ScheduleChange> {
        val until = now.plusWeeks(3)
        fun relevant(l: Lecture) = filter.shows(l) && l.start.isAfter(now) && l.start.isBefore(until)
        fun key(l: Lecture) = "${l.title}|${l.start}"

        val oldActive = old.filter { !it.cancelled && relevant(it) }.associateBy(::key)
        val oldCancelled = old.filter { it.cancelled }.map(::key).toSet()
        val newActive = new.filter { !it.cancelled && relevant(it) }.associateBy(::key)
        val newCancelled = new.filter { it.cancelled && relevant(it) }.associateBy(::key)

        val changes = ArrayList<ScheduleChange>()

        // Neu ausgefallen
        for ((k, l) in newCancelled) {
            if (k !in oldCancelled) {
                val reason = if (l.note.isNotBlank()) " (${l.note})" else ""
                changes += ScheduleChange(ScheduleChange.Kind.CANCELLED, l, "Entfällt: ${l.title} · ${l.start.format(WHEN)}$reason")
            }
        }
        // Raumänderung
        for ((k, l) in newActive) {
            val o = oldActive[k] ?: continue
            if (!o.location.equals(l.location, ignoreCase = true) && l.location.isNotBlank()) {
                changes += ScheduleChange(
                    ScheduleChange.Kind.ROOM, l,
                    "Raumänderung: ${l.title} · ${l.start.format(WHEN)} → ${l.location}" +
                        if (o.location.isNotBlank()) " (statt ${o.location})" else "",
                )
            }
        }
        // Vertretung (andere Lehrkraft)
        for ((k, l) in newActive) {
            val o = oldActive[k] ?: continue
            if (o.teacher != l.teacher && l.teacher.isNotBlank() && l.changed) {
                changes += ScheduleChange(
                    ScheduleChange.Kind.TEACHER, l,
                    "Vertretung: ${l.title} · ${l.start.format(WHEN)} bei ${l.teacher}",
                )
            }
        }
        // Weggefallen / hinzugekommen → als Verlegung zusammenfassen, wenn möglich
        val removed = oldActive.filterKeys { it !in newActive && it !in newCancelled }.values.toMutableList()
        val added = newActive.filterKeys { it !in oldActive }.values.toMutableList()
        for (r in removed.toList()) {
            val a = added.filter { it.title == r.title }
                .minByOrNull { kotlin.math.abs(ChronoUnit.MINUTES.between(r.start, it.start)) }
            if (a != null && kotlin.math.abs(ChronoUnit.DAYS.between(r.start, a.start)) <= 14) {
                added.remove(a)
                removed.remove(r)
                changes += ScheduleChange(
                    ScheduleChange.Kind.MOVED, a,
                    "Verlegt: ${a.title} · ${r.start.format(WHEN)} → ${a.start.format(WHEN)}" +
                        if (a.location.isNotBlank()) " · ${a.location}" else "",
                )
            }
        }
        for (r in removed) {
            changes += ScheduleChange(ScheduleChange.Kind.REMOVED, r, "Entfernt: ${r.title} · ${r.start.format(WHEN)}")
        }
        for (a in added) {
            changes += ScheduleChange(
                ScheduleChange.Kind.ADDED, a,
                "Neuer Termin: ${a.title} · ${a.start.format(WHEN)}" + if (a.location.isNotBlank()) " · ${a.location}" else "",
            )
        }
        return changes.sortedBy { it.lecture.start }
    }
}
