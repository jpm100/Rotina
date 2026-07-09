package com.jpm.rotina.backup

import com.jpm.rotina.data.Completion
import com.jpm.rotina.data.Habit
import com.jpm.rotina.data.RotinaDao
import org.json.JSONArray
import org.json.JSONObject

object Backup {

    suspend fun export(dao: RotinaDao): String {
        val root = JSONObject()
        root.put("version", 1)
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
        return root.toString(2)
    }

    suspend fun import(dao: RotinaDao, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val habits = root.getJSONArray("habits")
            val completions = root.optJSONArray("completions") ?: JSONArray()

            dao.clearCompletions()
            dao.clearHabits()

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
            true
        } catch (e: Exception) {
            false
        }
    }
}
