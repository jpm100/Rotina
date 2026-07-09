package com.jpm.rotina.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
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
import com.jpm.rotina.data.Habit
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditHabitScreen(vm: AppViewModel, habitId: Long, onBack: () -> Unit) {
    var loaded by remember { mutableStateOf(habitId == 0L) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(HabitColors[0]) }
    var days by remember { mutableStateOf(0b1111111) }
    var times by remember { mutableStateOf(listOf("08:00")) }
    var snooze by remember { mutableStateOf(10f) }
    var enabled by remember { mutableStateOf(true) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    val habits by vm.habits.collectAsStateWithLifecycle()

    LaunchedEffect(habitId, habits) {
        if (!loaded && habitId != 0L) {
            habits.find { it.id == habitId }?.let { h ->
                name = h.name
                color = h.color
                days = h.days
                times = h.times.split(",").filter { it.isNotBlank() }.sorted()
                snooze = h.snoozeMinutes.toFloat()
                enabled = h.enabled
                createdAt = h.createdAt
                loaded = true
            }
        }
    }

    fun save() {
        if (name.isBlank()) {
            nameError = true
            return
        }
        vm.saveHabit(
            Habit(
                id = habitId,
                name = name.trim(),
                color = color,
                days = days,
                times = times.sorted().joinToString(","),
                snoozeMinutes = snooze.toInt(),
                enabled = enabled,
                createdAt = createdAt
            )
        )
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (habitId == 0L) R.string.new_habit else R.string.edit_habit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (habitId != 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text(stringResource(R.string.habit_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.error_name_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text(stringResource(R.string.color), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
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

            Column {
                Text(stringResource(R.string.days_of_week), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { dow ->
                        val bit = 1 shl (dow.value - 1)
                        val selected = days and bit != 0
                        FilterChip(
                            selected = selected,
                            onClick = { days = days xor bit },
                            label = {
                                Text(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                            }
                        )
                    }
                }
            }

            Column {
                Text(stringResource(R.string.times), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.forEach { t ->
                        InputChip(
                            selected = true,
                            onClick = { if (times.size > 1) times = times - t },
                            label = { Text(t) },
                            trailingIcon = {
                                if (times.size > 1) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.delete),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                    InputChip(
                        selected = false,
                        onClick = { showTimePicker = true },
                        label = { Text(stringResource(R.string.add_time)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            Column {
                Text(
                    stringResource(R.string.snooze_minutes, snooze.toInt()),
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = snooze,
                    onValueChange = { snooze = it },
                    valueRange = 5f..60f,
                    steps = 10
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.enabled),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val slot = "%02d:%02d".format(state.hour, state.minute)
                    times = (times + slot).distinct().sorted()
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
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    habits.find { it.id == habitId }?.let { vm.deleteHabit(it) }
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
