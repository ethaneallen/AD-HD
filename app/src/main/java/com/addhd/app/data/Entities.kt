package com.addhd.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All persisted models. Dates are stored as epochDay (LocalDate.toEpochDay) or
 * epochMillis where a timestamp is needed, to keep Room simple (no type converters
 * for java.time required beyond Longs).
 */

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val done: Boolean = false,
    val energy: Int = 2,            // 1 = tiny, 2 = medium, 3 = big — for low-pressure sorting
    val estimateMin: Int = 25,      // used by auto-scheduler
    val dueEpochDay: Long? = null,
    // Time-block assignment (null = unscheduled). Minutes-from-midnight start.
    val blockDayEpochDay: Long? = null,
    val blockStartMin: Int? = null,
    // True when the user manually locked this quest to blockStartMin. The auto-scheduler pins
    // these in place and sequences flexible quests around them.
    val blockPinned: Boolean = false,
    val createdAt: Long = 0,
    val sort: Int = 0,
    // epochDay the task was completed (null while open). Drives the midnight clean-slate
    // (Done list shows only today) and the 30-day archive purge.
    val completedAtEpochDay: Long? = null,
    // Sub-task / breakdown link: null = a top-level quest; non-null = a micro-step whose parent
    // is the task with this id. Sub-steps render nested under their parent and are excluded from
    // scheduling, the Task Jar, and campaign-gate credit (so they can't cheese the boss gate).
    val parentId: Long? = null,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📜",
    val schedule: String = "DAILY",   // DAILY or comma day-of-week list "1,2,3" (1=Mon)
    val createdAt: Long = 0,
    val archived: Boolean = false,
    // DORMANT (retained for schema parity only): once let a habit also surface on the Tasks
    // board. Habits are now strictly decoupled from the Quest Log and live only on the Habits
    // (Rituals) screen, so nothing sets this true anymore. Kept as an additive-only column to
    // avoid a destructive migration; safe to drop in a future schema recreate.
    val showAsTask: Boolean = false,
)

/** One row per habit per completed day. */
@Entity(tableName = "habit_checks", primaryKeys = ["habitId", "epochDay"])
data class HabitCheckEntity(
    val habitId: Long,
    val epochDay: Long,
)

@Entity(tableName = "mood_logs")
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMillis: Long,
    val mood: Int,            // 1..5
    val energy: Int,          // 1..5
    val note: String = "",
)

@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMillis: Long,
    val prompt: String = "",
    val body: String,
)

/** Dopamine SOS check-ins: logged when the user rides an urge or does a healthy boost. */
@Entity(tableName = "dopamine_logs")
data class DopamineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMillis: Long,
    val level: Int,           // 1..5 self-rated before
    val trigger: String = "", // what the urge was (scroll, snack, etc.)
    val action: String = "",  // what they chose to do instead
    val rodeItOut: Boolean = false,
)

/**
 * Single-row hero/character state (id is always 1). Table is still named "pet" and the
 * plant-era columns are reused verbatim — we map them to D&D concepts in the domain layer
 * rather than migrating data destructively:
 *   name  -> the Hero's name        xp/level -> character XP & level (same thresholds)
 *   streakDays -> (unchanged)       cleanStreakStartEpochDay -> (unchanged)
 */
@Entity(tableName = "pet")
data class PetEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "The Hero",
    val xp: Int = 0,
    val level: Int = 1,
    val lastFedEpochDay: Long = 0,
    val streakDays: Int = 0,
    val cleanStreakStartEpochDay: Long = 0,
    // Chosen D&D class: "" = not yet chosen (show the class picker), else FIGHTER/ROGUE/WIZARD.
    val heroClass: String = "",
    // Fidget dice: daily-capped critical-hit XP. fidgetXpDay is the epochDay the count applies
    // to; fidgetXpCount resets to 0 when a roll happens on a new day. Caps exploit-free XP.
    val fidgetXpDay: Long = 0,
    val fidgetXpCount: Int = 0,
    // Daily Tavern Boon: the epochDay the once-per-day +10-gold completion reward was last granted.
    // Shared across all three Tavern Games so the boon can only pay out once per calendar day.
    val tavernRewardDay: Long = 0,
    /**
     * Lifetime count of completed medium/big quests — the hero's STR.
     *
     * Previously STR was counted live off the tasks table, which meant it SHRANK: completed tasks
     * are purged after [com.addhd.app.vm.AppViewModel.ARCHIVE_RETENTION_DAYS] days, so the stat
     * silently walked backwards as history aged out. A monotonic counter can only ever grow.
     */
    val totalBigQuestsCompleted: Int = 0,
    /**
     * The first day this save saw use, for the all-time "since …" readout. Backfilled from the
     * oldest surviving row on upgrade, and stamped on first launch for a new save.
     */
    val firstOpenEpochDay: Long = 0,
    /**
     * Momentum — the hero's XP multiplier, and the one number in the app with real stakes.
     *
     * Earned (+1 per day you do something, capped) and LOST (−2 per idle day, floored at 0). It
     * replaced a multiplier derived from the clean-streak date, which grew purely by the calendar
     * advancing: it couldn't be earned, couldn't be lost, and stopped changing after day 15 while
     * the badge kept counting. This one moves both ways and you can watch it fall.
     */
    val momentum: Int = 0,
    /** The last day momentum was credited — decay is measured from here. */
    val momentumDay: Long = 0,
    /**
     * Rooms cleared, as a real counter: ONE PER COMPLETED QUEST.
     *
     * Previously derived as `lifetimeXP / 20`, which meant "Room 7/10" and the XP bar were the
     * same quantity drawn twice — progress that looked earned but was just the XP bar relabelled.
     * Now a room is a thing you did.
     */
    val roomsCleared: Int = 0,
    /**
     * Remaining HP of the boss standing in room 10, and the quest it belongs to.
     *
     * The boss used to die to a single task completion: `isBossRoom` was checked, loot dropped,
     * done. Meanwhile STR fed `bossDamage` and WIS fed a crit chance that were computed, printed
     * in a sentence, and applied to nothing — there was no HP for the damage to reduce. These two
     * columns are what make the stat block real.
     */
    val bossHpRemaining: Int = 0,
    val bossQuestIndex: Int = -1,
    // Permanent lifetime high score. Only ever increments — bumped when archived tasks are
    // purged so the tally survives the 30-day deletion. The task text is trashed; this isn't.
    val totalQuestsCompleted: Int = 0,
    // New Game Plus / World Tier. 1 = base game (no scaling). Increments by 1 each time the
    // player clears the FINAL campaign and the rotation wraps back to the first. Every tier
    // above 1 scales boss task-gates up and task/campaign XP rewards proportionally.
    val worldTier: Int = 1,
    // Adventurer's Shop currency. Earned alongside XP on productivity/boss awards (NOT fidget
    // dice — that path stays goldless to match the World Tier anti-farm rule). Spent in the shop.
    val gold: Int = 0,
)

/**
 * Loot dropped by defeating dungeon bosses. The only genuinely new persisted state for the
 * D&D pivot — drops are random so they must be stored. `defId` points at a definition in the
 * code-side loot table (LootTable.kt), keeping item content easy to extend without migrations.
 */
@Entity(tableName = "loot_items")
data class LootEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val defId: String,
    val acquiredAtMillis: Long,
    val equipped: Boolean = false,
)

/**
 * A shop item the player OWNS. Distinct from LootEntity (boss gear): shop items are bought with
 * gold and defined in the code-side ShopCatalog (ShopItem.kt), so only the `itemId` is persisted
 * — no migration needed to add new shop content later.
 *
 * `itemId` is the primary key: one row per distinct owned item.
 *   - CONSUMABLES stack in `qty` (buying more increments it; using one decrements it; a row at
 *     qty 0 is deleted so "owned" == row-exists-with-qty>0).
 *   - ARTIFACTS are unique (qty always 1) and use `equipped` to occupy a passive-buff slot.
 * Kept deliberately separate from the loot paperdoll so economy/meta buffs never touch the
 * combat-stat (STR/DEX/CON/WIS) gear model.
 */
@Entity(tableName = "owned_items")
data class OwnedItemEntity(
    @PrimaryKey val itemId: String,
    val qty: Int = 1,
    val equipped: Boolean = false,
    val acquiredAtMillis: Long = 0,
)

/**
 * The Hero's Tavern (Stronghold) — a single-row state table (id always 1), mirroring PetEntity.
 * Phase 1 tracks only the base-tab unlock and the Garden Plot. Absence of the row IS the default
 * (locked, no garden) — no seeding; the row is lazily created on first purchase. Future rooms
 * (Coin Vault, War Table, …) are additive ALTER TABLE columns on this table.
 *
 * gardenLastHarvestMillis is the brew-clock anchor: pending potions are DERIVED from elapsed time
 * (now - anchor) / interval, never stored, so the count can't drift. Only the anchor persists.
 */
@Entity(tableName = "stronghold")
data class StrongholdEntity(
    @PrimaryKey val id: Int = 1,
    val unlocked: Boolean = false,            // bought the Hero's Tavern (base tab unlock)
    val unlockedAtMillis: Long = 0,
    val gardenLevel: Int = 0,                 // 0 = not built, 1 = built (>1 reserved for upgrade tiers)
    val gardenLastHarvestMillis: Long = 0,    // last harvest/build time; brew clock counts from here
    // ---- Phase 2 (v13): Trophy Room — endgame storefront ----
    val trophyRoomLevel: Int = 0,             // 0 = locked, 1 = unlocked
    val ascensionPurchased: Boolean = false,  // Ascended Class Evolution owned (mechanics land in a later step)
    val chronoAnchorPurchased: Boolean = false, // Chrono-Anchor owned
    val chronoCharges: Int = 0,               // Streak-Freeze charges available now
    val chronoChargesResetMillis: Long = 0,   // anchor for the monthly recharge clock (drift-free)
    // ---- Phase 2 (v13): Coin Vault — passive gold engine ----
    val vaultLevel: Int = 0,                  // 0 = locked, 1 = base, 2 = upgraded
    val vaultLastClaimMillis: Long = 0,       // last stipend claim; claim clock counts from here (mirrors garden)
    // ---- Phase 3 (v20): drains that can never run out ----
    /**
     * SUPERSEDED (v20 -> v21). These backed "a night at the inn", which paid 600 gold to restore 2
     * Momentum — i.e. +2% XP. The arithmetic never worked: the gold cost ~1,200 raw XP of work to
     * earn, while two active days restored the same 2 points for free, so nothing would ever have
     * been bought and nothing drained. Replaced by the bard's commission below, which sells a
     * bonus you feel on the next completion instead of insurance against a trivial loss.
     *
     * Kept rather than dropped because removing a SQLite column means recreating the table, and
     * minSdk 26 predates ALTER TABLE DROP COLUMN. They are written by nothing.
     */
    val innNightsUsed: Int = 0,
    val innPeriodStartMillis: Long = 0,
    /** Daily cap bookkeeping for the gambling table (mirrors the fidget XP cap pattern). */
    val diceBetsDay: Long = 0,
    val diceBetsCount: Int = 0,
    // ---- Phase 3 (v21): the bard's commission ----
    /**
     * Commissions bought this period and when the period began — the escalating price makes this a
     * sink that can't be farmed, and (unlike the inn) can't be completed either.
     */
    val bardCommissionsUsed: Int = 0,
    val bardPeriodStartMillis: Long = 0,
    /** While now() is below this, every XP award is multiplied. 0 = no bard playing. */
    val bardExpiresAtMillis: Long = 0,
)

/**
 * A long-term campaign imported from the D&D game engine (see assets/campaigns.json).
 * `active` marks the one campaign currently pinned to the dashboard. `completed` flips
 * true when its final milestone is done.
 */
@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey val campaignKey: String,
    val name: String,
    val icon: String,
    val description: String,
    val setting: String = "",
    val level: String = "",
    val sort: Int = 0,               // display order (import order)
    val active: Boolean = false,
    val completed: Boolean = false,
)

/**
 * One milestone (chapter) within a campaign. `tasksRequired` real task-completions advance
 * it; `tasksDone` tracks progress toward that. `done` flips when tasksDone >= tasksRequired.
 */
@Entity(tableName = "campaign_milestones", primaryKeys = ["campaignKey", "order"])
data class MilestoneEntity(
    val campaignKey: String,
    val order: Int,
    val title: String,
    val objective: String,
    val description: String,
    val tasksRequired: Int = 5,
    val tasksDone: Int = 0,
    val done: Boolean = false,
    // ---- v14: Typed Boss Encounters ----
    val bossType: String = "STANDARD",   // BossType name; mapped via BossType.fromContent at use site
    val bossProgress: Int = 0,           // typed-demand counter for the current encounter
    val bossAnchorMillis: Long = 0,      // when the boss became "alive" — focus/streak/timed counts start here
)

/** Focus (Pomodoro) session history. */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMillis: Long,
    val minutes: Int,
    val completed: Boolean,
    /**
     * Which quest this block was spent on, attributed AFTER the fact from the finish prompt.
     * Null means unattributed, which is the normal case and carries no penalty — it's the raw
     * focus minutes that count toward XP and the daily total. This exists purely so actual time
     * can be compared against the quest's [TaskEntity.estimateMin].
     */
    val taskId: Long? = null,
)
