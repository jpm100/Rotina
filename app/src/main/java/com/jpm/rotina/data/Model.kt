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

/**
 * A one-off, dated reminder — independent of habits.
 * [time] is null for an all-day entry, which notifies at [ALL_DAY_NOTIFY_TIME].
 */
@Entity
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    // LocalDate.toEpochDay()
    val date: Long,
    // "HH:mm", or null for an all-day reminder
    val time: String? = null,
    val color: Long = 0xFF1E88E5,
    val notes: String = "",
    val done: Boolean = false,
    val notify: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun localDate(): LocalDate = LocalDate.ofEpochDay(date)

    /** The wall-clock time this reminder fires at; all-day entries use a fixed morning slot. */
    fun notifyTime(): LocalTime =
        time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: ALL_DAY_NOTIFY_TIME

    fun dateTime(): LocalDateTime = LocalDateTime.of(localDate(), notifyTime())

    companion object {
        val ALL_DAY_NOTIFY_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

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

    @Query("UPDATE Reminder SET done = :done WHERE id = :id")
    suspend fun setReminderDone(id: Long, done: Boolean)

    @Query("DELETE FROM Reminder")
    suspend fun clearReminders()
}

@Database(
    entities = [Habit::class, Completion::class, Reminder::class],
    version = 2,
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

        fun get(context: Context): RotinaDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RotinaDb::class.java,
                    "rotina.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
