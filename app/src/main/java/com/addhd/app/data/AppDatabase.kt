package com.addhd.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        MoodEntity::class,
        JournalEntity::class,
        DopamineEntity::class,
        PetEntity::class,
        FocusSessionEntity::class,
        LootEntity::class,
        CampaignEntity::class,
        MilestoneEntity::class,
        OwnedItemEntity::class,
        StrongholdEntity::class,
    ],
    version = 18,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun moodDao(): MoodDao
    abstract fun journalDao(): JournalDao
    abstract fun dopamineDao(): DopamineDao
    abstract fun petDao(): PetDao
    abstract fun focusDao(): FocusDao
    abstract fun lootDao(): LootDao
    abstract fun campaignDao(): CampaignDao
    abstract fun shopDao(): ShopDao
    abstract fun strongholdDao(): StrongholdDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 -> v2: add the clean-streak start column without wiping existing data.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet ADD COLUMN cleanStreakStartEpochDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v2 -> v3: D&D pivot. Add hero class column + create the loot table. Schema preserved.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet ADD COLUMN heroClass TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS loot_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "defId TEXT NOT NULL, " +
                        "acquiredAtMillis INTEGER NOT NULL, " +
                        "equipped INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // v3 -> v4: habits can opt in to appearing as a derived daily task. Schema preserved.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN showAsTask INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v4 -> v5: long-term D&D campaigns imported from the game engine. Two new content
        // tables; existing data untouched. Rows are seeded from assets/campaigns.json on first
        // open after this migration (see seedCampaignsIfEmpty in the ViewModel).
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS campaigns (" +
                        "campaignKey TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "icon TEXT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "setting TEXT NOT NULL, " +
                        "level TEXT NOT NULL, " +
                        "sort INTEGER NOT NULL, " +
                        "active INTEGER NOT NULL, " +
                        "completed INTEGER NOT NULL, " +
                        "PRIMARY KEY(campaignKey))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS campaign_milestones (" +
                        "campaignKey TEXT NOT NULL, " +
                        "`order` INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "objective TEXT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "tasksRequired INTEGER NOT NULL, " +
                        "tasksDone INTEGER NOT NULL, " +
                        "done INTEGER NOT NULL, " +
                        "PRIMARY KEY(campaignKey, `order`))"
                )
            }
        }

        // v5 -> v6: fidget dice daily-capped XP counters on the single pet row.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet ADD COLUMN fidgetXpDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pet ADD COLUMN fidgetXpCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v6 -> v7: Quest Archive & Purge — task completion date + lifetime completed counter.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedAtEpochDay INTEGER")
                db.execSQL("ALTER TABLE pet ADD COLUMN totalQuestsCompleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v7 -> v8: one-time backfill of legacy completed tasks that predate v7 and have a NULL
        // completion date. Stamp them with TODAY so they stay hidden, keep counting toward the
        // lifetime score, and cleanly auto-purge in 30 days instead of lingering forever.
        // `today` is computed in Kotlin (LocalDate.now().toEpochDay()) to exactly match the app's
        // own day math — avoids SQLite's UTC strftime being off by a day near midnight.
        private fun migration7to8(today: Long) = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE tasks SET completedAtEpochDay = ? WHERE done = 1 AND completedAtEpochDay IS NULL",
                    arrayOf<Any>(today),
                )
            }
        }

        // v8 -> v9: manual quest-time locks — a flag marking a task's block as user-pinned.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN blockPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v9 -> v10: New Game Plus / World Tier. Persist the loop counter on the single pet row.
        // Defaults to 1 (base game) for existing saves so nothing is retroactively scaled.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet ADD COLUMN worldTier INTEGER NOT NULL DEFAULT 1")
            }
        }

        // v10 -> v11: Gold Economy & Adventurer's Shop. Add the gold balance to the pet row and
        // create the owned-items table (shop inventory). Existing saves start with 0 gold and an
        // empty bag; item CONTENT lives in code (ShopCatalog) so future stock needs no migration.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet ADD COLUMN gold INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS owned_items (" +
                        "itemId TEXT NOT NULL, " +
                        "qty INTEGER NOT NULL DEFAULT 1, " +
                        "equipped INTEGER NOT NULL DEFAULT 0, " +
                        "acquiredAtMillis INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(itemId))"
                )
            }
        }

        // v11 -> v12: Hero's Tavern (Stronghold) Phase 1. Single-row state table for the base-tab
        // unlock + Garden Plot. Not seeded — absence of the row is the locked default; the row is
        // lazily created on first purchase. Future rooms are additive ALTERs on this table.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS stronghold (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "unlocked INTEGER NOT NULL DEFAULT 0, " +
                        "unlockedAtMillis INTEGER NOT NULL DEFAULT 0, " +
                        "gardenLevel INTEGER NOT NULL DEFAULT 0, " +
                        "gardenLastHarvestMillis INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // v12 -> v13: Hero's Tavern Phase 2 — Trophy Room + Coin Vault. Seven additive columns on
        // the single stronghold row. All NOT NULL DEFAULT 0 so existing saves (and the lazily-
        // created row) read as locked / unpurchased / never-claimed with zero backfill. Purely
        // additive — no data risk, and no touching owned_items or the item catalog (the Chrono-
        // Anchor is a self-contained Trophy Room perk, not an equippable artifact).
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- Trophy Room (endgame storefront) ---
                db.execSQL("ALTER TABLE stronghold ADD COLUMN trophyRoomLevel INTEGER NOT NULL DEFAULT 0")        // 0 = locked, 1 = unlocked
                db.execSQL("ALTER TABLE stronghold ADD COLUMN ascensionPurchased INTEGER NOT NULL DEFAULT 0")     // 0/1 — Ascended Class Evolution owned
                db.execSQL("ALTER TABLE stronghold ADD COLUMN chronoAnchorPurchased INTEGER NOT NULL DEFAULT 0")  // 0/1 — Chrono-Anchor owned
                // --- Chrono-Anchor charges (self-contained; drift-free monthly reset) ---
                db.execSQL("ALTER TABLE stronghold ADD COLUMN chronoCharges INTEGER NOT NULL DEFAULT 0")          // Streak-Freeze charges available now
                db.execSQL("ALTER TABLE stronghold ADD COLUMN chronoChargesResetMillis INTEGER NOT NULL DEFAULT 0") // anchor for the monthly recharge clock
                // --- Coin Vault (passive gold engine) ---
                db.execSQL("ALTER TABLE stronghold ADD COLUMN vaultLevel INTEGER NOT NULL DEFAULT 0")             // 0 = locked, 1 = base, 2 = upgraded
                db.execSQL("ALTER TABLE stronghold ADD COLUMN vaultLastClaimMillis INTEGER NOT NULL DEFAULT 0")   // drift-free stipend clock anchor
            }
        }

        // v13 -> v14: Typed Boss Encounters. Three additive columns on campaign_milestones so a
        // chapter's boss can demand a specific loop (focus sessions / streak / timed tasks) instead
        // of raw task count. All NOT NULL DEFAULT so every existing milestone reads as STANDARD —
        // original behavior, untouched. The retro-UPDATEs then paint typed bosses onto the
        // already-seeded content (seedCampaignsIfEmpty is one-shot and won't re-run on populated
        // installs), so existing saves get a diverse playground without a factory reset.
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE campaign_milestones ADD COLUMN bossType TEXT NOT NULL DEFAULT 'STANDARD'")
                db.execSQL("ALTER TABLE campaign_milestones ADD COLUMN bossProgress INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE campaign_milestones ADD COLUMN bossAnchorMillis INTEGER NOT NULL DEFAULT 0")

                // Retro-assign a diverse spread onto the recommended-start campaign so populated
                // installs immediately have all three boss types to exercise. New/reset installs
                // pick these up from campaigns.json instead. Chapters keyed by (campaignKey, order).
                val key = "keep_on_borderlands"
                fun assign(order: Int, type: String) = db.execSQL(
                    "UPDATE campaign_milestones SET bossType = ? WHERE campaignKey = ? AND `order` = ?",
                    arrayOf<Any>(type, key, order),
                )
                assign(3, "SHADOW_SPECTER")   // Ch 3: Outer Caves — timed (time-blocked) tasks
                assign(4, "IRON_BEHEMOTH")    // Ch 4: Inner Caves — focus sessions
                assign(6, "HYDRA")            // Ch 6 (final): the warlord — sustained streak
            }
        }

        // v14 -> v15: Task breakdown / micro-steps. One nullable column on tasks so any task can
        // become a parent and its steps link back via parentId. Nullable (no NOT NULL) so every
        // existing task defaults to a top-level quest (parentId = NULL) with zero data risk.
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN parentId INTEGER DEFAULT NULL")
            }
        }

        // v15 -> v16: Daily Tavern Boon. One additive column tracking the epochDay the +10-gold
        // completion reward was last granted, so it's claimable once per day across all three
        // Tavern Games. DEFAULT 0 → existing saves can claim immediately today. Zero data risk.
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet ADD COLUMN tavernRewardDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * True when [table] already has [column]. A migration that re-adds an existing column
         * throws SQLiteException, and because there's no destructive fallback that failure
         * repeats on EVERY launch — a permanently unopenable save. Guarding makes the step
         * re-runnable, which matters whenever a column landed but the version bump didn't (two
         * DB opens racing the upgrade, or a process killed mid-migration).
         */
        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) if (c.getString(nameIdx) == column) return true
                false
            }

        // v16 -> v17: time-estimation feedback. One nullable column linking a focus session to the
        // quest it was spent on, so actual minutes can be compared with the quest's estimate.
        // Nullable with no default — every existing session stays (correctly) unattributed, and
        // attribution is always optional. Purely additive; no data risk.
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "focus_sessions", "taskId")) {
                    db.execSQL("ALTER TABLE focus_sessions ADD COLUMN taskId INTEGER DEFAULT NULL")
                }
            }
        }

        /**
         * v17 -> v18: the app stops forgetting you.
         *
         * Two monotonic columns on the single pet row. [PetEntity.totalBigQuestsCompleted] replaces
         * the live COUNT that backed STR — because completed quests purge after 30 days, that count
         * (and therefore the hero's Strength) decreased over time. [PetEntity.firstOpenEpochDay]
         * anchors the all-time view.
         *
         * Both are backfilled from surviving history so an existing save doesn't read as brand new:
         * STR from the tasks still on disk, and the start date from the oldest row of any kind.
         * Guarded per column so a half-applied upgrade can be re-run instead of bricking the save.
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "pet", "totalBigQuestsCompleted")) {
                    db.execSQL("ALTER TABLE pet ADD COLUMN totalBigQuestsCompleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL(
                        "UPDATE pet SET totalBigQuestsCompleted = " +
                            "(SELECT COUNT(*) FROM tasks WHERE done = 1 AND energy >= 2)"
                    )
                }
                if (!hasColumn(db, "pet", "firstOpenEpochDay")) {
                    db.execSQL("ALTER TABLE pet ADD COLUMN firstOpenEpochDay INTEGER NOT NULL DEFAULT 0")
                    // Oldest timestamp across the three long-lived tables -> epoch day. 0 if the
                    // save is empty, in which case the app stamps today on next launch.
                    db.execSQL(
                        "UPDATE pet SET firstOpenEpochDay = COALESCE((SELECT MIN(d) FROM (" +
                            "SELECT MIN(epochMillis) / 86400000 AS d FROM focus_sessions " +
                            "UNION ALL SELECT MIN(epochMillis) / 86400000 FROM mood_logs " +
                            "UNION ALL SELECT MIN(createdAt) / 86400000 FROM tasks" +
                            ") WHERE d IS NOT NULL), 0)"
                    )
                }
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val today = java.time.LocalDate.now().toEpochDay()
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focuspet.db",
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, migration7to8(today), MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                ).build().also { INSTANCE = it }
            }
    }
}
