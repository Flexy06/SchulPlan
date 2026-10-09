package de.flexy.stundenplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.flexy.stundenplan.data.Exam
import de.flexy.stundenplan.data.Homework
import de.flexy.stundenplan.ui.theme.moduleColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)

/** "heute", "morgen", "in 3 Tagen", "Mo, 12. Okt" … */
internal fun relativeDay(d: LocalDate, today: LocalDate = LocalDate.now()): String =
    when (val n = ChronoUnit.DAYS.between(today, d)) {
        0L -> "heute"
        1L -> "morgen"
        -1L -> "gestern"
        in 2..6 -> "in $n Tagen"
        else -> d.format(DAY)
    }

/** Hausaufgaben & Prüfungen. */
@Composable
fun TasksScreen(state: UiState, vm: TimetableViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "Hausaufgaben" else "Prüfungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text("Hausaufgaben (${state.openHomework.size})")
                }
                SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text("Prüfungen (${state.upcomingExams.size})")
                }
            }
            if (tab == 0) {
                val open = state.homework.filter { !state.isDone(it) && !it.due.isBefore(today) }
                val done = state.homework.filter { state.isDone(it) && !it.due.isBefore(today.minusDays(7)) }
                val overdue = state.homework.filter { !state.isDone(it) && it.due.isBefore(today) && !it.due.isBefore(today.minusDays(14)) }
                if (open.isEmpty() && done.isEmpty() && overdue.isEmpty()) {
                    Empty("Keine Hausaufgaben", "Hausaufgaben aus WebUntis erscheinen hier, sobald Lehrkräfte sie eintragen.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (overdue.isNotEmpty()) {
                            item { Section("Überfällig") }
                            items(overdue, key = { "o" + it.id }) { HomeworkCard(it, false, overdue = true) { d -> vm.setHomeworkDone(it, d) } }
                        }
                        if (open.isNotEmpty()) {
                            item { Section("Offen") }
                            items(open, key = { "h" + it.id }) { HomeworkCard(it, false) { d -> vm.setHomeworkDone(it, d) } }
                        }
                        if (done.isNotEmpty()) {
                            item { Section("Erledigt") }
                            items(done, key = { "d" + it.id }) { HomeworkCard(it, true) { d -> vm.setHomeworkDone(it, d) } }
                        }
                    }
                }
            } else {
                val exams = state.upcomingExams
                if (exams.isEmpty()) {
                    Empty("Keine Prüfungen", "Angekündigte Klassenarbeiten und Tests aus WebUntis erscheinen hier.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(exams, key = { "e" + it.id + it.date }) { ExamCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Empty(title: String, text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Rounded.Assignment, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HomeworkCard(h: Homework, done: Boolean, overdue: Boolean = false, onDone: (Boolean) -> Unit) {
    val c = moduleColors(h.subject)
    Card(
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.onContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().then(if (done) Modifier.alpha(0.6f) else Modifier),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = done, onCheckedChange = onDone)
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.subject.ifBlank { "Hausaufgabe" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "fällig " + relativeDay(h.due),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (overdue) MaterialTheme.colorScheme.error else c.accent,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    h.text.ifBlank { "(kein Text)" },
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                if (h.remark.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(h.remark, style = MaterialTheme.typography.bodySmall, color = c.onContainer.copy(alpha = 0.75f))
                }
                val meta = listOfNotNull(
                    "aufgegeben " + relativeDay(h.date),
                    h.teacher.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Spacer(Modifier.height(4.dp))
                Text(meta, style = MaterialTheme.typography.labelSmall, color = c.onContainer.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun ExamCard(e: Exam) {
    val c = moduleColors(e.subject)
    val days = ChronoUnit.DAYS.between(LocalDate.now(), e.date)
    Card(
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.onContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = c.accent, contentColor = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.width(56.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (days <= 0) "heute" else days.toString(), style = MaterialTheme.typography.titleLarge)
                    if (days > 0) Text(if (days == 1L) "Tag" else "Tage", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.subject.ifBlank { e.name.ifBlank { "Prüfung" } }, style = MaterialTheme.typography.titleMedium)
                val kind = listOf(e.type, e.name).filter { it.isNotBlank() && it != e.subject }.distinct().joinToString(" · ")
                if (kind.isNotBlank()) Text(kind, style = MaterialTheme.typography.bodyMedium)
                val time = if (e.start != null && e.end != null) " · ${e.start.format(TIME)}–${e.end.format(TIME)}" else ""
                Text(
                    e.date.format(DAY) + time + (if (e.rooms.isNotBlank()) " · ${e.rooms}" else ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onContainer.copy(alpha = 0.8f),
                )
                if (e.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Icon(Icons.Rounded.EditNote, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(e.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
