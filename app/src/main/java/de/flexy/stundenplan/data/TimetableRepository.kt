package de.flexy.stundenplan.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Alles, was ein Abgleich mit WebUntis liefert. */
data class SchoolData(
    val lectures: List<Lecture>,
    val homework: List<Homework>,
    val exams: List<Exam>,
)

/**
 * Holt Stundenplan, Hausaufgaben und Prüfungen aus WebUntis und hält eine Offline-Kopie im App-Speicher.
 */
class TimetableRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val credentials = CredentialStore(appContext)

    val isLoggedIn: Boolean get() = credentials.user != null

    var hiddenModules: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("hidden", value).apply()

    /** Einzeln ausgeblendete Stunden ("Fach|Startzeit"). */
    var skipped: Set<String>
        get() = prefs.getStringSet("skipped", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("skipped", value).apply()

    /** Lokal abgehakte Hausaufgaben (IDs). */
    var doneHomework: Set<String>
        get() = prefs.getStringSet("doneHomework", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("doneHomework", value).apply()

    val filter: LectureFilter get() = LectureFilter(hiddenModules, skipped)

    /** Tagesvorschau: 0 = aus, 1 = am Vorabend (20 Uhr), 2 = morgens (7 Uhr). */
    var digestMode: Int
        get() = prefs.getInt("digestMode", 0)
        set(value) = prefs.edit().putInt("digestMode", value).apply()

    var showCancelled: Boolean
        get() = prefs.getBoolean("showCancelled", true)
        set(value) = prefs.edit().putBoolean("showCancelled", value).apply()

    /** Minuten vor Beginn für die Erinnerung, 0 = aus. */
    var reminderMinutes: Int
        get() = prefs.getInt("reminderMinutes", 0)
        set(value) = prefs.edit().putInt("reminderMinutes", value).apply()

    /** Benachrichtigung bei Entfall/Vertretung/Raumänderung (Hintergrund-Abgleich). */
    var notifyChanges: Boolean
        get() = prefs.getBoolean("notifyChanges", true)
        set(value) = prefs.edit().putBoolean("notifyChanges", value).apply()

    /** "day" oder "week" */
    var viewMode: String
        get() = prefs.getString("viewMode", "day") ?: "day"
        set(value) = prefs.edit().putString("viewMode", value).apply()

    val lastUpdated: Long get() = prefs.getLong("updated", 0L)

    /* ------------------------------------------------------------------------------------------ */

    /** Prüft die Zugangsdaten, speichert sie und lädt gleich den ersten Stand. */
    suspend fun login(account: UntisAccount): SchoolData = withContext(Dispatchers.IO) {
        val data = download(account)
        credentials.save(account)
        data
    }

    fun logout() {
        credentials.clear()
        listOf(timetableFile, homeworkFile, examFile).forEach { it.delete() }
        prefs.edit().remove("updated").remove("skipped").remove("hidden").remove("doneHomework").apply()
    }

    suspend fun loadCached(): SchoolData? = withContext(Dispatchers.IO) { loadCachedSync() }

    /** Synchron, nur lokale Dateien (für Widget, Erinnerungen, Tagesvorschau). */
    fun loadCachedSync(): SchoolData? {
        if (!timetableFile.exists()) return null
        return runCatching {
            SchoolData(
                lectures = UntisParser.timetable(JSONArray(timetableFile.readText())),
                homework = homeworkFile.takeIf { it.exists() }?.let { UntisParser.homework(JSONObject(it.readText())) }.orEmpty(),
                exams = examFile.takeIf { it.exists() }?.let { UntisParser.exams(JSONObject(it.readText())) }.orEmpty(),
            )
        }.getOrNull()
    }

    /** Stunden, die tatsächlich stattfinden und nicht ausgeblendet sind. */
    fun upcomingActive(now: LocalDateTime = LocalDateTime.now()): List<Lecture> {
        val f = filter
        return loadCachedSync()?.lectures.orEmpty().filter { !it.cancelled && f.shows(it) && it.end.isAfter(now) }
    }

    /** Frisch von WebUntis laden und Cache aktualisieren. */
    suspend fun fetch(): SchoolData = withContext(Dispatchers.IO) {
        val account = credentials.load() ?: throw UntisException("Bitte erneut anmelden.", badCredentials = true)
        download(account)
    }

    private fun download(account: UntisAccount): SchoolData {
        val client = UntisClient(account)
        try {
            val session = client.login()
            val today = LocalDate.now()
            // Letzte Woche bis 5 Wochen voraus
            val from = today.minusDays(7)
            val to = today.plusWeeks(5)
            val tt = client.timetable(from, to)
            // Hausaufgaben & Prüfungen sind "nice to have" – Fehler hier brechen den Abgleich nicht ab
            val hw = runCatching { client.homework(today.minusWeeks(2), today.plusWeeks(6)) }.getOrNull()
            val ex = runCatching { client.exams(today.minusDays(7), today.plusMonths(6)) }.getOrNull()
            if (session.klasseId > 0) client.klasseName()?.let { credentials.klasse = it }

            timetableFile.writeText(tt.toString())
            hw?.let { homeworkFile.writeText(it.toString()) }
            ex?.let { examFile.writeText(it.toString()) }
            prefs.edit().putLong("updated", System.currentTimeMillis()).apply()

            return SchoolData(
                lectures = UntisParser.timetable(tt),
                homework = hw?.let(UntisParser::homework)
                    ?: homeworkFile.takeIf { it.exists() }?.let { UntisParser.homework(JSONObject(it.readText())) }.orEmpty(),
                exams = ex?.let(UntisParser::exams)
                    ?: examFile.takeIf { it.exists() }?.let { UntisParser.exams(JSONObject(it.readText())) }.orEmpty(),
            )
        } finally {
            client.logout()
        }
    }

    private val timetableFile get() = File(appContext.filesDir, "timetable.json")
    private val homeworkFile get() = File(appContext.filesDir, "homework.json")
    private val examFile get() = File(appContext.filesDir, "exams.json")
}
