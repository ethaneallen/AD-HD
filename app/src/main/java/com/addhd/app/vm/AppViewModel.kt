package com.addhd.app.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.addhd.app.campaign.BossType
import com.addhd.app.campaign.Campaign
import com.addhd.app.campaign.HeroClass
import com.addhd.app.campaign.HeroStats
import com.addhd.app.campaign.LootTable
import com.addhd.app.coach.Coach
import com.addhd.app.coach.CoachResult
import com.addhd.app.coach.CoachSnapshot
import com.addhd.app.data.AppDatabase
import com.addhd.app.data.CampaignEntity
import com.addhd.app.data.DopamineEntity
import com.addhd.app.data.FocusSessionEntity
import com.addhd.app.data.HabitCheckEntity
import com.addhd.app.data.HabitEntity
import com.addhd.app.data.JournalEntity
import com.addhd.app.data.LootEntity
import com.addhd.app.data.OwnedItemEntity
import com.addhd.app.data.MilestoneEntity
import com.addhd.app.data.MoodEntity
import com.addhd.app.data.PetEntity
import com.addhd.app.data.StrongholdEntity
import com.addhd.app.data.TaskEntity
import com.addhd.app.util.Scheduler
import com.addhd.app.util.StreakCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val taskDao = db.taskDao()
    private val habitDao = db.habitDao()
    private val moodDao = db.moodDao()
    private val journalDao = db.journalDao()
    private val dopamineDao = db.dopamineDao()
    private val petDao = db.petDao()
    private val focusDao = db.focusDao()
    private val lootDao = db.lootDao()
    private val campaignDao = db.campaignDao()
    private val shopDao = db.shopDao()
    private val strongholdDao = db.strongholdDao()

    private val appContext = app.applicationContext

    // The shared, process-wide reward pipeline. The SAME instance the home-screen widget drives, so
    // an in-app check-off and a widget check-off run one loop, one combo, one set of locks. The VM
    // delegates its completion/reward logic here and re-exposes the engine's flows unchanged below.
    private val engine = RewardEngine.get(appContext)

    // Sound on/off is a device UI preference (not hero state) → SharedPreferences, synchronous.
    private val prefs = appContext.getSharedPreferences("tavern_prefs", Context.MODE_PRIVATE)
    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled
    fun setSoundEnabled(on: Boolean) {
        prefs.edit().putBoolean("sound_enabled", on).apply()
        _soundEnabled.value = on
        com.addhd.app.util.Sound.enabled = on
    }

    // Reduce motion: freezes the app's perpetual animations (bobbing hero, breathing focus
    // familiar, pulsing shield, rotating Sanctuary sigil). Defaults to whatever the OS already
    // says — if animations are off system-wide, don't make the user ask twice.
    private val _reduceMotion = MutableStateFlow(
        prefs.getBoolean(KEY_REDUCE_MOTION, com.addhd.app.ui.theme.systemAnimationsDisabled(appContext)),
    )
    val reduceMotion: StateFlow<Boolean> = _reduceMotion
    fun setReduceMotion(on: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, on).apply()
        _reduceMotion.value = on
    }

    // --- Notification preferences (same prefs store; keys shared with Notifier so receivers/workers
    // read the identical values with no ViewModel in scope). Master + three per-type toggles, all
    // default ON. Flipping the master or a type re-syncs the recurring workers so a disabled nudge
    // stops firing immediately.
    private fun notifPref(key: String) = MutableStateFlow(prefs.getBoolean(key, true))
    private val _notifMaster = notifPref(com.addhd.app.util.notify.Notifier.KEY_MASTER)
    private val _notifTimeBlock = notifPref(com.addhd.app.util.notify.Notifier.KEY_TIME_BLOCK)
    private val _notifStreak = notifPref(com.addhd.app.util.notify.Notifier.KEY_STREAK)
    private val _notifMorning = notifPref(com.addhd.app.util.notify.Notifier.KEY_MORNING)
    val notifMaster: StateFlow<Boolean> = _notifMaster
    val notifTimeBlock: StateFlow<Boolean> = _notifTimeBlock
    val notifStreak: StateFlow<Boolean> = _notifStreak
    val notifMorning: StateFlow<Boolean> = _notifMorning

    private fun setNotifPref(key: String, flow: MutableStateFlow<Boolean>, on: Boolean) {
        prefs.edit().putBoolean(key, on).apply()
        flow.value = on
        // Re-arm the daily workers so a freshly-enabled type starts firing and a disabled one is
        // simply gated out at post time (workers still run, but Notifier suppresses the post).
        com.addhd.app.util.notify.ReminderScheduler.ensureDailyWork(appContext)
        // Turning the master (or time-block) back on should re-arm alarms that were never scheduled
        // while off; turning off leaves alarms armed but Notifier gates the actual post.
        if (on) viewModelScope.launch {
            com.addhd.app.util.notify.ReminderScheduler.rescheduleAllTimeBlocks(appContext)
        }
    }
    fun setNotifMaster(on: Boolean) = setNotifPref(com.addhd.app.util.notify.Notifier.KEY_MASTER, _notifMaster, on)
    fun setNotifTimeBlock(on: Boolean) = setNotifPref(com.addhd.app.util.notify.Notifier.KEY_TIME_BLOCK, _notifTimeBlock, on)
    fun setNotifStreak(on: Boolean) = setNotifPref(com.addhd.app.util.notify.Notifier.KEY_STREAK, _notifStreak, on)
    fun setNotifMorning(on: Boolean) = setNotifPref(com.addhd.app.util.notify.Notifier.KEY_MORNING, _notifMorning, on)

    /** Re-usable hook: (re)schedule one task's time-block alarm if it has a block, else cancel it. */
    private fun syncTimeBlockAlarm(task: TaskEntity, taskId: Long = task.id) {
        val day = task.blockDayEpochDay
        val start = task.blockStartMin
        if (!task.done && day != null && start != null) {
            com.addhd.app.util.notify.ReminderScheduler.scheduleTimeBlock(appContext, taskId, task.title, day, start)
        } else {
            com.addhd.app.util.notify.ReminderScheduler.cancelTimeBlock(appContext, taskId)
        }
    }

    private fun today() = LocalDate.now().toEpochDay()
    private fun now() = System.currentTimeMillis()
    private fun startOfTodayMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // Focus-timer state (behaviour lives in the "Focus timer" section near the bottom of the file).
    // Declared HERE, above init, on purpose: property initializers run in declaration order, and
    // init calls restoreFocusTimer() which writes this flow. viewModelScope can't defer that —
    // it dispatches Main.immediate, so on the main thread it runs inline during construction.
    private val _focusTimer = MutableStateFlow(FocusTimer())
    val focusTimer: StateFlow<FocusTimer> = _focusTimer
    private var focusTicker: kotlinx.coroutines.Job? = null

    // Same reason: restoreFocusTimer() can credit a block during construction, and logFocus()
    // writes _lastFocusSessionId from a Dispatchers.IO coroutine that runs in PARALLEL with the
    // remaining property initializers. Declared here so it is never read before it exists.
    /** The just-finished block, awaiting optional attribution. Null once attributed or cleared. */
    private val _lastFocusSessionId = MutableStateFlow<Long?>(null)
    /** Title of the quest the last block was attributed to — drives the confirmation line. */
    private val _focusAttributedTo = MutableStateFlow<String?>(null)

    /**
     * The quest the hero has explicitly committed to working on, set by "Pick it back up" on Home.
     * Only the id is held (and persisted): the live row is resolved through [focusTarget] so the
     * target disappears by itself once the quest is completed or deleted.
     */
    private val _focusTargetId = MutableStateFlow(
        prefs.getLong(KEY_FOCUS_TARGET_ID, 0L).takeIf { it > 0L },
    )

    /**
     * Which quest the hero has already said "still on it" about, and on which day. The reminder
     * card is a PROMPT, not a permanent banner: once answered it should go quiet, but it has to
     * come back the next day, because "what was I doing?" is exactly the question a new day asks.
     */
    private data class ResumeAck(val taskId: Long, val day: Long)
    private val _resumeAck = MutableStateFlow(
        prefs.getLong(KEY_RESUME_ACK_ID, 0L).takeIf { it > 0L }
            ?.let { ResumeAck(it, prefs.getLong(KEY_RESUME_ACK_DAY, 0L)) },
    )

    init {
        // Preload all SFX into the low-latency SoundPool, then apply the persisted mute preference.
        com.addhd.app.util.Sound.init(appContext)
        com.addhd.app.util.Sound.enabled = _soundEnabled.value
        // Ensure a pet row exists, and start the clean streak clock on first ever launch.
        viewModelScope.launch {
            val p = petDao.get()
            if (p == null) {
                petDao.upsert(PetEntity(cleanStreakStartEpochDay = today()))
            } else if (p.cleanStreakStartEpochDay == 0L) {
                petDao.upsert(p.copy(cleanStreakStartEpochDay = today()))
            }
        }
        // Notifications: create channels, register the daily morning/evening nudges, and re-arm any
        // upcoming time-block alarms (they don't survive process death). Cheap + idempotent.
        com.addhd.app.util.notify.Notifier.ensureChannels(appContext)
        com.addhd.app.util.notify.ReminderScheduler.ensureDailyWork(appContext)
        viewModelScope.launch {
            com.addhd.app.util.notify.ReminderScheduler.rescheduleAllTimeBlocks(appContext)
        }
        // Import the D&D campaigns from assets on first launch after the v5 migration.
        viewModelScope.launch(Dispatchers.IO) { seedCampaignsIfEmpty() }
        // Quest Archive purge: on every launch, credit then delete completed tasks older than
        // the 30-day retention window so the DB stays light and the list stays fast.
        viewModelScope.launch(Dispatchers.IO) { purgeArchivedTasks() }
        // NOTE: anything that touches a StateFlow declared further down the class body must NOT
        // go here — see the trailing init block at the bottom of this class for why.
    }

    /**
     * Delete completed tasks older than [ARCHIVE_RETENTION_DAYS], but FIRST bump the permanent
     * lifetime counter by exactly how many are about to be purged — so the high score survives
     * the deletion. Runs off the main thread; a no-op when nothing has aged out.
     */
    private suspend fun purgeArchivedTasks() {
        val cutoff = today() - ARCHIVE_RETENTION_DAYS
        val purgeable = taskDao.countCompletedBefore(cutoff)
        if (purgeable <= 0) return
        val p = petDao.get() ?: PetEntity()
        petDao.upsert(p.copy(totalQuestsCompleted = p.totalQuestsCompleted + purgeable))
        taskDao.purgeCompletedBefore(cutoff)
    }

    /**
     * One-time import of assets/campaigns.json into the campaign tables. No-op once seeded.
     * No campaign is activated at import — the dashboard shows a picker so the player chooses
     * which campaign to embark on (see [selectCampaign]).
     */
    private suspend fun seedCampaignsIfEmpty() {
        if (campaignDao.campaignCount() > 0) return
        val json = try {
            appContext.assets.open("campaigns.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return // no asset -> nothing to seed
        }
        val root = org.json.JSONObject(json)
        val arr = root.optJSONArray("campaigns") ?: return
        val campaigns = mutableListOf<CampaignEntity>()
        val milestones = mutableListOf<MilestoneEntity>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val key = c.getString("campaignKey")
            campaigns.add(
                CampaignEntity(
                    campaignKey = key,
                    name = c.getString("name"),
                    icon = c.optString("icon", "📜"),
                    description = c.optString("description", ""),
                    setting = c.optString("setting", ""),
                    level = c.optString("level", ""),
                    sort = i,
                    active = false,           // none active until the player picks one
                    completed = false,
                )
            )
            val ms = c.optJSONArray("milestones") ?: continue
            for (j in 0 until ms.length()) {
                val m = ms.getJSONObject(j)
                milestones.add(
                    MilestoneEntity(
                        campaignKey = key,
                        order = m.optInt("order", j),
                        title = m.getString("title"),
                        objective = m.optString("objective", ""),
                        description = m.optString("description", ""),
                        bossType = m.optString("bossType", "STANDARD"),
                    )
                )
            }
        }
        campaignDao.insertCampaigns(campaigns)
        campaignDao.insertMilestones(milestones)
    }

    // ---- Streams ----
    val tasks: StateFlow<List<TaskEntity>> =
        taskDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Open top-level tasks ranked lowest-effort-first (energy dominates, estimate breaks ties) —
     * the single source of truth for "what's the easiest thing to start right now." Backs the Home
     * Quick-Strike sheet.
     *
     * No longer feeds the Focus screen: "Just Start" used to label itself with the top entry, but a
     * focus session has no task reference (focus_sessions carries no taskId and completing a block
     * doesn't touch a quest), so naming one implied a link that didn't exist.
     */
    val quickStrikeTasks: StateFlow<List<TaskEntity>> =
        tasks.map { list ->
            list.filter { !it.done && it.parentId == null }
                .sortedBy { it.energy * 100 + it.estimateMin }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habits: StateFlow<List<HabitEntity>> =
        habitDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitChecks: StateFlow<List<HabitCheckEntity>> =
        habitDao.allChecks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Single source of truth for "habits (rituals) checked off today" — the count of live habits
     * whose id has a check row dated today. Counting habits (not check rows) naturally excludes
     * orphaned checks and can't exceed the habit total, so no clamp is needed. Consumed by the
     * Coach and any UI; replaces the ad-hoc inline computation the nav layer used to do.
     */
    val habitsDoneToday: StateFlow<Int> =
        combine(habits, habitChecks) { habitsList, checks ->
            val checkedToday = checks.filter { it.epochDay == today() }.map { it.habitId }.toSet()
            habitsList.count { it.id in checkedToday }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ---- Campaigns (long-term D&D arcs) ----
    val campaigns: StateFlow<List<CampaignEntity>> =
        campaignDao.campaigns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Current World Tier (NG+ counter) for the Character sheet and gate/reward scaling. 1 = base. */
    val worldTier: StateFlow<Int> =
        petDao.observe().map { it?.worldTier ?: 1 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /**
     * Reactive total GATE_REDUCTION from equipped shop artifacts (whole tasks to shave off the
     * boss gate). Derived straight from the owned-items table so equipping/unequipping the Tome
     * of Shortcuts re-emits activeQuest instantly. Declared BEFORE activeQuest so it's initialized
     * when the combine below captures it. This is the reactive twin of the suspend gateReductionNow()
     * used by the write paths — both read the same equipped artifacts, so UI and engine agree.
     */
    val gateReduction: StateFlow<Int> =
        shopDao.owned().map { owned ->
            val equippedIds = owned.filter { it.equipped }.map { it.itemId }
            Math.round(
                com.addhd.app.campaign.ShopCatalog.buffTotal(
                    equippedIds, com.addhd.app.campaign.BuffKind.GATE_REDUCTION,
                )
            ).toInt()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * The active campaign + its current (first not-done) milestone, projected for the dashboard.
     * Null once every campaign is complete or before seeding finishes.
     */
    val activeQuest: StateFlow<ActiveQuest?> =
        combine(
            campaignDao.activeCampaign(), campaignDao.milestones(), worldTier, gateReduction,
            petDao.observe(),
        ) { camp, allMs, tier, reduction, pet ->
            if (camp == null) return@combine null
            val mine = allMs.filter { it.campaignKey == camp.campaignKey }.sortedBy { it.order }
            val current = mine.firstOrNull { !it.done }
            val doneCount = mine.count { it.done }
            val td = current?.tasksDone ?: 0
            // Effective gate = World Tier scaling MINUS equipped GATE_REDUCTION artifacts, floored
            // at 1. Same function the write paths call, so the displayed gate can't drift from the
            // enforced one.
            val tr = current?.let {
                com.addhd.app.campaign.WorldTier.effectiveGate(it.tasksRequired, tier, reduction)
            } ?: 0
            val ready = current != null && tr > 0 && td >= tr
            // Boss is truly killable only when the TYPED demand is also met. Same branches as the
            // guard in defeatBoss(); if these drift the button will no-op again (the defeat loop).
            val vulnerable = ready && current != null && when (
                com.addhd.app.campaign.BossType.fromContent(current.bossType)
            ) {
                com.addhd.app.campaign.BossType.STANDARD -> true
                com.addhd.app.campaign.BossType.IRON_BEHEMOTH ->
                    current.bossProgress >= com.addhd.app.campaign.BossType.IRON_BEHEMOTH_SESSIONS
                com.addhd.app.campaign.BossType.SHADOW_SPECTER ->
                    current.bossProgress >= com.addhd.app.campaign.BossType.SHADOW_SPECTER_TIMED
                com.addhd.app.campaign.BossType.HYDRA -> {
                    val anchorDay = if (current.bossAnchorMillis <= 0L) Long.MAX_VALUE else
                        java.time.Instant.ofEpochMilli(current.bossAnchorMillis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                    val streakStart = pet?.cleanStreakStartEpochDay ?: 0L
                    streakStart in 1..anchorDay
                }
            }
            ActiveQuest(
                campaignName = camp.name,
                campaignIcon = camp.icon,
                milestoneTitle = current?.title ?: "Campaign complete!",
                objective = current?.objective ?: "",
                tasksDone = td,
                tasksRequired = tr,
                milestoneIndex = doneCount,
                milestoneCount = mine.size,
                campaignDone = camp.completed,
                // Boss gate: chapter fully charged but not yet cleared -> the boss is waiting.
                bossReady = ready,
                bossType = current?.bossType ?: "STANDARD",
                bossProgress = current?.bossProgress ?: 0,
                bossAnchorMillis = current?.bossAnchorMillis ?: 0,
                bossVulnerable = vulnerable,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val moods: StateFlow<List<MoodEntity>> =
        moodDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // ================================================================================================
    //  Mana matching — the Status screen's energy reading decides which quests get offered.
    // ================================================================================================
    // TaskEntity.energy (1..3 effort) and MoodEntity.energy (1..5 reserves) were the same idea and
    // never met: the mood log's five-point scale was consumed as a single bit (<=2 -> suggest the
    // Sanctuary) and otherwise discarded. Now a low reading caps the effort of what the app puts in
    // front of you, so "I'm depleted" produces a shorter list instead of just a diary entry.

    /** Today's most recent mana reading (1..5), or null if nothing has been logged today. */
    val manaToday: StateFlow<Int?> = moods.map { list ->
        val startOfDay = startOfTodayMillis()
        list.firstOrNull { it.epochMillis >= startOfDay }?.energy
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Highest task effort worth offering right now, derived from [manaToday]. Null means "no
     * reading today, don't filter anything" — absence of data must never hide quests.
     */
    val effortCeiling: StateFlow<Int?> = manaToday.map { energyToEffortCeiling(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Open quests at or below [effortCeiling], lowest-effort first. Falls back to the unfiltered
     * ranking when there's no reading, and — deliberately — when the ceiling would leave you with
     * nothing: an empty list would read as "no quests", which is worse than an honest one.
     */
    val manaMatchedTasks: StateFlow<List<TaskEntity>> =
        combine(quickStrikeTasks, effortCeiling) { ranked, ceiling ->
            if (ceiling == null) ranked
            else ranked.filter { it.energy <= ceiling }.ifEmpty { ranked }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val journal: StateFlow<List<JournalEntity>> =
        journalDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dopamineLogs: StateFlow<List<DopamineEntity>> =
        dopamineDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pet: StateFlow<PetEntity?> =
        petDao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---- Hero's Tavern (Stronghold) ----
    /** Projected Tavern state for the UI. `pendingGardenPotions` is DERIVED (never stored). */
    data class StrongholdUiState(
        val isUnlocked: Boolean = false,
        val gardenLevel: Int = 0,
        val pendingGardenPotions: Int = 0,
        // Millis until the next potion finishes brewing (0 when a potion is ready or garden unbuilt);
        // lets the UI show a countdown without recomputing the delta itself.
        val nextPotionInMillis: Long = 0,
        // ---- Phase 2: Trophy Room ----
        val trophyRoomLevel: Int = 0,
        val ascensionPurchased: Boolean = false,
        val chronoAnchorPurchased: Boolean = false,
        val chronoCharges: Int = 0,
        // ---- Phase 2: Coin Vault ----
        val vaultLevel: Int = 0,
        // Millis until the next stipend is claimable (0 when a claim is ready or vault unbuilt);
        // computed drift-free from vaultLastClaimMillis so the UI just formats it.
        val nextStipendInMillis: Long = 0,
    )

    /**
     * Ticker so time-derived state (pending potions) re-emits while the screen is open. A pure
     * map on strongholdDao.observe() would freeze — the DB row doesn't change as time passes, so
     * the count would only update after a build/harvest. Emits every minute (plus once immediately).
     */
    private val minuteTicker = flow {
        while (true) { emit(Unit); kotlinx.coroutines.delay(60_000) }
    }

    val strongholdUiState: StateFlow<StrongholdUiState> =
        combine(strongholdDao.observe(), minuteTicker) { s, _ ->
            if (s == null || !s.unlocked) return@combine StrongholdUiState(isUnlocked = false)

            // Garden (Phase 1): pending potions + brew countdown, derived from elapsed time.
            var pending = 0
            var nextPotionIn = 0L
            if (s.gardenLevel > 0) {
                val elapsed = (now() - s.gardenLastHarvestMillis).coerceAtLeast(0)
                pending = (elapsed / GARDEN_BREW_INTERVAL_MILLIS).toInt().coerceAtMost(GARDEN_MAX_PENDING)
                // Time until the NEXT incoming potion finishes brewing. Always the remainder of the
                // current chunk while below cap — independent of how many are already stored (a 2/3
                // garden is still counting toward its 3rd). 0 only when capped (UI shows "Storage Full").
                nextPotionIn = if (pending >= GARDEN_MAX_PENDING) 0L
                    else GARDEN_BREW_INTERVAL_MILLIS - (elapsed % GARDEN_BREW_INTERVAL_MILLIS)
            }

            // Coin Vault (Phase 2): time until the next stipend is claimable. Drift-free — the
            // remainder of the current 24h chunk measured from the last-claim anchor. 0 when a claim
            // is ready (a full interval elapsed) or the vault isn't built.
            val nextStipendIn = if (s.vaultLevel <= 0) 0L else {
                val elapsed = (now() - s.vaultLastClaimMillis).coerceAtLeast(0)
                if (elapsed >= VAULT_CLAIM_INTERVAL_MILLIS) 0L
                else VAULT_CLAIM_INTERVAL_MILLIS - (elapsed % VAULT_CLAIM_INTERVAL_MILLIS)
            }

            StrongholdUiState(
                isUnlocked = true,
                gardenLevel = s.gardenLevel,
                pendingGardenPotions = pending,
                nextPotionInMillis = nextPotionIn,
                trophyRoomLevel = s.trophyRoomLevel,
                ascensionPurchased = s.ascensionPurchased,
                chronoAnchorPurchased = s.chronoAnchorPurchased,
                chronoCharges = s.chronoCharges,
                vaultLevel = s.vaultLevel,
                nextStipendInMillis = nextStipendIn,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StrongholdUiState())

    /**
     * Lifetime quests completed for the Character sheet: the permanent purged tally PLUS the
     * completed tasks still in the archive (not yet purged). Accurate at all times, not just
     * after a purge fires. Declared after [pet]/[tasks] since it combines them.
     */
    val lifetimeQuestsCompleted: StateFlow<Int> = combine(pet, tasks) { p, taskList ->
        (p?.totalQuestsCompleted ?: 0) + taskList.count { it.done }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val focusSessions: StateFlow<List<FocusSessionEntity>> =
        focusDao.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Lifetime completed focus blocks — counted in SQL so it isn't capped by [focusSessions]' window. */
    val focusBlocksCompleted: StateFlow<Int> =
        focusDao.completedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loot: StateFlow<List<LootEntity>> =
        lootDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Gold & Shop ----
    /** Live gold balance for the shop header and affordability checks. */
    val gold: StateFlow<Int> =
        petDao.observe().map { it?.gold ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Owned shop items (consumable stacks + equipped/unequipped artifacts). */
    val ownedItems: StateFlow<List<OwnedItemEntity>> =
        shopDao.owned().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The four D&D stats, derived live from existing tables (no stored columns):
     * STR = medium/big tasks done, DEX = focus blocks completed,
     * CON = clean-streak days, WIS = urges ridden in SOS.
     */
    val heroStats: StateFlow<HeroStats> = combine(
        tasks, focusBlocksCompleted, dopamineLogs, pet,
    ) { _, dex, dopa, petState ->
        // STR reads the lifetime counter rather than counting the tasks table, which used to make
        // it DECREASE as completed quests aged out of the 30-day archive.
        val str = petState?.totalBigQuestsCompleted ?: 0
        val con = cleanDays(petState?.cleanStreakStartEpochDay ?: 0L, today())
        val wis = dopa.count { it.rodeItOut }
        HeroStats(str = str, dex = dex, con = con, wis = wis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeroStats(0, 0, 0, 0))

    /**
     * Hero stats WITH equipped-gear bonuses folded in — this is what combat reads, so the
     * single equipped weapon actually raises your to-hit. The character sheet shows the same
     * base+gear split; here we just sum them for the boss-fight attack modifier.
     */
    val combatStats: StateFlow<HeroStats> = combine(heroStats, loot) { base, lootList ->
        var str = 0; var dex = 0; var con = 0; var wis = 0
        lootList.filter { it.equipped }.forEach { li ->
            LootTable.def(li.defId)?.let { d ->
                when (d.stat) {
                    com.addhd.app.campaign.LootStat.STR -> str += d.bonus
                    com.addhd.app.campaign.LootStat.DEX -> dex += d.bonus
                    com.addhd.app.campaign.LootStat.CON -> con += d.bonus
                    com.addhd.app.campaign.LootStat.WIS -> wis += d.bonus
                }
            }
        }
        HeroStats(base.str + str, base.dex + dex, base.con + con, base.wis + wis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeroStats(0, 0, 0, 0))

    /** One-shot combat events for the Home overlay (task done -> combat log, boss drops). */
    data class CombatEvent(
        val line: String,
        val crit: Boolean,
        val bossDefeated: Boolean = false,
        val lootName: String? = null,
        val lootEmoji: String? = null,
    )

    /** The pinned campaign quest shown on the dashboard (see [activeQuest]). */
    data class ActiveQuest(
        val campaignName: String,
        val campaignIcon: String,
        val milestoneTitle: String,
        val objective: String,
        val tasksDone: Int,
        val tasksRequired: Int,
        val milestoneIndex: Int,   // how many milestones already done
        val milestoneCount: Int,
        val campaignDone: Boolean,
        val bossReady: Boolean = false,  // chapter charged, boss alive, awaiting explicit defeat
        // ---- v14: Typed Boss Encounters (display) ----
        val bossType: String = "STANDARD",
        val bossProgress: Int = 0,
        val bossAnchorMillis: Long = 0,
        // True only when defeatBoss() would actually succeed — i.e. the typed vulnerability demand
        // is ALSO satisfied, not just the task gate. Mirrors the guard in defeatBoss() so the Home
        // "Defeat the Boss" button can't be tapped into a silent no-op (the defeat loop).
        val bossVulnerable: Boolean = false,
    )


    // Combat events now originate in the shared engine (both app + widget wins feed it). Re-exposed
    // here so every existing screen observer (`vm.combat`) keeps working with no change.
    val combat: SharedFlow<CombatEvent> = engine.combat

    /**
     * The explicit reward from a single "Invoke a Blessing" tap. Unlike combat loot (a transient
     * global overlay that a fast tap can miss), this rides a replay=1 flow so the Sanctuary's
     * DoneCard can always read exactly what the last blessing granted and state it plainly.
     * Every blessing yields SOMETHING — either gear or a named gold/XP boon — never an empty result.
     */
    data class BlessingReward(
        val kind: Kind,
        val xp: Int,
        val gold: Int = 0,
        val lootName: String? = null,
        val lootEmoji: String? = null,
        val rarity: String? = null,   // display tier for GEAR (e.g. "Rare"); null for boons
    ) {
        enum class Kind { GEAR, GOLD, XP_BOON }
    }

    private val _blessing = MutableSharedFlow<BlessingReward>(replay = 1, extraBufferCapacity = 1)
    val blessing: SharedFlow<BlessingReward> = _blessing

    /** Fired once when the campaign rotation wraps and the World Tier increases (see [defeatBoss]). */
    data class WorldTierEvent(val newTier: Int)

    val worldTierUp: SharedFlow<WorldTierEvent> = engine.worldTierUp

    /** Coach output derived from live state. */
    val coach: StateFlow<CoachResult> = combine(
        tasks, habits, habitChecks, moods, combine(dopamineLogs, pet, focusSessions) { d, p, f -> Triple(d, p, f) }
    ) { tasksList, habitsList, checks, moodsList, triple ->
        val (dopa, petState, focus) = triple
        val t = today()
        // Completion DATE, not due date. Keying off dueEpochDay counted a quest finished today but
        // due tomorrow as "not done", while separately counting EVERY undated inbox capture ever
        // completed (they live for 30 days) as done today — so the coach's picture of the day was
        // both inflated and wrong. Matches what Home and Your Patterns already count. Top-level
        // quests only: micro-steps shouldn't pad either tally.
        val doneToday = tasksList.count { it.done && it.parentId == null && it.completedAtEpochDay == t }
        val open = tasksList.count { !it.done && it.parentId == null }
        val focusMin = focus.filter { it.completed && it.epochMillis >= startOfTodayMillis() }.sumOf { it.minutes }
        val checkedToday = checks.filter { it.epochDay == t }.map { it.habitId }.toSet()
        val habitsDone = habitsList.count { it.id in checkedToday }
        val lastMood = moodsList.firstOrNull()
        val ridesToday = dopa.count { it.rodeItOut && it.epochMillis >= startOfTodayMillis() }
        val snap = CoachSnapshot(
            hour = LocalDateTime.now().hour,
            tasksDoneToday = doneToday,
            tasksOpen = open,
            focusMinutesToday = focusMin,
            habitsDueToday = habitsList.size,
            habitsDoneToday = habitsDone,
            lastMood = lastMood?.mood,
            lastEnergy = lastMood?.energy,
            petStreakDays = petState?.streakDays ?: 0,
            petLevel = petState?.level ?: 1,
            dopamineRidesToday = ridesToday,
            minutesSinceLastWin = null,
        )
        Coach.evaluate(snap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Coach.evaluate(emptySnapshot()))

    private fun emptySnapshot() = CoachSnapshot(
        hour = 9, tasksDoneToday = 0, tasksOpen = 0, focusMinutesToday = 0,
        habitsDueToday = 0, habitsDoneToday = 0, lastMood = null, lastEnergy = null,
        petStreakDays = 0, petLevel = 1, dopamineRidesToday = 0, minutesSinceLastWin = null,
    )

    // ---- Insights ("Your Patterns") ----
    // Reflective, non-judgmental metrics derived ENTIRELY from existing rows — no schema change,
    // no new DAO query. Aggregation runs off the main thread (combine collectors + a Default-
    // dispatched fold isn't needed here: the math is O(n) over ≤50 focus rows + the task list,
    // cheap enough for the flow's collector thread). Each metric self-gates on a minimum sample
    // so we never show a confident-but-hollow "peak" from two data points.

    /** One day in the 7-day Focus-vs-Mood series. [mood] is null on days with no logged mood. */
    data class DayPoint(val epochDay: Long, val label: String, val focusMin: Int, val mood: Int?)

    data class InsightsData(
        val peakWindow: String?,      // e.g. "10 AM–12 PM"; null until ≥3 completed focus sessions
        val strongestDay: String?,    // e.g. "Tuesday"; null until any task is completed
        val weeklyQuests: Int,        // top-level quests completed in the last 7 days
        val weeklyFocusMin: Int,      // completed focus minutes in the last 7 days
        val streakDays: Int,
        val series: List<DayPoint>,   // last 7 calendar days, oldest → newest
        val hasEnoughData: Boolean,   // gates the screen onto a friendly empty state
    )

    val insights: StateFlow<InsightsData> = combine(
        tasks, focusSessions, moods, pet,
    ) { taskList, focus, moodList, petState ->
        val zone = ZoneId.systemDefault()
        val todayDay = today()
        val weekStartDay = todayDay - 6            // inclusive 7-day window
        val doneFocus = focus.filter { it.completed }

        // Peak focus window: 2-hour buckets over completed-session start hours. Needs a real
        // sample (≥3) or it's noise. Uses focus millis — task completion is day-only (no hour).
        val peakWindow: String? = if (doneFocus.size >= 3) {
            val buckets = IntArray(12) // 12 two-hour buckets across the day
            doneFocus.forEach { s ->
                val hour = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(s.epochMillis), zone).hour
                buckets[hour / 2]++
            }
            val top = buckets.indices.maxByOrNull { buckets[it] } ?: -1
            if (top >= 0 && buckets[top] > 0) hourLabel(top * 2) + "–" + hourLabel(top * 2 + 2) else null
        } else null

        // Strongest day: weekday with the most completed top-level quests, all-time.
        val completedTop = taskList.filter { it.done && it.parentId == null && it.completedAtEpochDay != null }
        val strongestDay: String? = completedTop
            .groupingBy { LocalDate.ofEpochDay(it.completedAtEpochDay!!).dayOfWeek }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

        // Weekly rollups.
        val weeklyQuests = completedTop.count { it.completedAtEpochDay!! >= weekStartDay }
        val weeklyFocusMin = doneFocus
            .filter { it.epochMillis >= LocalDate.ofEpochDay(weekStartDay).atStartOfDay(zone).toInstant().toEpochMilli() }
            .sumOf { it.minutes }

        // 7-day paired series (oldest → newest). Focus summed per day; mood = latest logged that day.
        val series = (0..6).map { offset ->
            val day = weekStartDay + offset
            val dayStart = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = dayStart + 86_400_000L
            val fMin = doneFocus.filter { it.epochMillis in dayStart until dayEnd }.sumOf { it.minutes }
            val dayMood = moodList
                .filter { it.epochMillis in dayStart until dayEnd }
                .maxByOrNull { it.epochMillis }?.mood
            DayPoint(
                epochDay = day,
                label = LocalDate.ofEpochDay(day).dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                focusMin = fMin,
                mood = dayMood,
            )
        }

        InsightsData(
            peakWindow = peakWindow,
            strongestDay = strongestDay,
            weeklyQuests = weeklyQuests,
            weeklyFocusMin = weeklyFocusMin,
            streakDays = petState?.streakDays ?: 0,
            series = series,
            // Something to reflect on: any focus history, any completed quest, or any mood logged.
            hasEnoughData = doneFocus.isNotEmpty() || completedTop.isNotEmpty() || moodList.isNotEmpty(),
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        InsightsData(null, null, 0, 0, 0, emptyList(), false),
    )

    /** 24h hour → compact 12h label ("10 AM", "12 PM", "2 PM"). */
    private fun hourLabel(h24: Int): String {
        val h = ((h24 + 24) % 24)
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
        return "$h12 $ampm"
    }

    // ---- Pet XP + campaign locks ----
    // The XP/gold and campaign-milestone serialization locks now live in the shared [engine] (so a
    // widget-driven award and an in-app award can't race). The VM's remaining read-modify-write
    // sites on those same rows — fidget/tavern XP, boss defeat/downed/select — take the engine's
    // locks via engine.withXpLock { } / engine.withCampaignLock { }.

    // Serializes shop purchases so two rapid buys can't both read the same gold balance and
    // overspend (spend more than the player has). Also guards the buy → deduct → grant sequence.
    private val shopMutex = Mutex()

    // Serializes Tavern operations (unlock/build/harvest) so a gold deduction and a state write
    // can't race — same discipline as shopMutex.
    private val strongholdMutex = Mutex()

    // Serializes loot sell-backs so two rapid sells can't both read the same gold balance and
    // lose one credit — same discipline as shopMutex.
    private val lootMutex = Mutex()

    // ---- Shop artifact buffs ----
    // Sum the magnitude of every EQUIPPED artifact of a given buff kind. Reads owned_items live
    // so equipping/unequipping takes effect on the next award with no cached state.
    private suspend fun equippedBuff(kind: com.addhd.app.campaign.BuffKind): Double {
        val equippedIds = shopDao.ownedNow().filter { it.equipped }.map { it.itemId }
        return com.addhd.app.campaign.ShopCatalog.buffTotal(equippedIds, kind)
    }


    // Potion of Focus + combo streak now live in the shared [engine]; re-exposed here so existing
    // UI observers (`vm.xpPotionPrimed`, `vm.comboCount`) keep working unchanged.
    val xpPotionPrimed: StateFlow<Boolean> = engine.xpPotionPrimed
    val comboCount: StateFlow<Int> = engine.comboCount

    /**
     * Drink a Potion of Focus: consume one from inventory and prime the next XP award to double.
     * No-op (no priming) if none are owned or one is already primed. The priming + "Focus primed"
     * toast live in [RewardEngine.primeFocusPotion]; here we only own the inventory consume, and
     * un-prime if the consume somehow failed after priming (defensive — should never happen).
     */
    fun usePotionOfFocus() = viewModelScope.launch(Dispatchers.IO) {
        if (xpPotionPrimed.value) return@launch       // already primed — don't waste a second potion
        if (consumeItem("potion_focus")) engine.primeFocusPotion()
    }

    /** Reset the combo — call when the idle window has clearly lapsed (e.g. app resume after a break). */
    fun coolCombo() = engine.coolCombo()

    // XP/gold award now lives in the shared [engine] (same lock, same combo, same level-up fanfare).
    // Thin fire-and-forget wrapper so the ~8 existing call sites (fidget, blessing, focus, scroll,
    // boss-clear) stay untouched.
    private fun awardXp(amount: Int, bonusGold: Int = 0, comboMult: Double = 1.0) = viewModelScope.launch {
        engine.awardXp(amount, bonusGold, comboMult)
    }

    // ---- Fidget dice ----
    private val _fidgetRolls = MutableSharedFlow<FidgetRoll>(extraBufferCapacity = 8)
    /** Emits each completed roll (value + any XP awarded) for the FidgetScreen to react to. */
    val fidgetRolls: SharedFlow<FidgetRoll> = _fidgetRolls

    data class FidgetRoll(
        val value: Int,          // 1..20
        val crit: Boolean,       // >= CRIT_THRESHOLD
        val xpAwarded: Int,      // 0 if free / capped out
        val critXpLeftToday: Int // remaining capped crit-XP awards after this roll
    )

    // Result of a lockpick/popper completion — did it pay out, and how many caps remain today.
    data class FidgetReward(val xpAwarded: Int, val critXpLeftToday: Int)

    private val _fidgetRewards = MutableSharedFlow<FidgetReward>(extraBufferCapacity = 8)
    /** Emits when the Lockpick or Rune Popper is solved (shares the dice daily XP cap). */
    val fidgetRewards: SharedFlow<FidgetReward> = _fidgetRewards

    /**
     * Award the shared fidget XP for completing Lockpick or Rune Popper. Uses the SAME daily
     * cap as the dice crit (fidgetXpDay/fidgetXpCount), so all three games together can only
     * pay out [FIDGET_CRIT_XP_CAP] times a day. Flat, un-multiplied, no streak bump.
     */
    fun awardFidgetGameXp() = viewModelScope.launch {
        engine.withXpLock {
            val p = petDao.get() ?: PetEntity()
            val t = today()
            val countToday = if (p.fidgetXpDay == t) p.fidgetXpCount else 0
            val underCap = countToday < FIDGET_CRIT_XP_CAP
            var awarded = 0
            var newCount = countToday
            if (underCap) { awarded = FIDGET_CRIT_XP; newCount = countToday + 1 }

            if (awarded > 0) {
                var xp = p.xp + awarded
                var level = p.level
                while (xp >= level * 100) { xp -= level * 100; level++ }
                petDao.upsert(p.copy(xp = xp, level = level, fidgetXpDay = t, fidgetXpCount = newCount))
            } else if (p.fidgetXpDay != t) {
                petDao.upsert(p.copy(fidgetXpDay = t, fidgetXpCount = 0))
            }
            _fidgetRewards.tryEmit(
                FidgetReward(awarded, (FIDGET_CRIT_XP_CAP - newCount).coerceAtLeast(0))
            )
        }
        claimTavernDailyReward()
    }

    /**
     * Daily Tavern Boon: the FIRST Tavern Game completion (lockpick pop, rune board clear, or the
     * day's first dice crit) grants a flat [TAVERN_DAILY_GOLD]. Guarded by [PetEntity.tavernRewardDay]
     * so it pays out exactly once per calendar day — no farming. Uses its own [xpMutex] section so
     * the read-modify-write of the gold + day marker is atomic against concurrent completions.
     */
    fun claimTavernDailyReward() = viewModelScope.launch {
        val granted = engine.withXpLock {
            val p = petDao.get() ?: PetEntity()
            val t = today()
            if (p.tavernRewardDay == t) return@withXpLock false   // already claimed today — anti-farm
            petDao.upsert(p.copy(gold = p.gold + TAVERN_DAILY_GOLD, tavernRewardDay = t))
            true
        }
        // Toast fires outside the lock, and only when the boon actually paid out.
        if (granted) engine.emitConsumed("🍺 Tavern boon: +$TAVERN_DAILY_GOLD gold — first game of the day!")
    }

    /**
     * Roll the d20. Always free and unlimited (pure fidget). The FIRST [FIDGET_CRIT_XP_CAP]
     * critical hits (>= [FIDGET_CRIT_THRESHOLD]) each day award a flat, un-multiplied
     * [FIDGET_CRIT_XP]. The VM generates the value so the cap can't be bypassed, and the XP
     * is a fixed trickle (no streak multiplier, no streak bump) so it can't replace real work.
     */
    fun rollFidgetDice() = viewModelScope.launch {
        engine.withXpLock {
            val p = petDao.get() ?: PetEntity()
            val t = today()
            val value = kotlin.random.Random.nextInt(1, 21) // 1..20
            val crit = value >= FIDGET_CRIT_THRESHOLD

            // Reset the daily counter if this is the first roll on a new day.
            val countToday = if (p.fidgetXpDay == t) p.fidgetXpCount else 0
            val underCap = countToday < FIDGET_CRIT_XP_CAP

            var awarded = 0
            var newCount = countToday
            if (crit && underCap) {
                awarded = FIDGET_CRIT_XP
                newCount = countToday + 1
            }

            if (awarded > 0) {
                // Flat award: no multiplier, and DON'T touch lastFedEpochDay/streak — fidget
                // XP must not feed the clean streak or masquerade as real productivity.
                var xp = p.xp + awarded
                var level = p.level
                while (xp >= level * 100) { xp -= level * 100; level++ }
                petDao.upsert(p.copy(xp = xp, level = level, fidgetXpDay = t, fidgetXpCount = newCount))
            } else if (crit && p.fidgetXpDay != t) {
                // First roll of a new day that didn't award (cap is 0? no) — still roll over the day
                // marker so the counter is anchored to today.
                petDao.upsert(p.copy(fidgetXpDay = t, fidgetXpCount = 0))
            }

            _fidgetRolls.tryEmit(
                FidgetRoll(
                    value = value,
                    crit = crit,
                    xpAwarded = awarded,
                    critXpLeftToday = (FIDGET_CRIT_XP_CAP - newCount).coerceAtLeast(0),
                )
            )
        }
    }

    fun renamePet(name: String) = viewModelScope.launch {
        val p = petDao.get() ?: PetEntity()
        petDao.upsert(p.copy(name = name.ifBlank { "The Hero" }))
    }

    /** Set the hero's D&D class (shown once, on first launch). */
    fun chooseClass(hero: HeroClass, name: String) = viewModelScope.launch {
        val p = petDao.get() ?: PetEntity()
        petDao.upsert(p.copy(heroClass = hero.id, name = name.ifBlank { "The Hero" }))
    }

    /** Emits true when an Elixir of Willpower absorbed a relapse reset (streak saved), false on a
     *  normal reset. Lets the Streak screen show "your streak held" feedback. */
    private val _streakResetOutcome = MutableSharedFlow<Boolean>(extraBufferCapacity = 2)
    val streakResetOutcome: SharedFlow<Boolean> = _streakResetOutcome

    /**
     * No-shame relapse reset. If an Elixir of Willpower is owned, it's consumed to ABSORB this
     * reset — the clean streak is preserved instead of zeroed (a real loss-aversion buffer). With
     * no Elixir, the clock restarts at today as before. Progress (XP/level) is untouched either way.
     */
    fun resetCleanStreak() = viewModelScope.launch(Dispatchers.IO) {
        val saved = consumeItem("elixir_willpower")   // true if an Elixir was held & consumed
        if (!saved) {
            val p = petDao.get() ?: PetEntity()
            petDao.upsert(p.copy(cleanStreakStartEpochDay = today()))
        } else {
            // A streak guard (Elixir / Chrono-Anchor Streak-Freeze) absorbed the relapse.
            com.addhd.app.util.Sound.play(appContext, com.addhd.app.util.Sfx.SFX_STREAK_GUARD)
        }
        _streakResetOutcome.tryEmit(saved)
    }

    /**
     * Nuclear "start fresh" — wipes every table (tasks, habits, mood, journal, dopamine
     * logs, focus history, pet/XP/level/streak) and re-seeds a brand-new Level 1 pet with
     * the clean streak starting today. Used by Settings > Reset All Data. Irreversible.
     */
    fun resetAllData() = viewModelScope.launch {
        // clearAllTables() is a blocking DB call — must run off the main thread or Room
        // throws "Cannot access database on the main thread" and kills the process.
        withContext(Dispatchers.IO) {
            // Cancel any armed time-block alarms before their tasks are wiped (ids gone after clear).
            taskDao.scheduledBlocksFrom(0L).forEach {
                com.addhd.app.util.notify.ReminderScheduler.cancelTimeBlock(appContext, it.id)
            }
            db.clearAllTables()
            petDao.upsert(PetEntity(cleanStreakStartEpochDay = today()))
            // clearAllTables also wiped the imported campaigns — re-import them so the
            // dashboard quest returns immediately rather than after the next app launch.
            seedCampaignsIfEmpty()
        }
    }

    companion object {
        // Fidget dice tuning: crit on a natural 15+, +5 XP each, only the first 3 crits/day pay out.
        const val FIDGET_CRIT_THRESHOLD = 15
        const val FIDGET_CRIT_XP = 5
        const val FIDGET_CRIT_XP_CAP = 3
        // Daily Tavern Boon: flat gold granted the first time ANY Tavern Game is completed each day.
        const val TAVERN_DAILY_GOLD = 10

        // Boss fight: roll the d20 vs the boss's Armor Class; land >= AC to fell it. A cleared
        // chapter grants this flat XP bonus on top of the daily-task XP already earned.
        const val BOSS_AC = 10
        // Sized to exactly one room on the XP-derived odometer (20 XP/room, see
        // Campaign.roomsClearedTotal) so a boss clear advances the Current Quest room by 1
        // instead of lurching several rooms from a big XP dump.
        const val BOSS_CLEAR_XP = 20

        // Chance (%) that completing an ORDINARY task drops a piece of gear. The dungeon-boss
        // (room 10) win always drops regardless of this. Tunable — raise for more frequent loot.
        const val TASK_LOOT_DROP_CHANCE_PCT = 30

        // ---- Invoke-a-Blessing (Sanctuary) reward tiers ----
        // A blessing ALWAYS rewards. This is the split between a gear drop and a gold/XP boon; the
        // remaining (100 - gear)% is the boon, itself split ~evenly between gold and XP.
        const val BLESSING_GEAR_CHANCE_PCT = 60
        const val BLESSING_GOLD_BASE = 25   // flat gold purse (× World Tier) on a gold boon
        const val BLESSING_XP_BOON = 30     // bonus XP on top of the base on an XP boon

        /** Clean days elapsed since the streak start (>= 0). 0 means "Day 1" in the UI. */
        fun cleanDays(startEpochDay: Long, todayEpochDay: Long): Int =
            if (startEpochDay <= 0L) 0 else (todayEpochDay - startEpochDay).coerceAtLeast(0).toInt()

        /**
         * Streak XP multiplier: +1% per clean day, capped at +15% (1.15x).
         * Small but real — enough that the number is worth protecting.
         */
        fun streakMultiplier(cleanDays: Int): Float =
            1.0f + (cleanDays.coerceIn(0, 15) * 0.01f)

        /** Completed tasks are kept in the Quest Archive this many days, then purged. */
        const val ARCHIVE_RETENTION_DAYS = 30

        /** Accessibility: freeze the app's looping animations. */
        private const val KEY_REDUCE_MOTION = "reduce_motion"

        /** Last day the app was opened, for lapse detection. */
        private const val KEY_LAST_OPEN_DAY = "last_open_epoch_day"

        /** The quest explicitly committed to; survives process death like the timer anchor does. */
        private const val KEY_FOCUS_TARGET_ID = "focus_target_task_id"

        // "Still on it" acknowledgement — quest id + the day it was given.
        private const val KEY_RESUME_ACK_ID = "resume_ack_task_id"
        private const val KEY_RESUME_ACK_DAY = "resume_ack_day"

        /**
         * A gap of at least this many days triggers the welcome-back. Five days is past "busy
         * weekend" and into the territory where someone expects to be told off for it.
         */
        const val LAPSE_DAYS = 5

        /**
         * Mana reading (1..5) -> the largest task effort (1..3) worth offering.
         * Depleted asks for tiny only; a surge opens everything up.
         */
        fun energyToEffortCeiling(energy: Int?): Int? = when (energy) {
            null -> null        // nothing logged today — never filter on absent data
            1, 2 -> 1           // Depleted / Low  -> tiny quests only
            3 -> 2              // Steady          -> tiny + medium
            else -> 3           // Surge / Overcharged -> anything
        }

        /** Copy for the mana-matched card, keyed by the effort ceiling. */
        fun manaMatchLabel(ceiling: Int?): String = when (ceiling) {
            1 -> "Reserves are low — here's the small stuff"
            2 -> "Steady reserves — these are a fair match"
            3 -> "Reserves are high — take a real swing"
            else -> "Pick a quest"
        }

        // ---- Focus timer ----
        /** Duration the Focus screen opens on. */
        const val DEFAULT_FOCUS_MIN = 25
        // Wall-clock anchor for the running block, so it survives tab switches and process death.
        private const val KEY_FOCUS_END_AT = "focus_end_at"
        private const val KEY_FOCUS_MINUTES = "focus_minutes"

        // ---- Gold economy ----
        /** Gold earned per point of (raw, pre-multiplier) XP. 0.5 → a 20-XP task drops ~10 gold. */
        const val GOLD_PER_XP = 0.5
        /** Floor so even the tiniest win pays at least one coin. */
        const val MIN_GOLD_PER_AWARD = 1
        /** Potion of Focus: multiplier applied to the next XP award after drinking one. */
        const val POTION_XP_MULT = 2.0

        // ---- Hero's Tavern (Stronghold) ----
        const val TAVERN_UNLOCK_COST = 3000       // gold to unlock the base Tavern tab
        const val GARDEN_BUILD_COST = 1500        // gold to build the Garden Plot
        /** Garden brews 1 Potion of Focus every 3 days, capped at 3 stored offline. */
        const val GARDEN_BREW_INTERVAL_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val GARDEN_MAX_PENDING = 3

        // ---- Hero's Tavern Phase 2: Trophy Room (endgame storefront) ----
        const val TROPHY_ROOM_UNLOCK_COST = 2000  // gold to clear the rubble
        const val ASCENSION_COST = 3000           // Ascended Class Evolution (permanent)
        const val CHRONO_ANCHOR_COST = 8000       // Chrono-Anchor (legendary perk)
        /** Streak-Freeze charges the Chrono-Anchor grants, and how often they refill. */
        const val CHRONO_CHARGES_PER_PERIOD = 2
        const val CHRONO_RECHARGE_INTERVAL_MILLIS = 30L * 24 * 60 * 60 * 1000  // ~1 month

        // ---- Hero's Tavern Phase 2: Coin Vault (passive gold engine) ----
        const val VAULT_UNLOCK_COST = 2500        // gold to construct
        const val VAULT_UPGRADE_COST = 4000       // gold to go level 1 -> 2
        const val VAULT_CLAIM_INTERVAL_MILLIS = 24L * 60 * 60 * 1000  // stipend cadence
        const val VAULT_STIPEND_L1 = 15           // gold per claim at level 1
        const val VAULT_STIPEND_L2 = 30           // gold per claim at level 2

        // ---- The Sanctuary: Consult the Oracle ----
        /**
         * Hardcoded pool of short, dry/stoic wisdom lines the Oracle draws from. Kept in code (like
         * the loot/shop catalogs) — no persistence, so no schema or migration touch. Dry and a touch
         * sardonic by design: the Oracle snaps the hero back to focus, it doesn't coddle.
         */
        val ORACLE_WISDOM: List<String> = listOf(
            "You will not always feel like it. Do it anyway. That is the whole secret.",
            "The task you are avoiding is smaller than the dread you have built around it.",
            "Discipline is just remembering what you actually want.",
            "The scroll is bottomless. Your evening is not.",
            "You cannot think your way out of a hole you behave your way into. Move.",
            "Motivation is a guest that arrives after the work has started, never before.",
            "A blade left unswung rusts the same as one that loses. At least swing.",
            "The mountain does not care that you are tired. It is still the way home.",
            "You have survived every worst day so far. This one is not special.",
            "Comfort is a pleasant road that leads precisely nowhere.",
            "The urge will pass whether you feed it or not. Only one of those costs you.",
            "Small and steady buries grand and someday. Every time.",
            "You are not behind. You are simply not yet started. Fix the second thing.",
            "Rest is a weapon. Collapse is not. Know which one you are reaching for.",
            "The hardest lift is the first one off the ground. It is always the first one.",
            "Do the dull thing now, or the frantic version of it later. Choose your suffering.",
            "Nobody is coming to make this easy. That is not cruelty; it is just quiet.",
            "The hero is not the one who never falters. It is the one who returns to the path.",
            "Your future self is watching what you do in the next ten minutes.",
            "Focus is a muscle, and complaining is not the exercise.",
        )

        /** Draw one Oracle line at random. Pure — no state, safe to call from anywhere. */
        fun oracleWisdom(): String = ORACLE_WISDOM.random()
    }

    // ---- Tasks ----
    // startMin != null => the user locked a specific time; pin it to today's blocks immediately
    // so the auto-scheduler treats it as a fixed anchor.
    fun addTask(title: String, energy: Int, estimate: Int, startMin: Int? = null) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val id = taskDao.insert(
            TaskEntity(
                title = title.trim(), energy = energy, estimateMin = estimate,
                dueEpochDay = today(), createdAt = now(), sort = now().toInt(),
                blockDayEpochDay = if (startMin != null) today() else null,
                blockStartMin = startMin,
                blockPinned = startMin != null,
            )
        )
        // Arm a reminder if this quest was created already time-blocked.
        if (startMin != null) {
            com.addhd.app.util.notify.ReminderScheduler.scheduleTimeBlock(appContext, id, title.trim(), today(), startMin)
        }
    }

    /**
     * One-Tap Capture: create a task from a title ALONE, zero other input. Everything falls back to
     * the lowest-friction defaults — energy 1 (tiny), 5-min estimate, and a null due date so it
     * lands in the "inbox" to be triaged later rather than forcing a scheduling decision at capture
     * time. Voice input routes here too. Blank/whitespace titles are ignored.
     */
    fun quickCapture(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        taskDao.insert(
            TaskEntity(
                title = title.trim(),
                energy = 1,
                estimateMin = 5,
                dueEpochDay = null,           // inbox: unscheduled, no due-date pressure
                createdAt = now(),
                sort = now().toInt(),
            )
        )
        com.addhd.app.util.Haptics.runePop(appContext)   // tactile confirmation the thought was caught
    }

    /**
     * Triage an inbox item onto today's list with one tap: stamp today's due date so it leaves the
     * "Unscheduled" section and joins the active Open quests. Leaves it unblocked (no pinned time) —
     * the auto-scheduler or a manual time-lock can place it later. No-op if it's already due today.
     */
    fun scheduleForToday(task: TaskEntity) = viewModelScope.launch {
        if (task.dueEpochDay == today()) return@launch
        taskDao.update(task.copy(dueEpochDay = today()))
        com.addhd.app.util.Haptics.runePop(appContext)
    }

    fun toggleTask(task: TaskEntity) = viewModelScope.launch {
        if (task.done) {
            // Re-opening a completed quest: clear the completion day and re-arm its reminder (if
            // still future). No reward side effects on un-completing — that's the engine's job only
            // on the done transition, so this branch stays local to the VM.
            val reopened = task.copy(done = false, completedAtEpochDay = null)
            taskDao.update(reopened)
            syncTimeBlockAlarm(reopened)
            return@launch
        }
        // Completing a quest: hand the FULL reward cascade to the shared engine — the exact same
        // entry point the home-screen widget calls, so an in-app tap and a widget check-off run one
        // loop, one combo, one lock. The alarm-sync callback cancels the task's time-block reminder
        // as the completed row is written (engine has no dependency on the notification scheduler).
        engine.completeTask(task) { written -> syncTimeBlockAlarm(written) }
    }

    /**
     * Break a quest into a micro-step: insert a tiny child task linked to [parentId]. Steps default
     * to energy 1 / 5 min and inherit today's due date so they surface immediately. Rendered nested
     * under their parent; completing one awards normal XP but doesn't advance the campaign gate.
     */
    fun addSubtask(parentId: Long, title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        taskDao.insert(
            TaskEntity(
                title = title.trim(), energy = 1, estimateMin = 5,
                dueEpochDay = today(), createdAt = now(), sort = now().toInt(),
                parentId = parentId,
            )
        )
    }

    /**
     * Scroll of Teleportation: consume one Scroll to instantly clear an OPEN task you can't face.
     * Marks it done with the normal XP + gold award (via [awardXp]) but DELIBERATELY does NOT call
     * [creditCampaignProgress] — so scrolls can't be farmed to cheese the campaign boss gate. No-op
     * if the task is already done or no Scroll is owned. Emits a [PurchaseResult]-free path; the UI
     * observes the task list + gold to reflect the result.
     */
    fun useScrollOnTask(task: TaskEntity) = viewModelScope.launch {
        if (task.done) return@launch
        if (!consumeItem("scroll_teleport")) return@launch   // none owned -> no-op
        taskDao.update(task.copy(done = true, completedAtEpochDay = today()))
        com.addhd.app.util.Sound.play(appContext, com.addhd.app.util.Sfx.SFX_TASK_TOGGLE)
        com.addhd.app.util.Haptics.runePop(appContext)   // tactile pop — a Scroll-skip still counts as a win
        val baseXp = 15 + task.energy * 5
        emitCombatForWin(baseXp = baseXp, energy = task.energy)
        awardXp(baseXp)
        engine.emitConsumed("📜 Teleported past \"${task.title}\"")
        // NB: no creditCampaignProgress() — teleporting past a task must not advance the boss gate.
    }

    /**
     * A real task was completed -> advance the active campaign's current chapter by one tick,
     * BUT stop at the boss gate. Progress fills up to tasksRequired and then freezes there
     * (tasksDone == tasksRequired) with the milestone still NOT done — the boss is now "alive".
     * The chapter does NOT auto-advance; only [defeatBoss] can complete a milestone and move on.
     *
     * Crucially this never touches XP or loot: toggleTask already awarded those before calling
     * here, so extra chores while the boss lives still pay out normally — they just don't push
     * the campaign forward. Idempotent and a no-op when no campaign is active or the gate is full.
     */
    private fun creditCampaignProgress() = viewModelScope.launch(Dispatchers.IO) {
        engine.creditCampaignProgress()
    }

    /**
     * Typed-boss damage interceptor — delegates to the shared engine (same campaign lock as credit
     * and defeat). Shared by the Focus (IRON_BEHEMOTH) and time-blocked-task (SHADOW_SPECTER) paths.
     */
    private suspend fun damageTypedBoss(forType: com.addhd.app.campaign.BossType, cap: Int) =
        engine.damageTypedBoss(forType, cap)

    /**
     * Choose which campaign to embark on. Deactivates any currently-active (incomplete) campaign
     * and activates the picked one. Ignores completed campaigns and no-ops if it's already active.
     * Guarded by campaignMutex so it can't race with task-credit or a boss defeat.
     */
    fun selectCampaign(key: String) = viewModelScope.launch(Dispatchers.IO) {
        engine.withCampaignLock {
            val all = campaignDao.campaigns().first()
            val target = all.firstOrNull { it.campaignKey == key } ?: return@withCampaignLock
            if (target.completed || target.active) return@withCampaignLock
            // Clear any other active campaign first (single-active invariant).
            all.filter { it.active }.forEach { campaignDao.updateCampaign(it.copy(active = false)) }
            campaignDao.updateCampaign(target.copy(active = true))
        }
    }

    /**
     * The Boss Clearance Rule: the ONLY path that completes a chapter and advances the campaign.
     * Requires the current chapter to be fully "charged" (tasksDone >= tasksRequired) so the boss
     * is actually available. Marks the milestone done, resets its counter (defensive), and either
     * moves to the next chapter or, if that was the last, completes the campaign and activates the
     * next one. Guarded by campaignMutex so it can't race with creditCampaignProgress.
     */
    fun defeatBoss() = viewModelScope.launch(Dispatchers.IO) {
        // Result of the guarded section: was a boss felled, and did the rotation wrap to a new
        // World Tier? The tier-up value (null = no wrap) drives the reward bonus + celebration.
        var wrappedToTier: Int? = null
        val cleared = engine.withCampaignLock {
            val active = campaignDao.campaigns().first().firstOrNull { it.active && !it.completed }
                ?: return@withCampaignLock false
            val current = campaignDao.firstOpenMilestone(active.campaignKey) ?: return@withCampaignLock false
            // Only allow the kill once the chapter is fully charged (effective gate reached):
            // World Tier scaling minus equipped GATE_REDUCTION, floored at 1 — the same
            // WorldTier.effectiveGate() the UI's activeQuest projection shows.
            val gate = com.addhd.app.campaign.WorldTier.effectiveGate(
                current.tasksRequired, petDao.get()?.worldTier ?: 1, gateReductionNow(),
            )
            if (current.tasksDone < gate) return@withCampaignLock false

            // Typed-boss vulnerability gate: a typed boss can't be felled by task count alone.
            //  IRON_BEHEMOTH / SHADOW_SPECTER: bossProgress must have reached its cap.
            //  HYDRA: the clean streak must be unbroken since the encounter began — i.e. the streak
            //         started at or before the anchor day (a reset since would move the start AFTER
            //         it). anchor 0 (never woke) can't satisfy this, which is correct.
            when (com.addhd.app.campaign.BossType.fromContent(current.bossType)) {
                com.addhd.app.campaign.BossType.STANDARD -> { /* task gate alone suffices */ }
                com.addhd.app.campaign.BossType.IRON_BEHEMOTH ->
                    if (current.bossProgress < BossType.IRON_BEHEMOTH_SESSIONS) return@withCampaignLock false
                com.addhd.app.campaign.BossType.SHADOW_SPECTER ->
                    if (current.bossProgress < BossType.SHADOW_SPECTER_TIMED) return@withCampaignLock false
                com.addhd.app.campaign.BossType.HYDRA -> {
                    val anchorDay = if (current.bossAnchorMillis <= 0L) Long.MAX_VALUE else
                        java.time.Instant.ofEpochMilli(current.bossAnchorMillis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                    val streakStart = petDao.get()?.cleanStreakStartEpochDay ?: 0L
                    // Unbroken since the fight began: streak started on/before the anchor day and
                    // hasn't reset since (a reset sets start = today > anchorDay).
                    if (streakStart <= 0L || streakStart > anchorDay) return@withCampaignLock false
                }
            }

            // Fell the boss: flip done=true (the reactive activeQuest reads this to pick the next
            // open chapter) AND zero the counter so the cleared chapter can never re-satisfy the
            // boss gate (td >= tr). Leaving tasksDone at the gate was what re-armed the "Defeat the
            // Boss" card, producing the defeat loop.
            campaignDao.updateMilestone(current.copy(tasksDone = 0, done = true))

            // If a next chapter exists its counter is already 0 (fresh import); the completed
            // chapter keeps its full counter as a record. If that was the final chapter, wrap up
            // the campaign and activate the next one.
            val next = campaignDao.firstOpenMilestone(active.campaignKey)
            if (next == null) {
                campaignDao.updateCampaign(active.copy(completed = true, active = false))
                val nextCampaign = campaignDao.campaigns().first()
                    .filter { !it.completed && it.campaignKey != active.campaignKey }
                    .minByOrNull { it.sort }
                if (nextCampaign != null) {
                    campaignDao.updateCampaign(nextCampaign.copy(active = true))
                } else {
                    // NEW GAME PLUS: no incomplete campaigns remain — the whole rotation is done.
                    // Instead of ending, bump the World Tier and restart the loop from campaign 1
                    // with a harder gate and richer rewards. This is the infinite-progression hook.
                    wrappedToTier = incrementWorldTierAndResetLoop()
                }
            }
            true
        }
        // Chapter-clear reward — only when a boss was actually felled (awardXp has its own lock).
        // On a wrap, the reward is already scaled by the NEW tier since awardXp reads it live.
        if (cleared) {
            // The final required task / streak condition was met and the boss fell.
            com.addhd.app.util.Sound.play(appContext, com.addhd.app.util.Sfx.SFX_BOSS_DEFEAT)
            // Campaign boss defeats now drop gear too (previously only the dungeon room-10 boss did,
            // inside emitCombatForWin). Deterministic by lifetime XP + current loot count so it's
            // resume-safe and won't repeat the same piece back-to-back. Surfaced via the same
            // CombatEvent the room-10 drop uses, so the loot toast/overlay renders identically.
            val p = petDao.get() ?: PetEntity()
            val dungeonIndex = com.addhd.app.campaign.Campaign.questIndex(p.xp, p.level)
            val owned = lootDao.allNow().map { it.defId }.toSet()
            val seed = com.addhd.app.campaign.Campaign.totalLifetimeXp(p.xp, p.level) + lootDao.count()
            val drop = com.addhd.app.campaign.LootTable.rollDrop(dungeonIndex, seed, owned)
            lootDao.insert(LootEntity(defId = drop.id, acquiredAtMillis = now()))
            com.addhd.app.util.Haptics.diceCrit(appContext)   // crisp escalating pops on a gear drop
            engine.emitCombat(
                CombatEvent(
                    line = "The boss falls — you claim ${drop.emoji} ${drop.name}!",
                    crit = false,
                    bossDefeated = true,
                    lootName = drop.name,
                    lootEmoji = drop.emoji,
                )
            )
            awardXp(BOSS_CLEAR_XP)
        }
        wrappedToTier?.let {
            // A whole rotation cleared -> a new World Tier unlocked. Celebrate the progression.
            com.addhd.app.util.Sound.play(appContext, com.addhd.app.util.Sfx.SFX_LEVEL_UP)
            engine.emitWorldTierUp(WorldTierEvent(it))
        }
    }

    /**
     * The New Game Plus reset, run under [campaignMutex] from [defeatBoss] when the final campaign
     * of the rotation falls. Increments the persistent World Tier, wipes every campaign's
     * completed/active flags and every milestone's progress back to a fresh state, then re-activates
     * the first campaign (lowest sort). Returns the new tier. Non-destructive to XP, level, loot,
     * and the lifetime quest tally — only the campaign LOOP state is recycled.
     */
    private suspend fun incrementWorldTierAndResetLoop(): Int {
        val p = petDao.get() ?: PetEntity()
        val newTier = p.worldTier + 1
        petDao.upsert(p.copy(worldTier = newTier))

        // Reset every milestone: not done, counter cleared.
        val allCampaigns = campaignDao.campaigns().first()
        allCampaigns.forEach { c ->
            campaignDao.milestonesFor(c.campaignKey).forEach { m ->
                if (m.done || m.tasksDone != 0) {
                    campaignDao.updateMilestone(m.copy(done = false, tasksDone = 0))
                }
            }
        }
        // Reset every campaign to not-completed/inactive, then activate the first by sort.
        allCampaigns.forEach { c ->
            if (c.completed || c.active) campaignDao.updateCampaign(c.copy(completed = false, active = false))
        }
        // Activate the first campaign with an EXPLICITLY reset copy. `first` is the pre-reset
        // snapshot (the just-cleared campaign still carries completed=true), so we must force
        // completed=false here or it would be re-activated as active-AND-completed, which
        // activeQuest filters out — orphaning the campaign and breaking the NG+ resume.
        allCampaigns.minByOrNull { it.sort }?.let { first ->
            campaignDao.updateCampaign(first.copy(active = true, completed = false))
        }
        return newTier
    }

    /**
     * The hero was downed in the boss fight. No XP/task loss — instead the boss goes back on
     * lockdown: we knock the current chapter's charge down by one so [ActiveQuest.bossReady]
     * flips false, and completing one real task re-charges it to re-open the fight. Reuses the
     * exact gate the rest of the app already respects.
     */
    fun bossDownedHero() = viewModelScope.launch(Dispatchers.IO) {
        engine.withCampaignLock {
            val active = campaignDao.campaigns().first().firstOrNull { it.active && !it.completed }
                ?: return@withCampaignLock
            val current = campaignDao.firstOpenMilestone(active.campaignKey) ?: return@withCampaignLock
            // Knock the charge one below the effective gate (tier scaling minus GATE_REDUCTION,
            // floored at 1) so bossReady flips false and a single real task re-opens the fight.
            // Same WorldTier.effectiveGate() every other site uses.
            val gate = com.addhd.app.campaign.WorldTier.effectiveGate(
                current.tasksRequired, petDao.get()?.worldTier ?: 1, gateReductionNow(),
            )
            if (current.tasksDone >= gate) {
                campaignDao.updateMilestone(current.copy(tasksDone = (gate - 1).coerceAtLeast(0)))
            }
        }
    }

    /**
     * Turns a completed win into a combat encounter: rolls damage (scaled by STR + task size),
     * a crit (chance scaled by WIS -> double XP feel), advances the dungeon, and rolls for loot.
     * Every task win has a [TASK_LOOT_DROP_CHANCE_PCT] chance to drop gear; the dungeon-boss
     * (room 10) win ALWAYS drops. Runs before awardXp so the room count reflects the pre-win state.
     */
    // Combat encounter + loot now lives in the shared [engine]; delegated so the scroll/blessing/
    // focus win paths keep their existing single call unchanged.
    private suspend fun emitCombatForWin(baseXp: Int, energy: Int, minor: Boolean = false) =
        engine.emitCombatForWin(baseXp, energy, minor)

    fun deleteTask(task: TaskEntity) = viewModelScope.launch {
        com.addhd.app.util.notify.ReminderScheduler.cancelTimeBlock(appContext, task.id)
        taskDao.delete(task)
    }

    /**
     * Equip/unequip a loot item, enforcing ONE equipped item per slot (Weapon, Chest, Trinket…).
     * Equipping a weapon auto-unequips any other weapon, so the boss fight only ever reads one
     * weapon's stats — closes the "equip everything at once" exploit. Unequipping is unconditional.
     */
    fun setLootEquipped(id: Long, equipped: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (equipped) {
            val slot = lootDao.allNow().firstOrNull { it.id == id }
                ?.let { LootTable.def(it.defId)?.slot }
            if (slot != null) {
                // Unequip any currently-equipped item sharing this slot before equipping the new one.
                lootDao.allNow()
                    .filter { it.id != id && it.equipped && LootTable.def(it.defId)?.slot == slot }
                    .forEach { lootDao.setEquipped(it.id, false) }
            }
        }
        lootDao.setEquipped(id, equipped)
    }

    // ---- Adventurer's Shop ----
    /** Outcome of a [buyItem] attempt, surfaced to the UI (toast/snackbar). */
    sealed class PurchaseResult {
        data class Success(val itemId: String, val goldSpent: Int, val goldLeft: Int) : PurchaseResult()
        data class NotEnoughGold(val itemId: String, val need: Int, val have: Int) : PurchaseResult()
        data class MaxStackReached(val itemId: String) : PurchaseResult()
        data class Unknown(val itemId: String) : PurchaseResult()
    }

    private val _purchases = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 4)
    val purchases: SharedFlow<PurchaseResult> = _purchases

    /** Emits a short confirmation line when a consumable is actually used (Scroll/Potion/Elixir),
     *  so the UI can toast it the same way purchases do instead of the effect firing silently.
     *  Now backed by the shared engine so a widget-side consume would surface identically. */
    val consumedMsg: SharedFlow<String> = engine.consumedMsg

    /**
     * Buy one unit of [itemId] from the [ShopCatalog]. Under [shopMutex] so the balance check and
     * deduction are atomic (no overspend on rapid taps). Rejects cleanly if unknown, unaffordable,
     * or a consumable already at its max stack. On success: deducts gold, then either increments a
     * consumable's qty or inserts the (unique, unequipped) artifact row. Emits a [PurchaseResult].
     */
    fun buyItem(itemId: String) = viewModelScope.launch(Dispatchers.IO) {
        val result = shopMutex.withLock {
            val def = com.addhd.app.campaign.ShopCatalog.item(itemId)
                ?: return@withLock PurchaseResult.Unknown(itemId)
            val p = petDao.get() ?: PetEntity()
            if (p.gold < def.cost) {
                return@withLock PurchaseResult.NotEnoughGold(itemId, def.cost, p.gold)
            }
            val existing = shopDao.get(itemId)
            when (def) {
                is com.addhd.app.campaign.ShopItem.Consumable -> {
                    val have = existing?.qty ?: 0
                    if (have >= def.maxStack) return@withLock PurchaseResult.MaxStackReached(itemId)
                    shopDao.upsert(
                        (existing ?: OwnedItemEntity(itemId = itemId, qty = 0, acquiredAtMillis = now()))
                            .copy(qty = have + 1)
                    )
                }
                is com.addhd.app.campaign.ShopItem.Artifact -> {
                    // Artifacts are unique — owning one already means nothing new to buy.
                    if (existing != null) return@withLock PurchaseResult.MaxStackReached(itemId)
                    shopDao.upsert(OwnedItemEntity(itemId = itemId, qty = 1, acquiredAtMillis = now()))
                }
            }
            // Deduct only after the insert path is committed to (all early-returns are above).
            petDao.upsert(p.copy(gold = p.gold - def.cost))
            PurchaseResult.Success(itemId, def.cost, p.gold - def.cost)
        }
        _purchases.tryEmit(result)
    }

    /** Outcome of a loot sell-back, surfaced as a toast the same way purchases are. */
    sealed interface SellResult {
        data class Success(val name: String, val goldEarned: Int, val newBalance: Int) : SellResult
        data object Equipped : SellResult   // refused — must unequip before selling
        data object NotFound : SellResult   // row already gone (double-tap) — no-op
    }

    private val _sells = MutableSharedFlow<SellResult>(extraBufferCapacity = 4)
    val sells: SharedFlow<SellResult> = _sells

    /**
     * Sell one piece of boss loot for gold based on its tier. Refuses equipped gear (unequip
     * first) so a sale can't silently strip the paperdoll. Deletes the row, then credits gold —
     * mirrors [buyItem] in reverse under its own mutex.
     */
    fun sellLootItem(lootId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val result = lootMutex.withLock {
            val row = lootDao.getById(lootId) ?: return@withLock SellResult.NotFound
            if (row.equipped) return@withLock SellResult.Equipped
            val def = LootTable.def(row.defId)
                ?: return@withLock SellResult.NotFound   // orphaned defId — treat as gone
            val gold = LootTable.sellValue(def)
            // Delete first, then credit — the delete is the point of no return; if the gold write
            // somehow failed the item is gone, but that's the safe direction (no dupe gold).
            lootDao.deleteById(lootId)
            val p = petDao.get() ?: PetEntity()
            val newBalance = p.gold + gold
            petDao.upsert(p.copy(gold = newBalance))
            SellResult.Success(def.name, gold, newBalance)
        }
        _sells.tryEmit(result)
    }

    /**
     * Equip/unequip a shop ARTIFACT, enforcing one equipped artifact per [ArtifactSlot] (mirrors
     * the loot paperdoll rule) so its passive buff can't be double-stacked. No-op for consumables
     * or unowned items. Equipping auto-unequips any other artifact sharing the slot.
     */
    fun setArtifactEquipped(itemId: String, equipped: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        shopMutex.withLock {
            val def = com.addhd.app.campaign.ShopCatalog.artifact(itemId) ?: return@withLock
            val row = shopDao.get(itemId) ?: return@withLock
            if (equipped) {
                val sameSlot = shopDao.ownedNow()
                    .filter { it.itemId != itemId && it.equipped }
                    .filter { com.addhd.app.campaign.ShopCatalog.artifact(it.itemId)?.slot == def.slot }
                    .map { it.itemId }
                if (sameSlot.isNotEmpty()) shopDao.unequipAll(sameSlot)
            }
            shopDao.upsert(row.copy(equipped = equipped))
        }
    }

    /**
     * Consume one unit of a held consumable, returning true if it fired. Decrements qty (deleting
     * the row at 0). The concrete gameplay EFFECT is applied by the caller/handler per
     * [ConsumableEffect] — this method owns only the inventory bookkeeping so it stays testable.
     */
    suspend fun consumeItem(itemId: String): Boolean = shopMutex.withLock {
        com.addhd.app.campaign.ShopCatalog.consumable(itemId) ?: return@withLock false
        val row = shopDao.get(itemId) ?: return@withLock false
        if (row.qty <= 0) return@withLock false
        if (row.qty == 1) shopDao.delete(itemId) else shopDao.upsert(row.copy(qty = row.qty - 1))
        true
    }

    /**
     * The active campaign boss-gate reduction from equipped artifacts (GATE_REDUCTION buff).
     * Returned as a whole number of tasks to subtract; callers apply it AFTER WorldTier scaling
     * and floor the final gate at 1. Exposed so the gate read sites can factor it in consistently.
     */
    suspend fun gateReductionNow(): Int =
        Math.round(equippedBuff(com.addhd.app.campaign.BuffKind.GATE_REDUCTION)).toInt()

    // ---- Hero's Tavern (Stronghold) ----
    /** Feedback for Tavern actions, surfaced as a toast (reuses the purchase-toast banner). */
    private val _tavernMsg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val tavernMsg: SharedFlow<String> = _tavernMsg

    /**
     * Unlock the Hero's Tavern (base tab). Costs [TAVERN_UNLOCK_COST]. Lazily creates the single
     * stronghold row. No-op if already unlocked or gold is short. Under [strongholdMutex] so the
     * gold check + deduction + row insert are atomic.
     */
    fun buyTavern() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            if (strongholdDao.get()?.unlocked == true) return@withLock
            val p = petDao.get() ?: PetEntity()
            if (p.gold < TAVERN_UNLOCK_COST) {
                _tavernMsg.tryEmit("Not enough gold — need ${TAVERN_UNLOCK_COST - p.gold} more for the Hero's Tavern")
                return@withLock
            }
            petDao.upsert(p.copy(gold = p.gold - TAVERN_UNLOCK_COST))
            strongholdDao.upsert(StrongholdEntity(unlocked = true, unlockedAtMillis = now()))
            _tavernMsg.tryEmit("🏰 The Hero's Tavern is yours!")
        }
    }

    /**
     * Build the Garden Plot. Requires the Tavern unlocked and [GARDEN_BUILD_COST] gold. Sets
     * gardenLevel=1 and anchors the brew clock to NOW so the first potion starts ticking on build.
     */
    fun buildGarden() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock       // Tavern not unlocked -> no-op
            if (!s.unlocked || s.gardenLevel > 0) return@withLock
            val p = petDao.get() ?: PetEntity()
            if (p.gold < GARDEN_BUILD_COST) {
                _tavernMsg.tryEmit("Not enough gold — need ${GARDEN_BUILD_COST - p.gold} more for the Garden Plot")
                return@withLock
            }
            petDao.upsert(p.copy(gold = p.gold - GARDEN_BUILD_COST))
            strongholdDao.upsert(s.copy(gardenLevel = 1, gardenLastHarvestMillis = now()))
            _tavernMsg.tryEmit("🌱 Garden Plot built — a potion brews every 3 days")
        }
    }

    /**
     * Harvest brewed Potions of Focus. Grants the derived pending count to the owned_items stack,
     * then advances the harvest anchor by EXACTLY the consumed time chunks (pending × interval) —
     * not to now() — so the partial progress toward the next potion is preserved. Respects the
     * consumable max stack so a harvest can't overflow the inventory cap.
     */
    fun harvestGarden() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock
            if (!s.unlocked || s.gardenLevel <= 0) return@withLock
            val elapsed = (now() - s.gardenLastHarvestMillis).coerceAtLeast(0)
            val pending = (elapsed / GARDEN_BREW_INTERVAL_MILLIS).toInt().coerceAtMost(GARDEN_MAX_PENDING)
            if (pending <= 0) {
                _tavernMsg.tryEmit("Nothing brewed yet — check back later")
                return@withLock
            }
            // Grant potions, honoring the consumable's max stack (extra brew is discarded, not lost
            // time — the cap is the design limit). Uses the same owned_items path as buyItem.
            val potionDef = com.addhd.app.campaign.ShopCatalog.consumable("potion_focus")
            val maxStack = potionDef?.maxStack ?: Int.MAX_VALUE
            val existing = shopDao.get("potion_focus")
            val have = existing?.qty ?: 0
            val granted = pending.coerceAtMost((maxStack - have).coerceAtLeast(0))
            if (granted > 0) {
                shopDao.upsert(
                    (existing ?: OwnedItemEntity(itemId = "potion_focus", qty = 0, acquiredAtMillis = now()))
                        .copy(qty = have + granted)
                )
            }
            // Advance the anchor by the consumed chunks only (preserve the remaining fraction).
            strongholdDao.upsert(
                s.copy(gardenLastHarvestMillis = s.gardenLastHarvestMillis + pending * GARDEN_BREW_INTERVAL_MILLIS)
            )
            // Potions pulled from the Focus Garden — the harvest chime.
            if (granted > 0) com.addhd.app.util.Sound.play(appContext, com.addhd.app.util.Sfx.SFX_POTION_HARVEST)
            _tavernMsg.tryEmit(
                if (granted == pending) "🧪 Harvested $granted Potion${if (granted == 1) "" else "s"} of Focus"
                else "🧪 Harvested $granted (bag full — some brew discarded)"
            )
        }
    }

    // ---- Phase 2: Trophy Room ----
    /**
     * Unlock the Trophy Room (clear the rubble). Requires the Tavern unlocked and
     * [TROPHY_ROOM_UNLOCK_COST] gold. No-op if the Tavern is locked, it's already unlocked, or gold
     * is short. Under [strongholdMutex] so the gold check + deduction + state write are atomic.
     */
    fun unlockTrophyRoom() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock       // Tavern not unlocked -> no-op
            if (!s.unlocked || s.trophyRoomLevel > 0) return@withLock
            val p = petDao.get() ?: PetEntity()
            if (p.gold < TROPHY_ROOM_UNLOCK_COST) {
                _tavernMsg.tryEmit("Not enough gold — need ${TROPHY_ROOM_UNLOCK_COST - p.gold} more to clear the Trophy Room")
                return@withLock
            }
            petDao.upsert(p.copy(gold = p.gold - TROPHY_ROOM_UNLOCK_COST))
            strongholdDao.upsert(s.copy(trophyRoomLevel = 1))
            _tavernMsg.tryEmit("🏆 The Trophy Room is yours — legends await within")
        }
    }

    /**
     * Purchase the Ascended Class Evolution. Requires the Trophy Room unlocked, not already owned,
     * and [ASCENSION_COST] gold. Sets the flag only — the 4th-slot / title mechanics land in a
     * later step. Under [strongholdMutex] for atomic gold-check + deduction + write.
     */
    fun purchaseAscension() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock
            if (s.trophyRoomLevel < 1 || s.ascensionPurchased) return@withLock
            val p = petDao.get() ?: PetEntity()
            if (p.gold < ASCENSION_COST) {
                _tavernMsg.tryEmit("Not enough gold — need ${ASCENSION_COST - p.gold} more for the Ascended Class")
                return@withLock
            }
            petDao.upsert(p.copy(gold = p.gold - ASCENSION_COST))
            strongholdDao.upsert(s.copy(ascensionPurchased = true))
            _tavernMsg.tryEmit("✨ You have Ascended — a new power awakens")
        }
    }

    /**
     * Purchase the Chrono-Anchor. Requires the Trophy Room unlocked, not already owned, and
     * [CHRONO_ANCHOR_COST] gold. Grants [CHRONO_CHARGES_PER_PERIOD] Streak-Freeze charges and stamps
     * the recharge clock to NOW, so the first monthly refill counts from purchase (mirrors how
     * [buildGarden] anchors its clock). Under [strongholdMutex] for atomicity.
     */
    fun purchaseChronoAnchor() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock
            if (s.trophyRoomLevel < 1 || s.chronoAnchorPurchased) return@withLock
            val p = petDao.get() ?: PetEntity()
            if (p.gold < CHRONO_ANCHOR_COST) {
                _tavernMsg.tryEmit("Not enough gold — need ${CHRONO_ANCHOR_COST - p.gold} more for the Chrono-Anchor")
                return@withLock
            }
            petDao.upsert(p.copy(gold = p.gold - CHRONO_ANCHOR_COST))
            strongholdDao.upsert(
                s.copy(
                    chronoAnchorPurchased = true,
                    chronoCharges = CHRONO_CHARGES_PER_PERIOD,
                    chronoChargesResetMillis = now(),
                )
            )
            _tavernMsg.tryEmit("⏳ The Chrono-Anchor hums to life — $CHRONO_CHARGES_PER_PERIOD Streak-Freezes ready")
        }
    }

    // ---- Phase 2: Coin Vault ----
    /**
     * Unlock (construct) the Coin Vault. Requires the Tavern unlocked, not already built, and
     * [VAULT_UNLOCK_COST] gold. Anchors the claim clock to NOW so the first stipend lands one full
     * interval after construction (no instant free claim). Under [strongholdMutex] for atomicity.
     */
    fun unlockCoinVault() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock
            if (!s.unlocked || s.vaultLevel > 0) return@withLock
            val p = petDao.get() ?: PetEntity()
            if (p.gold < VAULT_UNLOCK_COST) {
                _tavernMsg.tryEmit("Not enough gold — need ${VAULT_UNLOCK_COST - p.gold} more to build the Coin Vault")
                return@withLock
            }
            petDao.upsert(p.copy(gold = p.gold - VAULT_UNLOCK_COST))
            strongholdDao.upsert(s.copy(vaultLevel = 1, vaultLastClaimMillis = now()))
            _tavernMsg.tryEmit("💰 The Coin Vault is built — a stipend accrues daily")
        }
    }

    /**
     * Upgrade the Coin Vault from level 1 to level 2 (doubles the stipend). Requires vaultLevel == 1
     * and [VAULT_UPGRADE_COST] gold. Deliberately does NOT touch vaultLastClaimMillis, so the
     * current cooldown carries over — you can't reset the timer by upgrading. Under [strongholdMutex].
     */
    fun upgradeCoinVault() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock
            if (s.vaultLevel != 1) return@withLock
            val p = petDao.get() ?: PetEntity()
            if (p.gold < VAULT_UPGRADE_COST) {
                _tavernMsg.tryEmit("Not enough gold — need ${VAULT_UPGRADE_COST - p.gold} more to upgrade the Vault")
                return@withLock
            }
            petDao.upsert(p.copy(gold = p.gold - VAULT_UPGRADE_COST))
            strongholdDao.upsert(s.copy(vaultLevel = 2))
            _tavernMsg.tryEmit("💰 Coin Vault upgraded — now $VAULT_STIPEND_L2 gold per day")
        }
    }

    /**
     * Claim the daily gold stipend. Requires the Vault built and a full [VAULT_CLAIM_INTERVAL_MILLIS]
     * elapsed since the last claim. Awards [VAULT_STIPEND_L1] / [VAULT_STIPEND_L2] by level.
     *
     * Drift-free: advances the anchor by EXACTLY the consumed 24h chunks (not to now()), mirroring
     * [harvestGarden]. So a claim made a few hours late doesn't push the next one later — and if the
     * player was away for several days, each whole elapsed interval pays out in one claim rather than
     * silently forfeiting the accrued days. Under [strongholdMutex] for atomic check + credit + write.
     */
    fun claimStipend() = viewModelScope.launch(Dispatchers.IO) {
        strongholdMutex.withLock {
            val s = strongholdDao.get() ?: return@withLock
            if (s.vaultLevel <= 0) return@withLock
            val elapsed = (now() - s.vaultLastClaimMillis).coerceAtLeast(0)
            val periods = (elapsed / VAULT_CLAIM_INTERVAL_MILLIS).toInt()
            if (periods <= 0) {
                _tavernMsg.tryEmit("No stipend ready yet — check back later")
                return@withLock
            }
            val perPeriod = if (s.vaultLevel >= 2) VAULT_STIPEND_L2 else VAULT_STIPEND_L1
            val payout = perPeriod * periods
            val p = petDao.get() ?: PetEntity()
            petDao.upsert(p.copy(gold = p.gold + payout))
            // Advance the anchor by the consumed whole intervals only (preserve the partial remainder),
            // so the next stipend stays on its original cadence rather than drifting from `now()`.
            strongholdDao.upsert(
                s.copy(vaultLastClaimMillis = s.vaultLastClaimMillis + periods * VAULT_CLAIM_INTERVAL_MILLIS)
            )
            _tavernMsg.tryEmit(
                if (periods == 1) "💰 Claimed $payout gold from the Vault"
                else "💰 Claimed $payout gold ($periods days' stipend)"
            )
        }
    }

    /**
     * One-tap "No-Brainer" momentum starter shown on Home when there are no open tasks.
     * Inserts the action as an already-completed tiny task so it counts toward today's
     * "tasks done" and awards XP — the whole point is a frictionless first win.
     */
    fun completeQuickWin(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        taskDao.insert(
            TaskEntity(
                title = title.trim(), energy = 1, estimateMin = 5, done = true,
                dueEpochDay = today(), createdAt = now(), sort = now().toInt(),
                completedAtEpochDay = today(),
            )
        )
        // Even a tiny action is part of the fight — a scrappy improvised hit.
        com.addhd.app.util.Sound.play(appContext, com.addhd.app.util.Sfx.SFX_QUICK_WIN)
        com.addhd.app.util.Haptics.runePop(appContext)   // tactile pop on a No-Brainer quick win
        emitCombatForWin(baseXp = 20, energy = 1, minor = true)
        awardXp(20)
        creditCampaignProgress()
    }

    fun autoScheduleToday(startHour: Int = 9, endHour: Int = 18) = viewModelScope.launch {
        val day = today()
        // Re-plan the WHOLE open set each run (pins preserved as anchors, flexible quests
        // sequenced around them). Idempotent — re-running never stacks or overlaps.
        val open = taskDao.openList()
        val pinnedIds = open.filter { it.blockPinned }.map { it.id }.toSet()
        val plans = Scheduler.autoSchedule(open, startHour, endHour)
        plans.forEach { p ->
            // Keep the user's pin flag on manually-locked quests; auto blocks stay unpinned.
            if (p.taskId in pinnedIds) taskDao.pinBlock(p.taskId, day, p.startMin)
            else taskDao.assignBlock(p.taskId, day, p.startMin)
        }
        // The whole day was re-planned — re-arm every alarm from the new block layout.
        com.addhd.app.util.notify.ReminderScheduler.rescheduleAllTimeBlocks(appContext)
    }

    fun clearSchedule() = viewModelScope.launch {
        // Cancel each armed alarm for today before wiping the blocks, then clear.
        taskDao.scheduledBlocksFrom(today()).forEach {
            if (it.blockDayEpochDay == today()) {
                com.addhd.app.util.notify.ReminderScheduler.cancelTimeBlock(appContext, it.id)
            }
        }
        taskDao.clearDay(today())
    }

    // ---- Habits ----
    /**
     * Award habit XP at most once per (habit, day). Checking a habit deletes-then-recreates the
     * check row on every uncheck/recheck, so the row's presence can't be the "already rewarded"
     * memory — an unchecked-and-rechecked habit looks brand-new. We persist a claimed flag in prefs
     * (survives uncheck, process death, and the row deletion) so spamming the box can't farm XP.
     * Correcting a misclick does NOT refund; today's habit XP is earned exactly once.
     */
    private fun awardHabitXpOnce(habitId: Long, epochDay: Long) {
        val key = "habit_xp_${habitId}_$epochDay"
        if (prefs.getBoolean(key, false)) return   // already paid for this habit today
        prefs.edit().putBoolean(key, true).apply()
        awardXp(20)
    }

    fun addHabit(name: String, emoji: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        habitDao.insert(
            HabitEntity(name = name.trim(), emoji = emoji, createdAt = now())
        )
    }

    fun toggleHabit(habit: HabitEntity, checked: Boolean) = viewModelScope.launch {
        val t = today()
        if (checked) {
            habitDao.check(HabitCheckEntity(habit.id, t)); awardHabitXpOnce(habit.id, t)
        } else {
            habitDao.uncheck(habit.id, t)
        }
    }

    /**
     * Toggle a habit's check for an ARBITRARY day (calendar backfill). Lets the user record
     * days they'd started before adding the habit, or fix a missed check. Future days are
     * rejected so the streak can't be gamed forward. XP is only awarded for adding a check.
     */
    fun toggleHabitDay(habit: HabitEntity, epochDay: Long) = viewModelScope.launch {
        if (epochDay > today()) return@launch
        val already = habitDao.isChecked(habit.id, epochDay)
        if (already) {
            habitDao.uncheck(habit.id, epochDay)
        } else {
            habitDao.check(HabitCheckEntity(habit.id, epochDay)); awardHabitXpOnce(habit.id, epochDay)
        }
    }

    fun archiveHabit(habit: HabitEntity) = viewModelScope.launch {
        habitDao.update(habit.copy(archived = true))
    }

    /**
     * Manual "I slipped up" reset: clears this habit's entire check history so the streak
     * drops to 0 immediately (and today's box shows empty), without deleting the habit itself.
     * The derived daily task updates in lockstep since it reads the same checks.
     */
    fun resetHabitStreak(habit: HabitEntity) = viewModelScope.launch {
        habitDao.deleteChecksFor(habit.id)
    }

    /**
     * Permanently delete a habit and its entire check history. Removing the checks also
     * removes the derived daily task on the Tasks board — there's no separate task row to clean up.
     */
    fun deleteHabit(habit: HabitEntity) = viewModelScope.launch {
        habitDao.deleteChecksFor(habit.id)
        habitDao.deleteHabit(habit.id)
    }

    // ---- Mood ----
    fun logMood(mood: Int, energy: Int, note: String) = viewModelScope.launch {
        moodDao.insert(MoodEntity(epochMillis = now(), mood = mood, energy = energy, note = note.trim()))
        // Locking in how you feel on the Feel screen — a satisfying commit.
        com.addhd.app.util.Sound.play(appContext, com.addhd.app.util.Sfx.SFX_LOCK_POP)
        awardXp(10)
    }

    // ---- Journal ----
    fun addJournal(prompt: String, body: String) = viewModelScope.launch {
        if (body.isBlank()) return@launch
        journalDao.insert(JournalEntity(epochMillis = now(), prompt = prompt, body = body.trim()))
        awardXp(15)
    }

    fun deleteJournal(j: JournalEntity) = viewModelScope.launch { journalDao.delete(j) }

    // ---- Dopamine ----
    // Riding out an urge is the hardest executive-function win in the app — surfing a
    // 90-second chemical wave instead of acting on it. Reward it far above a normal task
    // (a medium task is ~25 XP) so the payoff matches the struggle. A healthy boost still
    // counts, just for less, since it's an easier redirect.
    fun logDopamine(level: Int, trigger: String, action: String, rodeItOut: Boolean) = viewModelScope.launch {
        dopamineDao.insert(
            DopamineEntity(
                epochMillis = now(), level = level, trigger = trigger.trim(),
                action = action.trim(), rodeItOut = rodeItOut,
            )
        )
        val baseXp = if (rodeItOut) 75 else 20
        if (rodeItOut) {
            // Urge-surf: the hardest win. Unchanged — rolls the combat loot pipeline + big XP.
            emitCombatForWin(baseXp = baseXp, energy = 1, minor = true)
            awardXp(baseXp)
            return@launch
        }

        // Invoke-a-Blessing: a GUARANTEED tiered reward, so a blessing never comes up empty.
        //   ~60% -> a gear drop (duplicate-protected via the same owned-exclusion roll as bosses)
        //   ~40% -> a named gold/XP boon (explicitly stated, not a silent XP tick)
        // The reward is emitted on the replay=1 [blessing] flow so the Sanctuary's DoneCard can
        // read exactly what was granted even if the tap raced the screen transition.
        val p = petDao.get() ?: PetEntity()
        val roll = kotlin.random.Random.nextInt(100)
        val reward: BlessingReward = if (roll < BLESSING_GEAR_CHANCE_PCT) {
            val dungeonIndex = Campaign.questIndex(p.xp, p.level)
            val owned = lootDao.allNow().map { it.defId }.toSet()
            val seed = Campaign.totalLifetimeXp(p.xp, p.level) + lootDao.count() + roll
            val drop = LootTable.rollDrop(dungeonIndex, seed, owned)
            lootDao.insert(LootEntity(defId = drop.id, acquiredAtMillis = now()))
            val tier = LootTable.rarityOf(drop).name.lowercase().replaceFirstChar { it.uppercase() }
            BlessingReward(
                BlessingReward.Kind.GEAR, xp = baseXp,
                lootName = drop.name, lootEmoji = drop.emoji, rarity = tier,
            )
        } else if (roll % 2 == 0) {
            // Gold boon — a flat purse, scaled a little by World Tier so it stays relevant in NG+.
            val purse = BLESSING_GOLD_BASE * p.worldTier.coerceAtLeast(1)
            BlessingReward(BlessingReward.Kind.GOLD, xp = baseXp, gold = purse)
        } else {
            // XP boon — a bonus jolt of resolve on top of the base.
            BlessingReward(BlessingReward.Kind.XP_BOON, xp = baseXp + BLESSING_XP_BOON)
        }

        // Credit atomically: the boon's flat gold rides awardXp's single pet write (no racing upsert).
        awardXp(reward.xp, bonusGold = reward.gold)
        // Reward haptics fire in the UI, paired with the toast appearing (see SosScreen), so the
        // buzz lands with the visible reward rather than ahead of it.
        _blessing.tryEmit(reward)
    }

    // ---- Focus ----
    fun logFocus(
        minutes: Int,
        completed: Boolean,
        /**
         * When the block actually ENDED. Defaults to now, but a block that elapsed while the app
         * was frozen/closed is credited on next launch — stamping that with the current time would
         * file a 2pm session under 6pm and quietly corrupt the "peak focus window" insight.
         */
        atMillis: Long = now(),
    ) = viewModelScope.launch(Dispatchers.IO) {
        val id = focusDao.insert(
            FocusSessionEntity(epochMillis = atMillis, minutes = minutes, completed = completed),
        )
        if (completed) {
            // If the hero already said what they're working on, honour it: attribute silently and
            // skip the prompt. Asking "what did you work on?" right after they told us is the
            // thing that made "Pick it back up" feel like it hadn't registered.
            val target = _focusTargetId.value
            if (target != null) {
                focusDao.attributeToTask(id, target)
                _focusAttributedTo.value = taskDao.getById(target)?.title
                _lastFocusSessionId.value = null
            } else {
                // Otherwise offer this block for attribution in the finish prompt.
                _lastFocusSessionId.value = id
            }
            awardXp(minutes)
            // Iron Behemoth interceptor: a completed deep-work session chips its armor.
            damageTypedBoss(com.addhd.app.campaign.BossType.IRON_BEHEMOTH, BossType.IRON_BEHEMOTH_SESSIONS)
        }
    }

    // ---- Post-block attribution (feeds estimate-vs-actual) ----
    // Attribution is asked for AFTER a block, never before. Choosing a target up front would put a
    // decision in front of the Start button, which is the precise moment the screen exists to get
    // past. It is always optional: an unattributed block still pays XP and still counts toward the
    // day's focus minutes — it just can't contribute to time-estimate calibration.

    val canAttributeFocus: StateFlow<Boolean> =
        _lastFocusSessionId.map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val focusAttributedTo: StateFlow<String?> = _focusAttributedTo

    /**
     * Attribute the block that just finished to [task]. One attribution per block. Picking a quest
     * here also makes it the standing target, so a follow-on block is attributed without asking
     * again.
     */
    fun attributeFocusTo(task: TaskEntity) {
        val sessionId = _lastFocusSessionId.value ?: return
        _lastFocusSessionId.value = null
        _focusAttributedTo.value = task.title
        setFocusTarget(task)
        viewModelScope.launch(Dispatchers.IO) { focusDao.attributeToTask(sessionId, task.id) }
    }

    private fun clearFocusAttribution() {
        _lastFocusSessionId.value = null
        _focusAttributedTo.value = null
    }

    // ================================================================================================
    //  All-time memory (#3) — the app is supposed to accumulate, not reset.
    // ================================================================================================
    // Every existing readout was a 7-day window or a live count over purgeable rows, so after six
    // months the app knew no more about you than in week one. These only ever grow.

    /** Lifetime completed focus minutes. */
    val lifetimeFocusMinutes: StateFlow<Int> =
        focusDao.lifetimeMinutes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    data class AllTime(
        val sinceEpochDay: Long,     // 0 = unknown/new save
        val daysKnown: Int,
        val questsCompleted: Int,
        val focusMinutes: Int,
        val bigQuests: Int,
        val focusBlocks: Int,
        val level: Int,
    ) {
        val hasHistory: Boolean get() = questsCompleted > 0 || focusMinutes > 0
    }

    val allTime: StateFlow<AllTime> = combine(
        lifetimeQuestsCompleted, lifetimeFocusMinutes, focusBlocksCompleted, pet,
    ) { quests, minutes, blocks, p ->
        val since = p?.firstOpenEpochDay ?: 0L
        AllTime(
            sinceEpochDay = since,
            daysKnown = if (since <= 0L) 0 else (today() - since + 1).coerceAtLeast(1).toInt(),
            questsCompleted = quests,
            focusMinutes = minutes,
            bigQuests = p?.totalBigQuestsCompleted ?: 0,
            focusBlocks = blocks,
            level = p?.level ?: 1,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        AllTime(0L, 0, 0, 0, 0, 0, 1),
    )

    // ================================================================================================
    //  Graceful return (#2) — the moment a lapsed user decides whether to stay.
    // ================================================================================================
    // Coming back after a fortnight used to mean a dead streak, a purged quest log and no
    // acknowledgement at all. ADHD use is bursty by nature, so that return is a normal event, not a
    // failure — it gets a welcome instead of a scoreboard.

    data class ReturnWelcome(
        val daysAway: Int,
        val allTime: AllTime,
        /** One low-effort quest to restart on, if the backlog has one. */
        val suggestion: TaskEntity?,
    )

    private val _returnWelcome = MutableStateFlow<ReturnWelcome?>(null)
    val returnWelcome: StateFlow<ReturnWelcome?> = _returnWelcome

    fun dismissReturnWelcome() { _returnWelcome.value = null }

    /**
     * Record this launch and, if the gap was long enough, assemble the welcome. Runs once per
     * process from init; the stamp is written immediately so a second launch the same day is quiet.
     */
    private fun noteAppOpened() {
        val today = today()
        val last = prefs.getLong(KEY_LAST_OPEN_DAY, 0L)
        prefs.edit().putLong(KEY_LAST_OPEN_DAY, today).apply()
        if (last <= 0L) return                       // first ever launch — onboarding, not a return
        val gap = (today - last).toInt()
        if (gap < LAPSE_DAYS) return

        // Read straight from the DAOs rather than from this class's StateFlows.
        //
        // Those flows are stateIn-backed, so during a cold start they hand back their seed values
        // (0 / emptyList) while their upstreams are still spinning up. Every attempt to wait for
        // "real" data via a predicate just raced a different field: first it rendered "0 quests,
        // Level 1", then it rendered no totals at all. A suspend DAO read can only return the
        // actual row, which makes the dialog's contents deterministic — and this is the one screen
        // whose entire purpose is showing correct totals.
        viewModelScope.launch(Dispatchers.IO) {
            val p = petDao.get() ?: return@launch
            val taskList = taskDao.all().first()
            val stats = AllTime(
                sinceEpochDay = p.firstOpenEpochDay,
                daysKnown = if (p.firstOpenEpochDay <= 0L) 0
                else (today() - p.firstOpenEpochDay + 1).coerceAtLeast(1).toInt(),
                questsCompleted = p.totalQuestsCompleted + taskList.count { it.done },
                focusMinutes = focusDao.lifetimeMinutes().first(),
                bigQuests = p.totalBigQuestsCompleted,
                focusBlocks = focusDao.completedCountNow(),
                level = p.level,
            )
            val open = taskList.filter { !it.done && it.parentId == null }
                .sortedBy { it.energy * 100 + it.estimateMin }
            val suggestion = open.firstOrNull { it.energy <= 1 } ?: open.firstOrNull()
            _returnWelcome.value = ReturnWelcome(gap, stats, suggestion)
        }
    }

    /** Stamp the start date on a save that doesn't have one (new install, or an empty upgrade). */
    private fun ensureFirstOpenStamped() = viewModelScope.launch(Dispatchers.IO) {
        val p = petDao.get() ?: return@launch
        if (p.firstOpenEpochDay <= 0L) petDao.upsert(p.copy(firstOpenEpochDay = today()))
    }

    // ================================================================================================
    //  Resume context (#4) — "what was I doing?"
    // ================================================================================================
    /**
     * The quest explicitly committed to, resolved live so it clears itself the moment that quest
     * is completed or deleted — no stale target can survive.
     */
    val focusTarget: StateFlow<TaskEntity?> =
        combine(_focusTargetId, tasks) { id, taskList ->
            id?.let { target -> taskList.firstOrNull { it.id == target && !it.done } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Commit to working on [task] next. Carries the choice into the next block so its minutes are
     * attributed automatically — saying "I'm still on this" used to be discarded immediately, and
     * the finish prompt would then ask which quest you'd just been working on.
     */
    fun setFocusTarget(task: TaskEntity?) {
        _focusTargetId.value = task?.id
        prefs.edit().apply {
            if (task == null) remove(KEY_FOCUS_TARGET_ID) else putLong(KEY_FOCUS_TARGET_ID, task.id)
        }.apply()
    }

    /** The newest ATTRIBUTED block's quest, if still open — the implicit "what was I doing". */
    private val lastAttributedTask: StateFlow<TaskEntity?> =
        combine(focusSessions, tasks) { sessions, taskList ->
            val lastTaskId = sessions
                .filter { it.completed && it.taskId != null }
                .maxByOrNull { it.epochMillis }
                ?.taskId
            lastTaskId?.let { id -> taskList.firstOrNull { it.id == id && !it.done } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * What Home reminds you of: an explicit [focusTarget] wins over the inferred
     * [lastAttributedTask], so a deliberate choice always outranks a guess. Suppressed for the rest
     * of the day once acknowledged — see [ResumeAck].
     */
    val resumeContext: StateFlow<TaskEntity?> =
        combine(focusTarget, lastAttributedTask, _resumeAck) { target, inferred, ack ->
            val task = target ?: inferred
            val answeredToday = task != null && ack != null &&
                ack.taskId == task.id && ack.day == today()
            if (answeredToday) null else task
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * "Still on it" — answer the reminder. This both records the commitment (so the next block's
     * minutes are attributed to this quest without asking) and quiets the card until tomorrow.
     *
     * Deliberately does NOT navigate anywhere. The card asks a question about a quest; a timer is
     * not an answer to it.
     */
    fun acknowledgeStillWorkingOn(task: TaskEntity) {
        setFocusTarget(task)
        val ack = ResumeAck(task.id, today())
        _resumeAck.value = ack
        prefs.edit()
            .putLong(KEY_RESUME_ACK_ID, ack.taskId)
            .putLong(KEY_RESUME_ACK_DAY, ack.day)
            .apply()
    }

    /** True when [resumeContext] is a quest actively committed to, not merely inferred from history. */
    val resumeIsActive: StateFlow<Boolean> = focusTarget.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** One quest's estimate against the focus time actually spent on it. */
    data class Calibration(
        val title: String,
        val estimateMin: Int,
        val actualMin: Int,
        val done: Boolean,
    ) {
        /** >1 means it took longer than estimated. */
        val ratio: Float get() = actualMin.toFloat() / estimateMin.coerceAtLeast(1)
    }

    /**
     * Estimate-vs-actual per quest, newest first, for quests with attributed focus time.
     * Time-estimation difficulty is close to definitional in ADHD, and the app already had half
     * the data (every task carries an estimateMin that nothing ever checked) — this closes the
     * loop so the gap is visible instead of invisible.
     */
    val calibration: StateFlow<List<Calibration>> =
        combine(tasks, focusDao.minutesPerTask()) { taskList, perTask ->
            val byId = taskList.associateBy { it.id }
            perTask.mapNotNull { row ->
                val t = byId[row.taskId] ?: return@mapNotNull null
                Calibration(t.title, t.estimateMin, row.minutes, t.done)
            }.sortedByDescending { it.actualMin }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Focus timer ----
    // The countdown lives HERE, not in FocusScreen. As screen-local `remember` state it died the
    // moment you left the Focus tab (or Android reclaimed the process), so a 25/45-minute block
    // run the way the screen actually tells you to run it — phone face-down, app in the background
    // — was never credited: nothing reached focus_sessions, so Home's "focus min" and Your
    // Patterns both stayed at 0. Now the block is anchored to a persisted wall-clock end time, so
    // it keeps running across tabs and is credited exactly once even if the process is killed
    // mid-session and restored afterwards.

    /** Live state of the Focus block. [finished] means "just completed and already credited". */
    data class FocusTimer(
        val minutes: Int = DEFAULT_FOCUS_MIN,
        val remainingSec: Int = DEFAULT_FOCUS_MIN * 60,
        val running: Boolean = false,
        val finished: Boolean = false,
    ) {
        /**
         * True when nothing is part-way through — drives Start vs. Resume and hides the retreat
         * hatch. A finished block counts as fresh: it's already banked, so the next tap must begin
         * a NEW block rather than try to "resume" a run with zero seconds left.
         */
        val isFresh: Boolean get() = finished || remainingSec >= minutes * 60
    }

    // _focusTimer / focusTicker are declared above the init block — see the note there.

    /** Pick a duration. Ignored mid-block, and it clears any stale finish prompt. */
    fun setFocusMinutes(minutes: Int) {
        if (_focusTimer.value.running) return
        _focusTimer.value = FocusTimer(minutes = minutes, remainingSec = minutes * 60)
    }

    /** Begin a fresh block of [minutes]. */
    fun startFocus(minutes: Int = _focusTimer.value.minutes) =
        runFocus(remainingSec = minutes * 60, minutes = minutes)

    /** Continue a paused block from where it stopped. */
    fun resumeFocus() {
        val s = _focusTimer.value
        if (!s.running && s.remainingSec > 0) runFocus(s.remainingSec, s.minutes)
    }

    /** Hold the block. The remaining time is kept; the persisted anchor is dropped. */
    fun pauseFocus() {
        if (!_focusTimer.value.running) return
        val left = remainingFromAnchor() ?: _focusTimer.value.remainingSec
        stopTicker()
        _focusTimer.update { it.copy(running = false, remainingSec = left, finished = false) }
    }

    /**
     * Abandon the block (Reset / Tactical Retreat). Whole minutes already spent are recorded as an
     * INCOMPLETE session — it shows up in history without paying XP or touching a streak.
     */
    fun resetFocus() {
        val s = _focusTimer.value
        // A completed block is already banked — clearing its prompt must not log the same minutes
        // a second time (as an incomplete session on top of the completed one).
        if (s.finished) {
            _focusTimer.value = FocusTimer(minutes = s.minutes, remainingSec = s.minutes * 60)
            return
        }
        val left = (if (s.running) remainingFromAnchor() else null) ?: s.remainingSec
        stopTicker()
        val spentMin = s.minutes - (left + 59) / 60     // round the leftover UP → minutes fully spent
        if (spentMin > 0) logFocus(spentMin, false)
        _focusTimer.value = FocusTimer(minutes = s.minutes, remainingSec = s.minutes * 60)
    }

    /** Clear the post-block prompt and return the screen to its default idle state. */
    fun dismissFocusFinish() {
        if (_focusTimer.value.running) return
        _focusTimer.value = FocusTimer()
        clearFocusAttribution()
    }

    /** Arm the countdown and persist the wall-clock anchor it's derived from. */
    private fun runFocus(remainingSec: Int, minutes: Int) {
        val endAt = now() + remainingSec * 1000L
        prefs.edit()
            .putLong(KEY_FOCUS_END_AT, endAt)
            .putInt(KEY_FOCUS_MINUTES, minutes)
            .apply()
        _focusTimer.value = FocusTimer(minutes, remainingSec, running = true)
        clearFocusAttribution()      // the previous block's window closes when a new one opens
        // Live countdown in the shade / on the lock screen for the duration of the block.
        com.addhd.app.util.notify.FocusTimerService.start(appContext, endAt, minutes)
        // Backstop: an alarm at the end time, so the completion still announces itself if the OS
        // freezes us mid-block (see FocusEndReceiver).
        com.addhd.app.util.notify.ReminderScheduler.scheduleFocusEnd(appContext, endAt, minutes)
        focusTicker?.cancel()
        focusTicker = viewModelScope.launch {
            while (true) {
                // Derived from the clock, never from a tick count, so a frozen/backgrounded app
                // catches up to the true elapsed time on its very next poll.
                val left = ((endAt - now()) / 1000L).toInt().coerceAtLeast(0)
                _focusTimer.update { it.copy(remainingSec = left) }
                if (left <= 0) break
                delay(250)
            }
            completeFocus(minutes, endedAtMillis = endAt)
        }
    }

    /**
     * Credit a finished block. Clearing the anchor FIRST is what makes this exactly-once: a restore
     * can no longer see a session that has already been paid out.
     */
    private fun completeFocus(
        minutes: Int,
        endedAtMillis: Long = now(),
        /** False when the alarm backstop has already announced it — don't notify twice. */
        notify: Boolean = true,
    ) {
        clearFocusAnchor()
        com.addhd.app.util.notify.FocusTimerService.stop(appContext)
        com.addhd.app.util.notify.ReminderScheduler.cancelFocusEnd(appContext, minutes)
        _focusTimer.value = FocusTimer(minutes, 0, running = false, finished = true)
        // Reaches the user even if the app is in the background when the block lands.
        if (notify) com.addhd.app.util.notify.Notifier.showFocusComplete(appContext, minutes)
        logFocus(minutes, true, atMillis = endedAtMillis)
    }

    /**
     * Re-attach to a block that outlived the process. Still running → pick the countdown back up;
     * already elapsed → credit it now rather than losing the work the user actually did.
     */
    private fun restoreFocusTimer() {
        val endAt = prefs.getLong(KEY_FOCUS_END_AT, 0L)
        if (endAt <= 0L) return
        val minutes = prefs.getInt(KEY_FOCUS_MINUTES, DEFAULT_FOCUS_MIN)
        val left = ((endAt - now()) / 1000L).toInt()
        if (left > 0) {
            runFocus(left, minutes)
        } else {
            // Elapsed while we were gone: the alarm backstop already posted the notification, so
            // credit silently — and against the moment it really finished.
            completeFocus(minutes, endedAtMillis = endAt, notify = false)
        }
    }

    /** Seconds left according to the persisted anchor, or null when no block is armed. */
    private fun remainingFromAnchor(): Int? {
        val endAt = prefs.getLong(KEY_FOCUS_END_AT, 0L)
        return if (endAt > 0L) ((endAt - now()) / 1000L).toInt().coerceAtLeast(0) else null
    }

    private fun stopTicker() {
        focusTicker?.cancel()
        focusTicker = null
        val minutes = _focusTimer.value.minutes
        clearFocusAnchor()
        com.addhd.app.util.notify.FocusTimerService.stop(appContext)
        com.addhd.app.util.notify.ReminderScheduler.cancelFocusEnd(appContext, minutes)
    }

    private fun clearFocusAnchor() {
        prefs.edit().remove(KEY_FOCUS_END_AT).remove(KEY_FOCUS_MINUTES).apply()
    }

    // ================================================================================================
    //  Startup work that depends on this class's own flows — MUST stay last.
    // ================================================================================================
    // Kotlin runs initializers in declaration order, and viewModelScope dispatches
    // Main.immediate, so a coroutine launched from the TOP init block executes inline, partway
    // through construction, while later properties are still null. logFocus() compounds it by
    // writing state from a Dispatchers.IO coroutine that runs in genuine parallel with the
    // remaining initializers. Three separate NullPointerExceptions came from exactly that.
    //
    // Declaring this init block after every property makes the ordering structural rather than
    // something each new call site has to remember.
    init {
        // Re-attach to (or credit) a Focus block that was running when the process last died.
        restoreFocusTimer()
        // All-time anchor, then lapse detection (which reads the allTime flow).
        ensureFirstOpenStamped()
        noteAppOpened()
    }
}
