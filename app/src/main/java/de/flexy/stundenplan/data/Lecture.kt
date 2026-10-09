package de.flexy.stundenplan.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Eine Unterrichtsstunde (Doppelstunden sind bereits zusammengefasst). */
data class Lecture(
    val id: String,
    /** Fach (Langname, z.B. "Mathematik") */
    val title: String,
    /** Fach-Kürzel (z.B. "M") */
    val code: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** Raum/Räume */
    val location: String,
    val cancelled: Boolean = false,
    /** Infotexte aus WebUntis (Stundentext, Info, Vertretungstext) */
    val note: String = "",
    /** Lehrkraft/Lehrkräfte */
    val teacher: String = "",
    /** Vertretung, Raum- oder Lehrerwechsel, Zusatzstunde … */
    val changed: Boolean = false,
    /** Was sich geändert hat, z.B. "Vertretung für Mül · Raum statt 204" */
    val changeInfo: String = "",
) {
    val date: LocalDate get() = start.toLocalDate()
    val duration: Duration get() = Duration.between(start, end)
}
