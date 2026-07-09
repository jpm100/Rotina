package com.jpm.rotina.data

import java.time.LocalDate

data class HabitStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val completionRate30d: Int,
    val doneLast7Days: List<Pair<Int, Int>> // (done, scheduled) per day, oldest first
)

object Stats {

    fun compute(habit: Habit, completions: List<Completion>, today: LocalDate = LocalDate.now()): HabitStats {
        val mine = completions.filter { it.habitId == habit.id }
        val doneDays = mine.map { it.date }.toSet()
        val slotCount = habit.timeList().size.coerceAtLeast(1)
        val created = LocalDate.ofEpochDay(
            java.time.Instant.ofEpochMilli(habit.createdAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        )

        // current streak: consecutive scheduled days with at least one completion,
        // counting backwards; an incomplete today does not break the streak.
        var currentStreak = 0
        var day = today
        var first = true
        while (!day.isBefore(created)) {
            if (habit.isScheduledOn(day.dayOfWeek)) {
                if (doneDays.contains(day.toEpochDay())) {
                    currentStreak++
                } else if (first && day == today) {
                    // today not done yet: skip without breaking
                } else {
                    break
                }
                first = false
            }
            day = day.minusDays(1)
            if (today.toEpochDay() - day.toEpochDay() > 3660) break
        }

        // best streak over habit lifetime
        var bestStreak = 0
        var run = 0
        var d = created
        while (!d.isAfter(today)) {
            if (habit.isScheduledOn(d.dayOfWeek)) {
                if (doneDays.contains(d.toEpochDay())) {
                    run++
                    if (run > bestStreak) bestStreak = run
                } else if (d != today) {
                    run = 0
                }
            }
            d = d.plusDays(1)
        }

        // completion rate over the last 30 days (per slot)
        var scheduledSlots = 0
        var doneSlots = 0
        for (i in 0 until 30) {
            val dd = today.minusDays(i.toLong())
            if (dd.isBefore(created)) break
            if (habit.isScheduledOn(dd.dayOfWeek)) {
                scheduledSlots += slotCount
                doneSlots += mine.count { it.date == dd.toEpochDay() }
            }
        }
        val rate = if (scheduledSlots == 0) 0 else (doneSlots * 100 / scheduledSlots).coerceAtMost(100)

        // last 7 days chart data, oldest first
        val week = (6 downTo 0).map { i ->
            val dd = today.minusDays(i.toLong())
            val scheduled = if (habit.isScheduledOn(dd.dayOfWeek) && !dd.isBefore(created)) slotCount else 0
            val done = mine.count { it.date == dd.toEpochDay() }
            Pair(done.coerceAtMost(slotCount), scheduled)
        }

        return HabitStats(currentStreak, bestStreak, rate, week)
    }
}
