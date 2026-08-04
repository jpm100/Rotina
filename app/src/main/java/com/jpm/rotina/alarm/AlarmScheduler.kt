package com.jpm.rotina.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.Reminder
import com.jpm.rotina.data.RotinaDb
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {

    const val ACTION_REMIND = "com.jpm.rotina.REMIND"
    const val ACTION_DONE = "com.jpm.rotina.DONE"
    const val ACTION_SNOOZE = "com.jpm.rotina.SNOOZE"
    const val ACTION_REMINDER_FIRE = "com.jpm.rotina.REMINDER_FIRE"
    const val ACTION_REMINDER_DONE = "com.jpm.rotina.REMINDER_DONE"
    const val ACTION_REMINDER_SNOOZE = "com.jpm.rotina.REMINDER_SNOOZE"
    const val EXTRA_HABIT_ID = "habitId"
    const val EXTRA_REMINDER_ID = "reminderId"
    const val EXTRA_OCCURRENCE = "occurrence"
    const val EXTRA_SNOOZED = "snoozed"
    const val EXTRA_TIME = "time"

    /** Habits and one-off reminders must never share a notification id or a PendingIntent
     *  request code, or one would silently replace the other. */
    private const val REMINDER_NOTIFICATION_BASE = 1_000_000L
    private const val REMINDER_REQUEST_BASE = 500_000_000L

    const val SNOOZE_MINUTES_REMINDER = 10

    fun habitNotificationId(habitId: Long): Int = habitId.toInt()

    fun reminderNotificationId(reminderId: Long): Int =
        (REMINDER_NOTIFICATION_BASE + reminderId).toInt()

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

    /**
     * Arms the reminder's next pending occurrence — the first one still in the future that
     * has not been ticked off. Recurring reminders re-arm from here after each firing.
     */
    suspend fun scheduleReminder(context: Context, reminder: Reminder) {
        cancelReminder(context, reminder.id)
        if (!reminder.notify) return

        val doneDates = RotinaDb.get(context).dao().doneDatesFor(reminder.id).toSet()
        val day = nextPendingOccurrence(reminder, doneDates) ?: return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_FIRE
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_OCCURRENCE, day.toEpochDay())
        }
        val pi = PendingIntent.getBroadcast(
            context, reminderRequestCode(reminder.id, 0), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = reminder.dateTimeOn(day)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(context, triggerAt, pi)
    }

    /** First occurrence whose notification time is still ahead and that is not yet done. */
    private fun nextPendingOccurrence(reminder: Reminder, doneDates: Set<Long>): LocalDate? {
        val now = LocalDateTime.now()
        var day = reminder.occurrenceOnOrAfter(now.toLocalDate()) ?: return null
        // a handful of steps is enough to skip today's past/ticked-off occurrences
        for (i in 0 until 60) {
            val pending = reminder.dateTimeOn(day).isAfter(now) &&
                day.toEpochDay() !in doneDates
            if (pending) return day
            day = reminder.occurrenceOnOrAfter(day.plusDays(1)) ?: return null
        }
        return null
    }

    fun scheduleReminderSnooze(context: Context, reminderId: Long, occurrence: Long, minutes: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_FIRE
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_OCCURRENCE, occurrence)
            putExtra(EXTRA_SNOOZED, true)
        }
        val pi = PendingIntent.getBroadcast(
            context, reminderRequestCode(reminderId, 3), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, System.currentTimeMillis() + minutes * 60_000L, pi)
    }

    fun cancelReminder(context: Context, reminderId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(0, 3).forEach { kind ->
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER_FIRE
            }
            val pi = PendingIntent.getBroadcast(
                context, reminderRequestCode(reminderId, kind), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }

    suspend fun rescheduleAll(context: Context) {
        val dao = RotinaDb.get(context).dao()
        dao.habits().forEach { scheduleNext(context, it) }
        dao.reminders().forEach { scheduleReminder(context, it) }
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

    private fun reminderRequestCode(reminderId: Long, kind: Int): Int =
        (REMINDER_REQUEST_BASE + reminderId * 10 + kind).toInt()
}
