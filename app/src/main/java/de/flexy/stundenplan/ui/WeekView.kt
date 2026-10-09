package de.flexy.stundenplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.ui.theme.moduleColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val HOUR_HEIGHT = 64.dp
private val AXIS_WIDTH = 36.dp

/** Wochenraster (Mo–Fr, bei Bedarf Sa/So) mit Zeitachse. */
@Composable
fun WeekPage(
    weekStart: LocalDate,
    today: LocalDate,
    byDay: Map<LocalDate, List<Lecture>>,
    now: LocalDateTime,
    bottomPadding: Dp,
    onClick: (Lecture) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val allDays = (0L until 7L).map { weekStart.plusDays(it) }
    val days = allDays.filter { d -> d.dayOfWeek.value <= 5 || byDay[d].orEmpty().isNotEmpty() }
    val weekLectures = days.flatMap { byDay[it].orEmpty() }

    val startHour = min(8, weekLectures.minOfOrNull { it.start.hour } ?: 8)
    val endHour = max(18, weekLectures.maxOfOrNull { if (it.end.minute > 0) it.end.hour + 1 else it.end.hour } ?: 18)
        .coerceAtMost(24)
    val hours = endHour - startHour

    Column(Modifier.fillMaxSize()) {
        // Kopfzeile mit Tagen
        Row(Modifier.fillMaxWidth().padding(start = AXIS_WIDTH, end = 8.dp, bottom = 6.dp)) {
            for (d in days) {
                val isToday = d == today
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onDayClick(d) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) cs.primary else cs.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isToday) cs.primary else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) cs.onPrimary else cs.onSurface,
                        )
                    }
                }
            }
        }

        // Raster
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomPadding + 16.dp)
        ) {
            val gridHeight = HOUR_HEIGHT * hours
            val lineColor = cs.outlineVariant.copy(alpha = 0.6f)
            Row(Modifier.fillMaxWidth().height(gridHeight).padding(end = 8.dp)) {
                // Zeitachse
                Box(Modifier.width(AXIS_WIDTH).fillMaxHeight()) {
                    for (h in 0 until hours) {
                        Text(
                            "%02d".format(startHour + h),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.offset(x = 8.dp, y = HOUR_HEIGHT * h - 6.dp),
                        )
                    }
                }
                for (d in days) {
                    val dayLectures = byDay[d].orEmpty()
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (d == today) cs.primary.copy(alpha = 0.05f) else androidx.compose.ui.graphics.Color.Transparent)
                            .drawBehind {
                                val step = HOUR_HEIGHT.toPx()
                                for (h in 0..hours) {
                                    drawLine(lineColor, Offset(0f, h * step), Offset(size.width, h * step), strokeWidth = 1f)
                                }
                                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
                            }
                    ) {
                        val laneInfo = remember(dayLectures) { assignLanes(dayLectures) }
                        val colWidth = maxWidth
                        for ((lecture, lane) in laneInfo) {
                            val top = minutesFrom(startHour, lecture.start)
                            val bottom = minutesFrom(startHour, lecture.end)
                            val laneWidth = colWidth / lane.second
                            WeekBlock(
                                lecture = lecture,
                                past = !lecture.cancelled && !now.isBefore(lecture.end),
                                modifier = Modifier
                                    .offset(x = laneWidth * lane.first, y = HOUR_HEIGHT * (top / 60f))
                                    .width(laneWidth)
                                    .height(HOUR_HEIGHT * ((bottom - top).coerceAtLeast(20) / 60f))
                                    .padding(horizontal = 1.5.dp, vertical = 1.dp),
                                onClick = { onClick(lecture) },
                            )
                        }
                        // Jetzt-Linie
                        if (d == now.toLocalDate()) {
                            val m = minutesFrom(startHour, now)
                            if (m in 0..(hours * 60)) {
                                val y = HOUR_HEIGHT * (m / 60f)
                                Box(
                                    Modifier
                                        .offset(y = y - 1.dp)
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(cs.primary)
                                )
                                Box(
                                    Modifier
                                        .offset(x = (-4).dp, y = y - 5.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(cs.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBlock(lecture: Lecture, past: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = moduleColors(lecture.title)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .alpha(if (past) 0.55f else 1f)
            .clip(shape)
            .background(if (lecture.cancelled) cs.surfaceContainerHigh else colors.container)
            .then(if (lecture.cancelled) Modifier.border(1.dp, cs.error.copy(alpha = 0.6f), shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (lecture.cancelled) cs.error else colors.accent)
        )
        Column(Modifier.padding(start = 6.dp, end = 3.dp, top = 3.dp, bottom = 2.dp)) {
            Text(
                lecture.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (lecture.cancelled) cs.onSurfaceVariant else colors.onContainer,
                textDecoration = if (lecture.cancelled) TextDecoration.LineThrough else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (lecture.location.isNotBlank()) {
                Text(
                    lecture.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (lecture.cancelled) cs.onSurfaceVariant else colors.onContainer).copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun minutesFrom(startHour: Int, t: LocalDateTime): Int = (t.hour - startHour) * 60 + t.minute

/**
 * Überlappende Termine nebeneinander anordnen.
 * Ergebnis: Termin → (Spalte, Anzahl Spalten der Überlappungsgruppe)
 */
private fun assignLanes(lectures: List<Lecture>): List<Pair<Lecture, Pair<Int, Int>>> {
    val sorted = lectures.sortedWith(compareBy<Lecture>({ it.start }, { it.end }))
    val result = ArrayList<Pair<Lecture, Pair<Int, Int>>>()
    var group = ArrayList<Pair<Lecture, Int>>()
    var groupEnd: LocalDateTime? = null
    val laneEnds = ArrayList<LocalDateTime>()

    fun flush() {
        val lanes = (group.maxOfOrNull { it.second } ?: 0) + 1
        group.forEach { (l, lane) -> result += l to (lane to lanes) }
        group = ArrayList()
        laneEnds.clear()
    }

    for (l in sorted) {
        val ge = groupEnd
        if (ge != null && !l.start.isBefore(ge)) {
            flush()
            groupEnd = null
        }
        var lane = laneEnds.indexOfFirst { !l.start.isBefore(it) }
        if (lane == -1) {
            laneEnds += l.end
            lane = laneEnds.size - 1
        } else {
            laneEnds[lane] = l.end
        }
        group += l to lane
        val cur = groupEnd
        groupEnd = if (cur == null || l.end.isAfter(cur)) l.end else cur
    }
    if (group.isNotEmpty()) flush()
    return result
}

internal fun mondayOf(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)
