package com.jpm.rotina.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpm.rotina.AppViewModel
import com.jpm.rotina.R
import com.jpm.rotina.TodayItem
import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.Reminder
import com.jpm.rotina.data.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val StreakOrange = Color(0xFFFF7043)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, onEdit: (Long) -> Unit, onEditReminder: (Long, Long) -> Unit) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val todayItems by vm.todayItems.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_habit)) }
            )
        }
    ) { padding ->
        if (habits.isEmpty() && todayItems.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌱", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_habits),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                TodayHeader(habits, completions, reminders, today)
            }
            items(
                todayItems,
                key = { item ->
                    when (item) {
                        is TodayItem.HabitItem -> "h${item.habit.id}"
                        is TodayItem.ReminderItem -> "r${item.reminder.id}"
                    }
                }
            ) { item ->
                when (item) {
                    is TodayItem.HabitItem -> HabitCard(
                        habit = item.habit,
                        completions = completions,
                        today = today,
                        onToggle = { slot, done -> vm.setDone(item.habit, today, slot, done) },
                        onClick = { onEdit(item.habit.id) }
                    )
                    is TodayItem.ReminderItem -> ReminderRow(
                        reminder = item.reminder,
                        onToggle = { vm.setReminderDone(item.reminder, !item.reminder.done) },
                        onClick = { onEditReminder(item.reminder.id, today.toEpochDay()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayHeader(
    habits: List<Habit>,
    completions: List<Completion>,
    reminders: List<Reminder>,
    today: LocalDate
) {
    val dateText = today
        .format(
            DateTimeFormatter.ofPattern(
                stringResource(R.string.date_pattern),
                Locale.getDefault()
            )
        )
        .replaceFirstChar { it.uppercase(Locale.getDefault()) }

    val scheduledSlots = habits
        .filter { it.enabled && it.isScheduledOn(today.dayOfWeek) }
        .flatMap { h -> h.timeList().map { t -> h.id to "%02d:%02d".format(t.hour, t.minute) } }
    val doneSet = completions
        .filter { it.date == today.toEpochDay() }
        .map { it.habitId to it.time }
        .toSet()
    val todayReminders = reminders.filter { it.date == today.toEpochDay() }
    // the day's progress counts habit slots and one-off reminders alike
    val doneCount = scheduledSlots.count { it in doneSet } + todayReminders.count { it.done }
    val total = scheduledSlots.size + todayReminders.size

    Column(Modifier.padding(bottom = 8.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            dateText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.home_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        if (total > 0) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (doneCount == total) stringResource(R.string.all_done)
                            else stringResource(R.string.progress_done, doneCount, total),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${if (total == 0) 0 else doneCount * 100 / total}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else doneCount.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitCard(
    habit: Habit,
    completions: List<Completion>,
    today: LocalDate,
    onToggle: (slot: String, done: Boolean) -> Unit,
    onClick: () -> Unit
) {
    val accent = Color(habit.color)
    val stats = Stats.compute(habit, completions, today)
    val scheduledToday = habit.isScheduledOn(today.dayOfWeek)
    val doneToday = completions
        .filter { it.habitId == habit.id && it.date == today.toEpochDay() }
        .map { it.time }
        .toSet()

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .padding(vertical = 16.dp)
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 15.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (stats.currentStreak > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = StreakOrange.copy(alpha = 0.15f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = StreakOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "${stats.currentStreak}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                when {
                    !habit.enabled -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.paused),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    !scheduledToday -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.not_today),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            habit.timeList().forEach { t ->
                                val slot = "%02d:%02d".format(t.hour, t.minute)
                                val done = doneToday.contains(slot)
                                SlotPill(
                                    time = slot,
                                    done = done,
                                    accent = accent,
                                    onClick = { onToggle(slot, !done) }
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
fun SlotPill(time: String, done: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (done) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = if (done) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (done) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                time,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (done) FontWeight.SemiBold else FontWeight.Medium,
                color = if (done) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
