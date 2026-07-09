package com.jpm.rotina.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.RotinaDb
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {

    const val ACTION_REMIND = "com.jpm.rotina.REMIND"
    const val ACTION_DONE = "com.jpm.rotina.DONE"
    const val ACTION_SNOOZE = "com.jpm.rotina.SNOOZE"
    const val EXTRA_HABIT_ID = "habitId"
    const val EXTRA_TIME = "time"

    fun nextOccurrence(habit: Habit, from: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val times = habit.timeList()
        if (habit.days == 0 || times.isEmpty() || !habit.enabled) return null
        for (offset in 0..7) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            if (!habit.isScheduledOn(date.dayOfWeek)) continue
            for (t in times) {
                val dt = LocalDateTime.of(date, t)
                if (dt.isAfter(from)) return dt
            }
        }
        return null
    }

    fun scheduleNext(context: Context, habit: Habit) {
        val next = nextOccurrence(habit) ?: run { cancel(context, habit.id); return }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_TIME, "%02d:%02d".format(next.hour, next.minute))
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(habit.id, 0), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(context, triggerAt, pi)
    }

    fun scheduleSnooze(context: Context, habitId: Long, time: String, minutes: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_TIME, time)
            putExtra("snoozed", true)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(habitId, 3), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, System.currentTimeMillis() + minutes * 60_000L, pi)
    }

    fun cancel(context: Context, habitId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_REMIND }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(habitId, 0), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    suspend fun rescheduleAll(context: Context) {
        RotinaDb.get(context).dao().habits().forEach { scheduleNext(context, it) }
    }

    private fun setAlarm(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pi)
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun requestCode(habitId: Long, kind: Int): Int = (habitId * 10 + kind).toInt()
}
