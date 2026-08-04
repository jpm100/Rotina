package com.jpm.rotina.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jpm.rotina.MainActivity
import com.jpm.rotina.R
import com.jpm.rotina.RotinaApp
import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Reminder
import com.jpm.rotina.data.RotinaDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_REMINDER_FIRE,
            AlarmScheduler.ACTION_REMINDER_DONE,
            AlarmScheduler.ACTION_REMINDER_SNOOZE -> {
                handleReminder(context, intent)
                return
            }
        }

        val habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1)
        if (habitId < 0) return
        val time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME) ?: ""
        val snoozed = intent.getBooleanExtra("snoozed", false)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = RotinaDb.get(context).dao()
                when (intent.action) {
                    AlarmScheduler.ACTION_REMIND -> {
                        val habit = dao.habit(habitId)
                        if (habit != null && habit.enabled) {
                            val alreadyDone = dao.completionCount(
                                habitId, LocalDate.now().toEpochDay(), time
                            ) > 0
                            if (!alreadyDone) {
                                showNotification(context, habit.id, habit.name, habit.color, time)
                            }
                            if (!snoozed) AlarmScheduler.scheduleNext(context, habit)
                        }
                    }
                    AlarmScheduler.ACTION_DONE -> {
                        dao.insertCompletion(
                            Completion(habitId = habitId, date = LocalDate.now().toEpochDay(), time = time)
                        )
                        notificationManager(context).cancel(AlarmScheduler.habitNotificationId(habitId))
                    }
                    AlarmScheduler.ACTION_SNOOZE -> {
                        val habit = dao.habit(habitId)
                        if (habit != null) {
                            AlarmScheduler.scheduleSnooze(context, habitId, time, habit.snoozeMinutes)
                        }
                        notificationManager(context).cancel(AlarmScheduler.habitNotificationId(habitId))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId < 0) return
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = RotinaDb.get(context).dao()
                when (intent.action) {
                    AlarmScheduler.ACTION_REMINDER_FIRE -> {
                        val reminder = dao.reminder(reminderId)
                        if (reminder != null && !reminder.done) {
                            showReminderNotification(context, reminder)
                        }
                    }
                    AlarmScheduler.ACTION_REMINDER_DONE -> {
                        dao.setReminderDone(reminderId, true)
                        AlarmScheduler.cancelReminder(context, reminderId)
                        notificationManager(context)
                            .cancel(AlarmScheduler.reminderNotificationId(reminderId))
                    }
                    AlarmScheduler.ACTION_REMINDER_SNOOZE -> {
                        AlarmScheduler.scheduleReminderSnooze(
                            context, reminderId, AlarmScheduler.SNOOZE_MINUTES_REMINDER
                        )
                        notificationManager(context)
                            .cancel(AlarmScheduler.reminderNotificationId(reminderId))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showReminderNotification(context: Context, reminder: Reminder) {
        val openApp = PendingIntent.getActivity(
            context, AlarmScheduler.reminderNotificationId(reminder.id),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val donePi = PendingIntent.getBroadcast(
            context, AlarmScheduler.reminderNotificationId(reminder.id) + 1,
            Intent(context, ReminderReceiver::class.java).apply {
                action = AlarmScheduler.ACTION_REMINDER_DONE
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getBroadcast(
            context, AlarmScheduler.reminderNotificationId(reminder.id) + 2,
            Intent(context, ReminderReceiver::class.java).apply {
                action = AlarmScheduler.ACTION_REMINDER_SNOOZE
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, RotinaApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(
                reminder.notes.ifBlank { context.getString(R.string.notif_reminder_text) }
            )
            .setColor(reminder.color.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_done), donePi)
            .addAction(0, context.getString(R.string.action_snooze), snoozePi)

        if (reminder.notes.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(reminder.notes))
        }

        try {
            notificationManager(context)
                .notify(AlarmScheduler.reminderNotificationId(reminder.id), builder.build())
        } catch (e: SecurityException) {
            // notification permission revoked; nothing to do
        }
    }

    private fun showNotification(context: Context, habitId: Long, name: String, color: Long, time: String) {
        val openApp = PendingIntent.getActivity(
            context, habitId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_DONE
            putExtra(AlarmScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(AlarmScheduler.EXTRA_TIME, time)
        }
        val donePi = PendingIntent.getBroadcast(
            context, (habitId * 10 + 1).toInt(), doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(AlarmScheduler.EXTRA_TIME, time)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context, (habitId * 10 + 2).toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RotinaApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(context.getString(R.string.notif_text, time))
            .setColor(color.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_done), donePi)
            .addAction(0, context.getString(R.string.action_snooze), snoozePi)
            .build()

        try {
            notificationManager(context)
                .notify(AlarmScheduler.habitNotificationId(habitId), notification)
        } catch (e: SecurityException) {
            // notification permission revoked; nothing to do
        }
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
