package com.jpm.rotina

import android.app.Application
import android.app.NotificationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jpm.rotina.alarm.AlarmScheduler
import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.Reminder
import com.jpm.rotina.data.ReminderDone
import com.jpm.rotina.data.RotinaDb
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** One row of the "Today" timeline — a habit or a dated reminder, ordered by clock time. */
sealed interface TodayItem {
    val sortTime: LocalTime?

    data class HabitItem(val habit: Habit) : TodayItem {
        override val sortTime: LocalTime? = habit.timeList().firstOrNull()
    }

    data class ReminderItem(val reminder: Reminder) : TodayItem {
        // an all-day reminder has no clock time and leads the day
        override val sortTime: LocalTime? = reminder.time?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = RotinaDb.get(app).dao()

    // chronological order: habit with the earliest first reminder comes first;
    // paused habits go to the bottom
    val habits = dao.habitsFlow()
        .map { list ->
            list.sortedWith(
                compareBy(
                    { !it.enabled },
                    { it.timeList().firstOrNull() ?: LocalTime.MAX },
                    { it.name.lowercase() }
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completions = dao.completionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders = dao.remindersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Completed reminder occurrences, as (reminderId, epochDay) pairs. */
    val reminderDones = dao.reminderDonesFlow()
        .map { list -> list.map { it.reminderId to it.date }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Habits scheduled today plus today's reminder occurrences, in one ordered list. */
    val todayItems = combine(habits, reminders) { habitList, reminderList ->
        val today = LocalDate.now()
        val items = buildList<TodayItem> {
            habitList.forEach { add(TodayItem.HabitItem(it)) }
            reminderList
                .filter { it.occursOn(today) }
                .forEach { add(TodayItem.ReminderItem(it)) }
        }
        items.sortedWith(
            compareBy(
                // paused habits sink to the bottom; everything else keeps clock order
                { it is TodayItem.HabitItem && !it.habit.enabled },
                { it.sortTime ?: LocalTime.MIN }
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            AlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun saveHabit(habit: Habit) {
        viewModelScope.launch {
            val id = dao.insertHabit(habit)
            val saved = habit.copy(id = if (habit.id == 0L) id else habit.id)
            AlarmScheduler.cancel(getApplication(), saved.id)
            AlarmScheduler.scheduleNext(getApplication(), saved)
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), habit.id)
            dao.deleteCompletionsFor(habit.id)
            dao.deleteHabit(habit)
        }
    }

    fun setDone(habit: Habit, date: LocalDate, time: String, done: Boolean) {
        viewModelScope.launch {
            if (done) {
                dao.insertCompletion(Completion(habitId = habit.id, date = date.toEpochDay(), time = time))
                // dismiss the reminder notification if it is currently showing
                notificationManager().cancel(AlarmScheduler.habitNotificationId(habit.id))
            } else {
                dao.deleteCompletion(habit.id, date.toEpochDay(), time)
            }
        }
    }

    fun saveReminder(reminder: Reminder) {
        viewModelScope.launch {
            val id = dao.insertReminder(reminder)
            val saved = reminder.copy(id = if (reminder.id == 0L) id else reminder.id)
            AlarmScheduler.scheduleReminder(getApplication(), saved)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            AlarmScheduler.cancelReminder(getApplication(), reminder.id)
            notificationManager().cancel(AlarmScheduler.reminderNotificationId(reminder.id))
            dao.deleteReminderDonesFor(reminder.id)
            dao.deleteReminder(reminder)
        }
    }

    /** Ticks a single occurrence of [reminder] on [date] off, or puts it back. */
    fun setReminderDone(reminder: Reminder, date: LocalDate, done: Boolean) {
        viewModelScope.launch {
            if (done) {
                dao.insertReminderDone(
                    ReminderDone(reminderId = reminder.id, date = date.toEpochDay())
                )
                notificationManager().cancel(AlarmScheduler.reminderNotificationId(reminder.id))
            } else {
                dao.deleteReminderDone(reminder.id, date.toEpochDay())
            }
            // the next pending occurrence may have moved either way
            AlarmScheduler.scheduleReminder(getApplication(), reminder)
        }
    }

    suspend fun exportJson(): String = com.jpm.rotina.backup.Backup.export(dao)

    suspend fun importJson(json: String): Boolean {
        val ok = com.jpm.rotina.backup.Backup.import(dao, json)
        if (ok) AlarmScheduler.rescheduleAll(getApplication())
        return ok
    }

    private fun notificationManager(): NotificationManager =
        getApplication<Application>()
            .getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
}
