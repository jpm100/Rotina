package com.jpm.rotina.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpm.rotina.AppViewModel
import com.jpm.rotina.R
import com.jpm.rotina.data.Reminder
import com.jpm.rotina.data.RepeatType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderScreen(
    vm: AppViewModel,
    reminderId: Long,
    initialDate: Long,
    onBack: () -> Unit
) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(reminderId == 0L) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(initialDate)) }
    var time by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf(HabitColors[1]) }
    var notify by remember { mutableStateOf(true) }
    var repeat by remember { mutableStateOf(RepeatType.NONE) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    LaunchedEffect(reminderId, reminders) {
        if (!loaded && reminderId != 0L) {
            reminders.find { it.id == reminderId }?.let { r ->
                title = r.title
                notes = r.notes
                date = r.localDate()
                time = r.time
                color = r.color
                notify = r.notify
                repeat = r.rule
                createdAt = r.createdAt
                loaded = true
            }
        }
    }

    fun save() {
        if (title.isBlank()) {
            titleError = true
            return
        }
        vm.saveReminder(
            Reminder(
                id = reminderId,
                title = title.trim(),
                date = date.toEpochDay(),
                time = time,
                color = color,
                notes = notes.trim(),
                repeatType = repeat.id,
                notify = notify,
                createdAt = createdAt
            )
        )
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (reminderId == 0L) R.string.new_reminder else R.string.edit_reminder
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                actions = {
                    if (reminderId != 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                label = { Text(stringResource(R.string.reminder_title)) },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text(stringResource(R.string.error_title_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.reminder_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.date)) },
                supportingContent = {
                    Text(
                        date.format(
                            DateTimeFormatter.ofPattern(
                                stringResource(R.string.date_pattern_full), Locale.getDefault()
                            )
                        ).replaceFirstChar { it.uppercase(Locale.getDefault()) }
                    )
                },
                leadingContent = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                modifier = Modifier.clickable { showDatePicker = true }
            )

            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.set_time),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.set_time_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = time != null,
                        onCheckedChange = { checked ->
                            if (checked) showTimePicker = true else time = null
                        }
                    )
                }
                if (time != null) {
                    Spacer(Modifier.height(10.dp))
                    ListItem(
                        headlineContent = { Text(time!!) },
                        leadingContent = {
                            Icon(Icons.Filled.Schedule, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showTimePicker = true }
                    )
                }
            }

            RepeatPicker(
                date = date,
                selected = repeat,
                onSelect = { repeat = it }
            )

            Column {
                Text(stringResource(R.string.color), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HabitColors.forEach { c ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (c == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { color = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (c == color) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.notify_me),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (time == null) stringResource(R.string.notify_all_day_desc)
                        else stringResource(R.string.notify_timed_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = notify, onCheckedChange = { notify = it })
            }

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        // DatePicker works in UTC millis; epochDay maps to UTC midnight exactly
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val current = time?.split(":")
        val state = rememberTimePickerState(
            initialHour = current?.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = current?.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(state.hour, state.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    stringResource(
                        if (repeat == RepeatType.NONE) R.string.delete_reminder_confirm
                        else R.string.delete_repeating_reminder_confirm
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    reminders.find { it.id == reminderId }?.let { vm.deleteReminder(it) }
                    showDeleteConfirm = false
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Repeat rules are all derived from the reminder's date, so instead of spelling out the
 * grammar ("first Friday of the month") each option previews the dates it actually produces.
 */
@Composable
private fun RepeatPicker(
    date: LocalDate,
    selected: RepeatType,
    onSelect: (RepeatType) -> Unit
) {
    val options = listOf(
        RepeatType.NONE to stringResource(R.string.repeat_none),
        RepeatType.DAILY to stringResource(R.string.repeat_daily),
        RepeatType.WEEKLY to stringResource(R.string.repeat_weekly),
        RepeatType.MONTHLY_DAY to stringResource(R.string.repeat_monthly_day, date.dayOfMonth),
        RepeatType.MONTHLY_WEEK to stringResource(R.string.repeat_monthly_week),
        RepeatType.MONTHLY_LAST_WEEK to stringResource(R.string.repeat_monthly_last_week),
        RepeatType.YEARLY to stringResource(R.string.repeat_yearly)
    )
    val previewFormat = DateTimeFormatter.ofPattern(
        stringResource(R.string.preview_date_pattern), Locale.getDefault()
    )

    Column {
        Text(stringResource(R.string.repeat_section), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        options.forEach { (type, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(type) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == type, onClick = { onSelect(type) })
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    if (selected == type && type != RepeatType.NONE) {
                        val preview = Reminder(title = "", date = date.toEpochDay(), repeatType = type.id)
                            .upcomingOccurrences(date, 3)
                            .joinToString(" · ") { it.format(previewFormat) }
                        if (preview.isNotBlank()) {
                            Text(
                                stringResource(R.string.repeat_preview, preview),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
