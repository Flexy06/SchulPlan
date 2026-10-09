package de.flexy.stundenplan.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Hausaufgabe aus WebUntis. */
data class Homework(
    val id: Long,
    val subject: String,
    val date: LocalDate,
    val due: LocalDate,
    val text: String,
    val remark: String = "",
    val teacher: String = "",
    /** in WebUntis als erledigt markiert */
    val completed: Boolean = false,
)

/** Prüfung / Klassenarbeit aus WebUntis. */
data class Exam(
    val id: Long,
    val subject: String,
    val name: String,
    val type: String,
    val date: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val rooms: String = "",
    val teachers: String = "",
    val text: String = "",
)

/**
 * Wandelt die Antworten der WebUntis-API in App-Objekte um (ohne Netzwerk → gut testbar).
 */
object UntisParser {

    private val YMD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun date(v: Int): LocalDate = LocalDate.parse(v.toString(), YMD)
    fun time(v: Int): LocalTime = LocalTime.of(v / 100, v % 100)
    fun ymd(d: LocalDate): Int = d.format(YMD).toInt()

    /** Ergebnis von getTimetable (JSON-Array) → Stunden, Doppelstunden zusammengefasst, sortiert. */
    fun timetable(result: JSONArray): List<Lecture> {
        val raw = ArrayList<Lecture>(result.length())
        for (i in 0 until result.length()) {
            val o = result.optJSONObject(i) ?: continue
            lesson(o)?.let(raw::add)
        }
        raw.sortWith(compareBy({ it.start }, { it.title }))
        return mergeDoubles(raw)
    }

    private fun lesson(o: JSONObject): Lecture? {
        val dateInt = o.optInt("date", 0)
        if (dateInt == 0) return null
        val day = date(dateInt)
        val start = LocalDateTime.of(day, time(o.optInt("startTime")))
        val end = LocalDateTime.of(day, time(o.optInt("endTime")))
        if (!end.isAfter(start)) return null

        val subjects = elements(o.optJSONArray("su"))
        val teachers = elements(o.optJSONArray("te"))
        val rooms = elements(o.optJSONArray("ro"))

        val code = o.optString("code", "")
        val cancelled = code == "cancelled"
        val lstext = o.optString("lstext", "").trim()
        val title = subjects.joinToString(" / ") { it.long.ifBlank { it.short } }
            .ifBlank { lstext }
            .ifBlank { o.optString("activityType", "").takeIf { it.isNotBlank() && it != "Unterricht" } ?: "" }
            .ifBlank { "Termin" }
        val short = subjects.joinToString(" / ") { it.short }

        // Was wurde ersetzt? (orgname = ursprünglicher Raum/Lehrer)
        val changes = ArrayList<String>()
        teachers.filter { it.original.isNotBlank() }.forEach {
            changes += if (it.short.isBlank()) "statt ${it.original}" else "Vertretung für ${it.original}"
        }
        rooms.filter { it.original.isNotBlank() }.forEach { changes += "Raum ${it.short} statt ${it.original}" }
        if (code == "irregular" && changes.isEmpty()) changes += "Geänderte Stunde"

        val notes = listOf(
            lstext.takeIf { it != title },
            o.optString("info", "").trim(),
            o.optString("substText", "").trim(),
        ).filterNotNull().filter { it.isNotBlank() }.distinct()

        return Lecture(
            id = "u" + o.optLong("id"),
            title = title,
            code = short,
            start = start,
            end = end,
            location = rooms.joinToString(", ") { it.short }.trim(),
            cancelled = cancelled,
            note = notes.joinToString(" · "),
            teacher = teachers.mapNotNull { t -> (t.long.ifBlank { t.short }).takeIf { it.isNotBlank() } }.joinToString(", "),
            changed = !cancelled && (code == "irregular" || changes.isNotEmpty()),
            changeInfo = if (cancelled) "" else changes.joinToString(" · "),
        )
    }

    private data class El(val short: String, val long: String, val original: String)

    private fun elements(a: JSONArray?): List<El> {
        if (a == null) return emptyList()
        val out = ArrayList<El>()
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            out += El(
                short = e.optString("name", "").trim().takeIf { it != "---" }.orEmpty(),
                long = e.optString("longname", "").trim(),
                original = e.optString("orgname", "").trim(),
            )
        }
        return out
    }

    /** Direkt aufeinanderfolgende gleiche Stunden (≤ 10 min Pause) zu einer Doppelstunde zusammenfassen. */
    fun mergeDoubles(sorted: List<Lecture>): List<Lecture> {
        val out = ArrayList<Lecture>(sorted.size)
        for (l in sorted) {
            val i = out.indexOfLast {
                it.date == l.date && it.title == l.title && it.teacher == l.teacher && it.location == l.location &&
                    it.cancelled == l.cancelled && it.changed == l.changed && it.note == l.note &&
                    !l.start.isBefore(it.end) && !l.start.isAfter(it.end.plusMinutes(10))
            }
            if (i >= 0) out[i] = out[i].copy(end = l.end, id = out[i].id + "+" + l.id)
            else out += l
        }
        return out.sortedWith(compareBy({ it.start }, { it.title }))
    }

    /** Antwort von /api/homeworks/lessons. */
    fun homework(root: JSONObject): List<Homework> {
        val data = root.optJSONObject("data") ?: return emptyList()
        val lessons = HashMap<Long, String>()
        data.optJSONArray("lessons")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { lessons[it.optLong("id")] = it.optString("subject", "") }
        }
        val teachers = HashMap<Long, String>()
        data.optJSONArray("teachers")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { teachers[it.optLong("id")] = it.optString("name", "") }
        }
        val teacherOf = HashMap<Long, String>()
        data.optJSONArray("records")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                teacherOf[it.optLong("homeworkId")] = teachers[it.optLong("teacherId")].orEmpty()
            }
        }
        val out = ArrayList<Homework>()
        val hw = data.optJSONArray("homeworks") ?: return out
        for (i in 0 until hw.length()) {
            val h = hw.optJSONObject(i) ?: continue
            val id = h.optLong("id")
            val due = h.optInt("dueDate", 0).takeIf { it > 0 } ?: continue
            out += Homework(
                id = id,
                subject = lessons[h.optLong("lessonId")].orEmpty(),
                date = date(h.optInt("date", due)),
                due = date(due),
                text = h.optString("text", "").trim(),
                remark = h.optString("remark", "").trim(),
                teacher = teacherOf[id].orEmpty(),
                completed = h.optBoolean("completed", false),
            )
        }
        return out.sortedWith(compareBy({ it.due }, { it.subject }))
    }

    /** Antwort von /api/exams. */
    fun exams(root: JSONObject): List<Exam> {
        val a = root.optJSONObject("data")?.optJSONArray("exams") ?: return emptyList()
        val out = ArrayList<Exam>()
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            val d = e.optInt("examDate", 0).takeIf { it > 0 } ?: continue
            out += Exam(
                id = e.optLong("id"),
                subject = e.optString("subject", "").trim(),
                name = e.optString("name", "").trim(),
                type = e.optString("examType", "").trim(),
                date = date(d),
                start = e.optInt("startTime", 0).takeIf { it > 0 }?.let(::time),
                end = e.optInt("endTime", 0).takeIf { it > 0 }?.let(::time),
                rooms = strings(e.optJSONArray("rooms")),
                teachers = strings(e.optJSONArray("teachers")),
                text = e.optString("text", "").trim(),
            )
        }
        return out.sortedWith(compareBy({ it.date }, { it.start }))
    }

    private fun strings(a: JSONArray?): String {
        if (a == null) return ""
        return (0 until a.length()).map { a.optString(it, "") }.filter { it.isNotBlank() }.joinToString(", ")
    }
}
