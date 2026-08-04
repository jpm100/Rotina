package com.jpm.rotina.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF6750A4,
    // bitmask: bit 0 = Monday ... bit 6 = Sunday
    val days: Int = 0b1111111,
    // comma-separated "HH:mm" list, e.g. "08:00,14:30"
    val times: String = "08:00",
    val snoozeMinutes: Int = 10,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun timeList(): List<LocalTime> =
        times.split(",").filter { it.isNotBlank() }.mapNotNull {
            runCatching { LocalTime.parse(it.trim()) }.getOrNull()
        }.sorted()

    fun isScheduledOn(day: DayOfWeek): Boolean = (days shr (day.value - 1)) and 1 == 1
}

/** How a reminder repeats. The rule's parameters are derived from the reminder's start date. */
enum class RepeatType(val id: Int) {
    /** Fires once, on the start date. */
    NONE(0),
    DAILY(1),
    /** Same weekday as the start date, every week. */
    WEEKLY(2),
    /** Same day-of-month as the start date (months without that day are skipped). */
    MONTHLY_DAY(3),
    /** Same weekday, same week-of-month as the start date — "first Friday of the month". */
    MONTHLY_WEEK(4),
    /** Same weekday, last one of the month — "last Friday of the month". */
    MONTHLY_LAST_WEEK(5),
    /** Same month and day every year. */
    YEARLY(6);

    companion object {
        fun from(id: Int): RepeatType = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * A dated reminder — independent of habits. [date] is the first occurrence; [repeatType]
 * decides whether it recurs. [time] is null for an all-day entry, which notifies at
 * [ALL_DAY_NOTIFY_TIME]. Completion is per occurrence, in [ReminderDone].
 */
@Entity
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    // LocalDate.toEpochDay() of the first occurrence
    val date: Long,
    // "HH:mm", or null for an all-day reminder
    val time: String? = null,
    val color: Long = 0xFF1E88E5,
    val notes: String = "",
    val repeatType: Int = RepeatType.NONE.id,
    val notify: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val rule: RepeatType get() = RepeatType.from(repeatType)

    fun localDate(): LocalDate = LocalDate.ofEpochDay(date)

    /** The wall-clock time this reminder fires at; all-day entries use a fixed morning slot. */
    fun notifyTime(): LocalTime =
        time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: ALL_DAY_NOTIFY_TIME

    fun dateTimeOn(day: LocalDate): LocalDateTime = LocalDateTime.of(day, notifyTime())

    /** True when this reminder lands on [target]. */
    fun occursOn(target: LocalDate): Boolean {
        val start = localDate()
        if (target.isBefore(start)) return false
        return when (rule) {
            RepeatType.NONE -> target == start
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> target.dayOfWeek == start.dayOfWeek
            RepeatType.MONTHLY_DAY -> target.dayOfMonth == start.dayOfMonth
            RepeatType.MONTHLY_WEEK ->
                target.dayOfWeek == start.dayOfWeek && weekOfMonth(target) == weekOfMonth(start)
            RepeatType.MONTHLY_LAST_WEEK ->
                target.dayOfWeek == start.dayOfWeek && isLastWeekOfMonth(target)
            RepeatType.YEARLY ->
                target.monthValue == start.monthValue && target.dayOfMonth == start.dayOfMonth
        }
    }

    /** First occurrence on or after [from], or null if the rule never lands again. */
    fun occurrenceOnOrAfter(from: LocalDate): LocalDate? {
        val start = localDate()
        if (rule == RepeatType.NONE) return start.takeIf { !it.isBefore(from) }
        var day = if (from.isBefore(start)) start else from
        // 800 days covers a yearly rule plus the months a monthly rule may skip
        for (i in 0 until 800) {
            if (occursOn(day)) return day
            day = day.plusDays(1)
        }
        return null
    }

    /** The next [count] occurrences on or after [from] — used to preview a repeat rule. */
    fun upcomingOccurrences(from: LocalDate, count: Int): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var day = from
        while (result.size < count) {
            val next = occurrenceOnOrAfter(day) ?: break
            result += next
            day = next.plusDays(1)
        }
        return result
    }

    companion object {
        val ALL_DAY_NOTIFY_TIME: LocalTime = LocalTime.of(9, 0)

        /** 1 for the first occurrence of that weekday in the month, 2 for the second, ... */
        fun weekOfMonth(d: LocalDate): Int = (d.dayOfMonth - 1) / 7 + 1

        fun isLastWeekOfMonth(d: LocalDate): Boolean =
            d.dayOfMonth + 7 > d.lengthOfMonth()
    }
}

/** One completed occurrence of a reminder. */
@Entity(indices = [Index(value = ["reminderId", "date"], unique = true)])
data class ReminderDone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    // LocalDate.toEpochDay() of the occurrence that was completed
    val date: Long,
    val doneAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index(value = ["habitId", "date", "time"], unique = true)])
data class Completion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    // LocalDate.toEpochDay()
    val date: Long,
    // scheduled slot "HH:mm"
    val time: String,
    val doneAt: Long = System.currentTimeMillis()
)

@Dao
interface RotinaDao {
    @Query("SELECT * FROM Habit ORDER BY createdAt")
    fun habitsFlow(): Flow<List<Habit>>

    @Query("SELECT * FROM Habit")
    suspend fun habits(): List<Habit>

    @Query("SELECT * FROM Habit WHERE id = :id")
    suspend fun habit(id: Long): Habit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("SELECT * FROM Completion")
    fun completionsFlow(): Flow<List<Completion>>

    @Query("SELECT * FROM Completion")
    suspend fun completions(): List<Completion>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: Completion)

    @Query("DELETE FROM Completion WHERE habitId = :habitId AND date = :date AND time = :time")
    suspend fun deleteCompletion(habitId: Long, date: Long, time: String)

    @Query("SELECT COUNT(*) FROM Completion WHERE habitId = :habitId AND date = :date AND time = :time")
    suspend fun completionCount(habitId: Long, date: Long, time: String): Int

    @Query("DELETE FROM Completion WHERE habitId = :habitId")
    suspend fun deleteCompletionsFor(habitId: Long)

    @Query("DELETE FROM Habit")
    suspend fun clearHabits()

    @Query("DELETE FROM Completion")
    suspend fun clearCompletions()

    // NULL times sort first, so all-day reminders lead their day
    @Query("SELECT * FROM Reminder ORDER BY date, time")
    fun remindersFlow(): Flow<List<Reminder>>

    @Query("SELECT * FROM Reminder")
    suspend fun reminders(): List<Reminder>

    @Query("SELECT * FROM Reminder WHERE id = :id")
    suspend fun reminder(id: Long): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("DELETE FROM Reminder")
    suspend fun clearReminders()

    @Query("SELECT * FROM ReminderDone")
    fun reminderDonesFlow(): Flow<List<ReminderDone>>

    @Query("SELECT * FROM ReminderDone")
    suspend fun reminderDones(): List<ReminderDone>

    @Query("SELECT date FROM ReminderDone WHERE reminderId = :reminderId")
    suspend fun doneDatesFor(reminderId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReminderDone(done: ReminderDone)

    @Query("DELETE FROM ReminderDone WHERE reminderId = :reminderId AND date = :date")
    suspend fun deleteReminderDone(reminderId: Long, date: Long)

    @Query("DELETE FROM ReminderDone WHERE reminderId = :reminderId")
    suspend fun deleteReminderDonesFor(reminderId: Long)

    @Query("DELETE FROM ReminderDone")
    suspend fun clearReminderDones()
}

@Database(
    entities = [Habit::class, Completion::class, Reminder::class, ReminderDone::class],
    version = 3,
    exportSchema = false
)
abstract class RotinaDb : RoomDatabase() {
    abstract fun dao(): RotinaDao

    companion object {
        @Volatile
        private var instance: RotinaDb? = null

        /**
         * v1 -> v2 adds the Reminder table. Habits and completions are untouched, so an
         * existing install keeps all of its data. The column definitions must match what
         * Room generates for [Reminder] exactly, or it fails the schema check on open.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Reminder` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`time` TEXT, " +
                        "`color` INTEGER NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "`notify` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v2 -> v3 gives reminders a repeat rule and moves completion out of the row into
         * [ReminderDone], one entry per occurrence. SQLite cannot drop a column, so the
         * table is rebuilt; existing rows keep their data and any `done = 1` row becomes a
         * completed occurrence on its own date. Habits and their history are untouched.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ReminderDone` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reminderId` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`doneAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_ReminderDone_reminderId_date` ON `ReminderDone` (`reminderId`, `date`)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `ReminderDone` (`reminderId`, `date`, `doneAt`) " +
                        "SELECT `id`, `date`, `createdAt` FROM `Reminder` WHERE `done` = 1"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Reminder_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`time` TEXT, " +
                        "`color` INTEGER NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`repeatType` INTEGER NOT NULL, " +
                        "`notify` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `Reminder_new` " +
                        "(`id`, `title`, `date`, `time`, `color`, `notes`, `repeatType`, `notify`, `createdAt`) " +
                        "SELECT `id`, `title`, `date`, `time`, `color`, `notes`, 0, `notify`, `createdAt` " +
                        "FROM `Reminder`"
                )
                db.execSQL("DROP TABLE `Reminder`")
                db.execSQL("ALTER TABLE `Reminder_new` RENAME TO `Reminder`")
            }
        }

        fun get(context: Context): RotinaDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RotinaDb::class.java,
                    "rotina.db"
                )
                    // a v1 install runs both in order and lands on the same schema
                    // as a device that already had v2
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
