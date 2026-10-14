package com.addhd.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, sort ASC, id DESC")
    fun all(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE blockDayEpochDay = :day ORDER BY blockStartMin ASC")
    fun blocksForDay(day: Long): Flow<List<TaskEntity>>

    // Open, top-level, time-blocked quests on or after [fromDay] — the set that needs alarms
    // (re)armed. Suspend one-shot for the scheduler + BootReceiver, which have no Flow lifecycle.
    @Query("SELECT * FROM tasks WHERE done = 0 AND parentId IS NULL AND blockStartMin IS NOT NULL AND blockDayEpochDay >= :fromDay ORDER BY blockDayEpochDay ASC, blockStartMin ASC")
    suspend fun scheduledBlocksFrom(fromDay: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE done = 0 AND blockStartMin IS NULL AND parentId IS NULL ORDER BY energy ASC, sort ASC")
    fun unscheduledOpen(): Flow<List<TaskEntity>>

    @Insert suspend fun insert(t: TaskEntity): Long
    @Update suspend fun update(t: TaskEntity)
    @Delete suspend fun delete(t: TaskEntity)

    // Single-row read for background callers (notification actions) that hold only an id and must
    // re-check the live state before awarding, rather than trusting a possibly-stale snapshot.
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE done = 0 AND blockStartMin IS NULL AND parentId IS NULL ORDER BY energy ASC, sort ASC")
    suspend fun unscheduledOpenList(): List<TaskEntity>

    // All open tasks (scheduled or not) — auto-schedule re-plans the whole set each run so it's
    // idempotent and never stacks freshly-added quests on top of the existing plan. Top-level
    // quests only — micro-steps never get their own time block.
    @Query("SELECT * FROM tasks WHERE done = 0 AND parentId IS NULL ORDER BY energy ASC, sort ASC")
    suspend fun openList(): List<TaskEntity>

    @Query("UPDATE tasks SET blockDayEpochDay = :day, blockStartMin = :start, blockPinned = 1 WHERE id = :id")
    suspend fun pinBlock(id: Long, day: Long, start: Int)

    @Query("UPDATE tasks SET blockDayEpochDay = :day, blockStartMin = :start, blockPinned = 0 WHERE id = :id")
    suspend fun assignBlock(id: Long, day: Long, start: Int)

    // Clear ALL blocks for the day (pins included) — used before a full re-plan and by "Clear".
    @Query("UPDATE tasks SET blockDayEpochDay = NULL, blockStartMin = NULL, blockPinned = 0 WHERE blockDayEpochDay = :day")
    suspend fun clearDay(day: Long)

    @Query("SELECT COUNT(*) FROM tasks WHERE done = 1 AND dueEpochDay = :day AND parentId IS NULL")
    fun doneCountForDay(day: Long): Flow<Int>

    // ---- Quest Archive & Purge ----
    // How many completed tasks are older than the cutoff epochDay (about to be purged).
    @Query("SELECT COUNT(*) FROM tasks WHERE done = 1 AND completedAtEpochDay IS NOT NULL AND completedAtEpochDay < :cutoffEpochDay")
    suspend fun countCompletedBefore(cutoffEpochDay: Long): Int

    // Permanently delete completed tasks older than the cutoff (their lifetime credit is
    // recorded on the pet row before this runs).
    @Query("DELETE FROM tasks WHERE done = 1 AND completedAtEpochDay IS NOT NULL AND completedAtEpochDay < :cutoffEpochDay")
    suspend fun purgeCompletedBefore(cutoffEpochDay: Long)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id ASC")
    fun all(): Flow<List<HabitEntity>>

    @Insert suspend fun insert(h: HabitEntity): Long
    @Update suspend fun update(h: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun check(c: HabitCheckEntity)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND epochDay = :day")
    suspend fun uncheck(habitId: Long, day: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM habit_checks WHERE habitId = :habitId AND epochDay = :day)")
    suspend fun isChecked(habitId: Long, day: Long): Boolean

    // Permanent delete: remove the habit row and all of its check history so no orphaned
    // check rows (which back the derived daily task) survive.
    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Long)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId")
    suspend fun deleteChecksFor(habitId: Long)

    @Query("SELECT epochDay FROM habit_checks WHERE habitId = :habitId ORDER BY epochDay DESC")
    fun checksFor(habitId: Long): Flow<List<Long>>

    @Query("SELECT * FROM habit_checks")
    fun allChecks(): Flow<List<HabitCheckEntity>>
}

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_logs ORDER BY epochMillis DESC")
    fun all(): Flow<List<MoodEntity>>

    @Insert suspend fun insert(m: MoodEntity): Long

    @Query("SELECT * FROM mood_logs ORDER BY epochMillis DESC LIMIT 1")
    fun latest(): Flow<MoodEntity?>
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal ORDER BY epochMillis DESC")
    fun all(): Flow<List<JournalEntity>>

    @Insert suspend fun insert(j: JournalEntity): Long
    @Delete suspend fun delete(j: JournalEntity)
}

@Dao
interface DopamineDao {
    @Query("SELECT * FROM dopamine_logs ORDER BY epochMillis DESC")
    fun all(): Flow<List<DopamineEntity>>

    @Insert suspend fun insert(d: DopamineEntity): Long

    @Query("SELECT COUNT(*) FROM dopamine_logs WHERE rodeItOut = 1 AND epochMillis >= :since")
    fun ridesSince(since: Long): Flow<Int>
}

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    fun observe(): Flow<PetEntity?>

    @Query("SELECT * FROM pet WHERE id = 1")
    suspend fun get(): PetEntity?

    @Upsert suspend fun upsert(p: PetEntity)
}

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY sort ASC")
    fun campaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE active = 1 LIMIT 1")
    fun activeCampaign(): Flow<CampaignEntity?>

    @Query("SELECT * FROM campaign_milestones ORDER BY campaignKey ASC, `order` ASC")
    fun milestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT COUNT(*) FROM campaigns")
    suspend fun campaignCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCampaigns(items: List<CampaignEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestones(items: List<MilestoneEntity>)

    @Query("SELECT * FROM campaigns WHERE campaignKey = :key")
    suspend fun getCampaign(key: String): CampaignEntity?

    @Update suspend fun updateCampaign(c: CampaignEntity)
    @Update suspend fun updateMilestone(m: MilestoneEntity)

    @Query("SELECT * FROM campaign_milestones WHERE campaignKey = :key AND done = 0 ORDER BY `order` ASC LIMIT 1")
    suspend fun firstOpenMilestone(key: String): MilestoneEntity?

    @Query("SELECT * FROM campaign_milestones WHERE campaignKey = :key ORDER BY `order` ASC")
    suspend fun milestonesFor(key: String): List<MilestoneEntity>
}

@Dao
interface FocusDao {
    @Insert suspend fun insert(s: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY epochMillis DESC LIMIT 50")
    fun recent(): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(minutes),0) FROM focus_sessions WHERE completed = 1 AND epochMillis >= :since")
    fun minutesSince(since: Long): Flow<Int>

    // Lifetime completed blocks — the hero's DEX. Counted in SQL rather than over [recent]'s
    // 50-row window, which silently capped DEX at 50 and let it DROP as newer incomplete
    // sessions pushed completed ones out of the window.
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completed = 1")
    fun completedCount(): Flow<Int>

    /** Lifetime completed focus minutes. Focus history is never purged, so this only grows. */
    @Query("SELECT COALESCE(SUM(minutes),0) FROM focus_sessions WHERE completed = 1")
    fun lifetimeMinutes(): Flow<Int>

    /**
     * Raw timestamps of completed blocks since [since], for the consistency grid. Returns millis
     * rather than a day number so the caller can bucket them in the LOCAL time zone — SQLite's
     * date functions are UTC and would mis-file anything near midnight.
     */
    @Query("SELECT epochMillis FROM focus_sessions WHERE completed = 1 AND epochMillis >= :since")
    fun completedMillisSince(since: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completed = 1")
    suspend fun completedCountNow(): Int

    /** Attribute an already-logged block to a quest (post-block, from the finish prompt). */
    @Query("UPDATE focus_sessions SET taskId = :taskId WHERE id = :sessionId")
    suspend fun attributeToTask(sessionId: Long, taskId: Long)

    /**
     * Total completed focus minutes per quest, for the estimate-vs-actual comparison. Not windowed
     * by [recent]'s 50-row limit — a long quest can easily span more sessions than that.
     */
    @Query(
        "SELECT taskId AS taskId, COALESCE(SUM(minutes),0) AS minutes FROM focus_sessions " +
            "WHERE completed = 1 AND taskId IS NOT NULL GROUP BY taskId"
    )
    fun minutesPerTask(): Flow<List<TaskMinutes>>
}

/** Row shape for [FocusDao.minutesPerTask]: actual focused minutes against one quest. */
data class TaskMinutes(val taskId: Long, val minutes: Int)

@Dao
interface LootDao {
    @Query("SELECT * FROM loot_items ORDER BY acquiredAtMillis DESC")
    fun all(): Flow<List<LootEntity>>

    @Insert suspend fun insert(l: LootEntity): Long

    @Query("SELECT COUNT(*) FROM loot_items")
    suspend fun count(): Int

    @Query("SELECT * FROM loot_items")
    suspend fun allNow(): List<LootEntity>

    @Query("UPDATE loot_items SET equipped = :equipped WHERE id = :id")
    suspend fun setEquipped(id: Long, equipped: Boolean)

    @Query("SELECT * FROM loot_items WHERE id = :id")
    suspend fun getById(id: Long): LootEntity?

    @Query("DELETE FROM loot_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM owned_items")
    fun owned(): Flow<List<OwnedItemEntity>>

    @Query("SELECT * FROM owned_items")
    suspend fun ownedNow(): List<OwnedItemEntity>

    @Query("SELECT * FROM owned_items WHERE itemId = :itemId")
    suspend fun get(itemId: String): OwnedItemEntity?

    @Upsert suspend fun upsert(item: OwnedItemEntity)

    @Query("DELETE FROM owned_items WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    // Unequip every artifact in a slot (single-equip-per-slot enforcement before equipping a new one).
    @Query("UPDATE owned_items SET equipped = 0 WHERE itemId IN (:itemIds)")
    suspend fun unequipAll(itemIds: List<String>)
}

@Dao
interface StrongholdDao {
    @Query("SELECT * FROM stronghold WHERE id = 1")
    fun observe(): Flow<StrongholdEntity?>     // null when never unlocked -> VM maps to locked default

    @Query("SELECT * FROM stronghold WHERE id = 1")
    suspend fun get(): StrongholdEntity?

    @Upsert suspend fun upsert(s: StrongholdEntity)
}
