package com.jpm.rotina.backup

import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.Reminder
import com.jpm.rotina.data.RotinaDao
import org.json.JSONArray
import org.json.JSONObject

object Backup {

    suspend fun export(dao: RotinaDao): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val habits = JSONArray()
        dao.habits().forEach { h ->
            habits.put(JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("color", h.color)
                put("days", h.days)
                put("times", h.times)
                put("snoozeMinutes", h.snoozeMinutes)
                put("enabled", h.enabled)
                put("createdAt", h.createdAt)
            })
        }
        root.put("habits", habits)

        val completions = JSONArray()
        dao.completions().forEach { c ->
            completions.put(JSONObject().apply {
                put("habitId", c.habitId)
                put("date", c.date)
                put("time", c.time)
                put("doneAt", c.doneAt)
            })
        }
        root.put("completions", completions)

        val reminders = JSONArray()
        dao.reminders().forEach { r ->
            reminders.put(JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("date", r.date)
                put("time", r.time ?: JSONObject.NULL)
                put("color", r.color)
                put("notes", r.notes)
                put("done", r.done)
                put("notify", r.notify)
                put("createdAt", r.createdAt)
            })
        }
        root.put("reminders", reminders)
        return root.toString(2)
    }

    suspend fun import(dao: RotinaDao, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val habits = root.getJSONArray("habits")
            val completions = root.optJSONArray("completions") ?: JSONArray()
            // absent in v1 backups, which restore with no reminders
            val reminders = root.optJSONArray("reminders") ?: JSONArray()

            dao.clearCompletions()
            dao.clearHabits()
            dao.clearReminders()

            for (i in 0 until habits.length()) {
                val h = habits.getJSONObject(i)
                dao.insertHabit(
                    Habit(
                        id = h.optLong("id", 0),
                        name = h.getString("name"),
                        color = h.optLong("color", 0xFF6750A4),
                        days = h.optInt("days", 127),
                        times = h.optString("times", "08:00"),
                        snoozeMinutes = h.optInt("snoozeMinutes", 10),
                        enabled = h.optBoolean("enabled", true),
                        createdAt = h.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            for (i in 0 until completions.length()) {
                val c = completions.getJSONObject(i)
                dao.insertCompletion(
                    Completion(
                        habitId = c.getLong("habitId"),
                        date = c.getLong("date"),
                        time = c.getString("time"),
                        doneAt = c.optLong("doneAt", System.currentTimeMillis())
                    )
                )
            }
            for (i in 0 until reminders.length()) {
                val r = reminders.getJSONObject(i)
                dao.insertReminder(
                    Reminder(
                        id = r.optLong("id", 0),
                        title = r.getString("title"),
                        date = r.getLong("date"),
                        time = if (r.isNull("time")) null else r.optString("time").ifBlank { null },
                        color = r.optLong("color", 0xFF1E88E5),
                        notes = r.optString("notes", ""),
                        done = r.optBoolean("done", false),
                        notify = r.optBoolean("notify", true),
                        createdAt = r.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
