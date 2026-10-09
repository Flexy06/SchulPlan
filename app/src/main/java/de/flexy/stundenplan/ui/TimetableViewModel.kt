package de.flexy.stundenplan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.flexy.stundenplan.data.ChangeDetector
import de.flexy.stundenplan.data.Exam
import de.flexy.stundenplan.data.Homework
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.data.LectureFilter
import de.flexy.stundenplan.data.SchoolData
import de.flexy.stundenplan.data.TimetableRepository
import de.flexy.stundenplan.data.UntisAccount
import de.flexy.stundenplan.data.UntisException
import de.flexy.stundenplan.system.Reminders
import de.flexy.stundenplan.widget.NextLectureWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val loggedIn: Boolean = false,
    val user: String = "",
    val klasse: String? = null,
    val school: String = UntisAccount.DEFAULT_SCHOOL,
    val loggingIn: Boolean = false,
    val loginError: String? = null,
    val all: List<Lecture> = emptyList(),
    val homework: List<Homework> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val doneHomework: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val skipped: Set<String> = emptySet(),
    val digestMode: Int = 0,
    val showCancelled: Boolean = true,
    val refreshing: Boolean = false,
    val initialLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: Long = 0L,
    val reminderMinutes: Int = 0,
    val weekView: Boolean = false,
    val notifyChanges: Boolean = true,
) {
    val visible: List<Lecture> by lazy {
        val f = LectureFilter(hidden, skipped)
        all.filter { f.shows(it) && (showCancelled || !it.cancelled) }
    }

    /** Alle Fächer (für die Ein-/Ausblenden-Liste). */
    val modules: List<String> by lazy {
        all.filter { !it.cancelled }.map { it.title }.distinct().sortedBy { it.lowercase() }
    }

    fun isDone(h: Homework) = h.completed || h.id.toString() in doneHomework

    /** Offene Hausaufgaben ab heute. */
    val openHomework: List<Homework> by lazy {
        val today = LocalDate.now()
        homework.filter { !isDone(it) && !it.due.isBefore(today) }
    }

    val upcomingExams: List<Exam> by lazy {
        val today = LocalDate.now()
        exams.filter { !it.date.isBefore(today) }
    }

    val subtitle: String get() = klasse ?: user
}

class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TimetableRepository(app)

    private val _state = MutableStateFlow(
        UiState(
            loggedIn = repo.isLoggedIn,
            user = repo.credentials.user.orEmpty(),
            klasse = repo.credentials.klasse,
            school = repo.credentials.school,
            hidden = repo.hiddenModules,
            skipped = repo.skipped,
            doneHomework = repo.doneHomework,
            digestMode = repo.digestMode,
            showCancelled = repo.showCancelled,
            lastUpdated = repo.lastUpdated,
            reminderMinutes = repo.reminderMinutes,
            weekView = repo.viewMode == "week",
            notifyChanges = repo.notifyChanges,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        if (repo.isLoggedIn) {
            viewModelScope.launch {
                val cached = repo.loadCached()
                if (cached != null) apply(cached)
                _state.update { it.copy(initialLoading = cached == null) }
                if (cached != null) onDataChanged()
                refresh()
            }
        }
    }

    private fun apply(d: SchoolData) = _state.update {
        it.copy(all = d.lectures, homework = d.homework, exams = d.exams)
    }

    fun login(link: String, user: String, password: String) {
        val (server, school) = UntisAccount.parseLink(link) ?: run {
            _state.update { it.copy(loginError = "Der WebUntis-Link sieht ungültig aus.") }
            return
        }
        if (user.isBlank() || password.isEmpty()) {
            _state.update { it.copy(loginError = "Bitte Benutzername und Passwort eingeben.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, loginError = null) }
            try {
                val data = repo.login(UntisAccount(server, school, user.trim(), password))
                apply(data)
                _state.update {
                    it.copy(
                        loggedIn = true, loggingIn = false, initialLoading = false,
                        user = user.trim(), school = school, klasse = repo.credentials.klasse,
                        lastUpdated = repo.lastUpdated,
                    )
                }
                onDataChanged()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loggingIn = false, loginError = message(e)) }
            }
        }
    }

    fun logout() {
        refreshJob?.cancel()
        repo.logout()
        _state.value = UiState(
            weekView = _state.value.weekView,
            reminderMinutes = repo.reminderMinutes,
            digestMode = repo.digestMode,
            notifyChanges = repo.notifyChanges,
        )
        onDataChanged()
    }

    fun refresh() {
        if (!_state.value.loggedIn || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                val before = _state.value.all
                val fresh = repo.fetch()
                if (before.isNotEmpty()) {
                    val changes = ChangeDetector.diff(before, fresh.lectures, repo.filter, java.time.LocalDateTime.now())
                    if (changes.isNotEmpty()) {
                        _state.update {
                            it.copy(error = if (changes.size == 1) changes.first().text else "${changes.size} Änderungen – z.B. ${changes.first().text}")
                        }
                    }
                }
                apply(fresh)
                _state.update {
                    it.copy(lastUpdated = repo.lastUpdated, klasse = repo.credentials.klasse, refreshing = false, initialLoading = false)
                }
                onDataChanged()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val relogin = e is UntisException && e.badCredentials
                _state.update { it.copy(refreshing = false, initialLoading = false, error = message(e)) }
                if (relogin) logout()
            }
        }
    }

    private fun message(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Keine Internetverbindung."
        is java.net.SocketTimeoutException -> "WebUntis antwortet nicht."
        is UntisException -> e.message ?: "WebUntis-Fehler."
        else -> e.message ?: "Aktualisieren fehlgeschlagen."
    }

    /** Beim Zurückkehren in die App aktualisieren, wenn der Stand älter als 30 min ist. */
    fun refreshIfStale() {
        val s = _state.value
        if (!s.loggedIn || s.initialLoading) return
        if (System.currentTimeMillis() - s.lastUpdated > 30 * 60 * 1000L) refresh()
    }

    fun clearLoginError() = _state.update { it.copy(loginError = null) }

    fun setModuleVisible(title: String, visible: Boolean) {
        val next = if (visible) _state.value.hidden - title else _state.value.hidden + title
        repo.hiddenModules = next
        _state.update { it.copy(hidden = next) }
        onDataChanged()
    }

    /** Einzelne Stunde aus-/wieder einblenden. */
    fun setSkipped(lecture: Lecture, skip: Boolean) {
        val k = LectureFilter.key(lecture)
        val next = if (skip) _state.value.skipped + k else _state.value.skipped - k
        repo.skipped = next
        _state.update { it.copy(skipped = next) }
        onDataChanged()
    }

    fun clearSkipped() {
        repo.skipped = emptySet()
        _state.update { it.copy(skipped = emptySet()) }
        onDataChanged()
    }

    /** Hausaufgabe lokal abhaken (wird nicht an WebUntis gesendet). */
    fun setHomeworkDone(h: Homework, done: Boolean) {
        val id = h.id.toString()
        val next = if (done) _state.value.doneHomework + id else _state.value.doneHomework - id
        repo.doneHomework = next
        _state.update { it.copy(doneHomework = next) }
    }

    fun setDigestMode(mode: Int) {
        repo.digestMode = mode
        _state.update { it.copy(digestMode = mode) }
        de.flexy.stundenplan.system.Digest.reschedule(getApplication())
    }

    fun setShowCancelled(value: Boolean) {
        repo.showCancelled = value
        _state.update { it.copy(showCancelled = value) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun setNotifyChanges(value: Boolean) {
        repo.notifyChanges = value
        _state.update { it.copy(notifyChanges = value) }
    }

    fun setReminderMinutes(minutes: Int) {
        repo.reminderMinutes = minutes
        _state.update { it.copy(reminderMinutes = minutes) }
        Reminders.reschedule(getApplication())
    }

    fun setWeekView(week: Boolean) {
        repo.viewMode = if (week) "week" else "day"
        _state.update { it.copy(weekView = week) }
    }

    /** Widget & Erinnerungen an neue Daten/Einstellungen anpassen. */
    private fun onDataChanged() {
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Reminders.reschedule(app) }
            runCatching { de.flexy.stundenplan.system.Digest.reschedule(app) }
            runCatching { NextLectureWidget.updateAll(app) }
        }
    }
}
