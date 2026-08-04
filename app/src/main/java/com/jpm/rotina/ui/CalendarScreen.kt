package com.jpm.rotina.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpm.rotina.AppViewModel
import com.jpm.rotina.R
import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.Reminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(vm: AppViewModel, onEditReminder: (Long, Long) -> Unit) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditReminder(0L, selected.toEpochDay()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_reminder)) }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            item {
                MonthHeader(
                    month = month,
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onToday = {
                        month = YearMonth.from(today)
                        selected = today
                    }
                )
            }
            item {
                MonthGrid(
                    month = month,
                    today = today,
                    selected = selected,
                    habits = habits,
                    completions = completions,
                    reminders = reminders,
                    onSelect = { selected = it }
                )
            }
            item {
                HeatLegend()
                Spacer(Modifier.height(8.dp))
            }
            item {
                DayDetail(
                    date = selected,
                    today = today,
                    habits = habits,
                    completions = completions,
                    reminders = reminders.filter { it.date == selected.toEpochDay() },
                    onToggleSlot = { habit, slot, done -> vm.setDone(habit, selected, slot, done) },
                    onToggleReminder = { reminder, done -> vm.setReminderDone(reminder, done) },
                    onEditReminder = { onEditReminder(it, selected.toEpochDay()) }
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month)
            )
        }
        Text(
            month.month
                .getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase(Locale.getDefault()) } + " ${month.year}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month)
            )
        }
    }
    TextButton(onClick = onToday) { Text(stringResource(R.string.jump_to_today)) }
}

/**
 * Month grid. Each cell carries two independent encodings:
 * a sequential fill (share of that day's habit slots completed) and up to three
 * dots for the reminders on the day.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    habits: List<Habit>,
    completions: List<Completion>,
    reminders: List<Reminder>,
    onSelect: (LocalDate) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    // Monday-first grid; leading blanks pad the first week
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val cells = leadingBlanks + daysInMonth
    val rows = (cells + 6) / 7

    val doneByDay = remember(completions) { completions.groupBy { it.date } }
    val remindersByDay = remember(reminders) { reminders.groupBy { it.date } }

    Column(Modifier.padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                        .uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val dayNumber = index - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            ratio = completionRatio(date, today, habits, doneByDay),
                            reminders = remindersByDay[date.toEpochDay()].orEmpty(),
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Share of the day's scheduled habit slots that were completed, or null if nothing was due. */
private fun completionRatio(
    date: LocalDate,
    today: LocalDate,
    habits: List<Habit>,
    doneByDay: Map<Long, List<Completion>>
): Float? {
    if (date.isAfter(today)) return null
    val scheduled = habits
        .filter { it.isScheduledOn(date.dayOfWeek) }
        .sumOf { it.timeList().size }
    if (scheduled == 0) return null
    val done = doneByDay[date.toEpochDay()]?.size ?: 0
    return (done.toFloat() / scheduled).coerceIn(0f, 1f)
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    ratio: Float?,
    reminders: List<Reminder>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    // single-hue sequential fill: empty stays bare, fuller days read stronger
    val fill = when {
        ratio == null || ratio <= 0f -> Color.Transparent
        else -> accent.copy(alpha = 0.15f + 0.55f * ratio)
    }
    // the fill is translucent, so pick the number's ink from what it actually
    // composites to — this keeps it legible in both light and dark themes
    val numberColor = when {
        ratio == null || ratio <= 0f ->
            if (isToday) accent else MaterialTheme.colorScheme.onSurface
        else -> {
            val blended = fill.compositeOver(surface)
            if (blended.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else Color.White
        }
    }

    Box(
        modifier
            .aspectRatio(0.95f)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(fill)
                .then(
                    when {
                        isSelected -> Modifier.border(2.dp, accent, CircleShape)
                        isToday -> Modifier.border(
                            1.dp, MaterialTheme.colorScheme.outline, CircleShape
                        )
                        else -> Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = numberColor
                )
                if (reminders.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        reminders.take(3).forEach { r ->
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (r.done) MaterialTheme.colorScheme.outlineVariant
                                        else Color(r.color)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatLegend() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            stringResource(R.string.legend_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        listOf(0f, 0.33f, 0.66f, 1f).forEach { step ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (step == 0f) Color.Transparent
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + 0.55f * step)
                    )
                    .then(
                        if (step == 0f) Modifier.border(
                            1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape
                        ) else Modifier
                    )
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.legend_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    today: LocalDate,
    habits: List<Habit>,
    completions: List<Completion>,
    reminders: List<Reminder>,
    onToggleSlot: (Habit, String, Boolean) -> Unit,
    onToggleReminder: (Reminder, Boolean) -> Unit,
    onEditReminder: (Long) -> Unit
) {
    val dayHabits = habits.filter { it.enabled && it.isScheduledOn(date.dayOfWeek) }
    val doneSlots = completions
        .filter { it.date == date.toEpochDay() }
        .map { it.habitId to it.time }
        .toSet()

    Column(Modifier.padding(top = 12.dp)) {
        Text(
            date.format(
                java.time.format.DateTimeFormatter.ofPattern(
                    stringResource(R.string.date_pattern), Locale.getDefault()
                )
            ).replaceFirstChar { it.uppercase(Locale.getDefault()) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        if (reminders.isEmpty() && dayHabits.isEmpty()) {
            Text(
                stringResource(R.string.nothing_on_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        reminders.forEach { reminder ->
            ReminderRow(
                reminder = reminder,
                onToggle = { onToggleReminder(reminder, !reminder.done) },
                onClick = { onEditReminder(reminder.id) }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (dayHabits.isNotEmpty()) {
            if (reminders.isNotEmpty()) Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.habits_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            dayHabits.forEach { habit ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(habit.color))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                habit.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            habit.timeList().forEach { t ->
                                val slot = "%02d:%02d".format(t.hour, t.minute)
                                val done = (habit.id to slot) in doneSlots
                                SlotPill(
                                    time = slot,
                                    done = done,
                                    accent = Color(habit.color),
                                    onClick = { onToggleSlot(habit, slot, !done) }
                                )
                            }
                        }
                    }
                }
            }
            if (date.isAfter(today)) {
                Text(
                    stringResource(R.string.future_day_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderRow(reminder: Reminder, onToggle: () -> Unit, onClick: () -> Unit) {
    val accent = Color(reminder.color)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = if (reminder.done) accent else Color.Transparent,
                border = if (reminder.done) null else BorderStroke(2.dp, accent),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (reminder.done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (reminder.done) TextDecoration.LineThrough else null,
                    color = if (reminder.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                val subtitle = reminder.time ?: stringResource(R.string.all_day)
                Text(
                    if (reminder.notes.isBlank()) subtitle else "$subtitle · ${reminder.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
