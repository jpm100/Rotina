package com.jpm.rotina

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jpm.rotina.alarm.AlarmScheduler
import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.RotinaDb
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = RotinaDb.get(app).dao()

    val habits = dao.habitsFlow()
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
