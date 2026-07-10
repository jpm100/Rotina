package com.jpm.rotina

import android.app.Application
import android.app.NotificationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jpm.rotina.alarm.AlarmScheduler
import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.RotinaDb
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

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

    val completions = dao.completionsFlow(LocalDate.now().toEpochDay() - 400)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                val nm = getApplication<Application>()
                    .getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(habit.id.toInt())
            } else {
                dao.deleteCompletion(habit.id, date.toEpochDay(), time)
            }
        }
    }

    suspend fun exportJson(): String = com.jpm.rotina.backup.Backup.export(dao)

    suspend fun importJson(json: String): Boolean {
        val ok = com.jpm.rotina.backup.Backup.import(dao, json)
        if (ok) AlarmScheduler.rescheduleAll(getApplication())
        return ok
    }
}
