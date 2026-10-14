package com.addhd.app.vm

import android.content.Context
import com.addhd.app.campaign.BossType
import com.addhd.app.campaign.Campaign
import com.addhd.app.campaign.CombatFlavor
import com.addhd.app.campaign.HeroClass
import com.addhd.app.campaign.HeroStats
import com.addhd.app.campaign.LootTable
import com.addhd.app.campaign.WorldTier
import com.addhd.app.data.AppDatabase
import com.addhd.app.data.LootEntity
import com.addhd.app.data.PetEntity
import com.addhd.app.data.TaskEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * The headless, process-wide completion-reward pipeline — the single loop that turns "a quest was
 * completed" into the full cascade of side effects: task-state, combat encounter + loot, XP/gold,
 * combo streak, campaign/boss credit, streak feed. Extracted verbatim from AppViewModel so BOTH the
 * on-screen ViewModel AND the home-screen Glance widget's background ActionCallback drive the SAME
 * reward logic through the SAME instance.
 *
 * Why a singleton (not a per-VM object): the combo streak, the primed-Focus-potion flag, and the two
 * serialization mutexes are shared invariants. If the widget owned its own copy, a check-off from the
 * widget could double-award XP against a completion tapped in the app at the same instant, or the
 * combo could fork. One instance ⇒ one set of locks ⇒ one source of truth, regardless of caller.
 *
 * State ownership moved here from the VM:
 *   - [xpMutex]  serializes the XP/gold read-modify-write + the primed-potion consume.
 *   - [campaignMutex] serializes campaign-gate credit, typed-boss damage, and boss defeat.
 *   - the ephemeral combo counter (+ its idle-decay clock) and the primed-potion flag.
 *   - the [combat] / [consumedMsg] / [comboCount] / [xpPotionPrimed] flows the UI observes.
 *
 * The VM keeps its Room stream declarations, its coroutine scope, and every OTHER feature; it now
 * calls into this engine for the reward loop and simply re-exposes the engine's flows.
 */
class RewardEngine private constructor(appContext: Context) {

    private val db = AppDatabase.get(appContext)
    private val taskDao = db.taskDao()
    private val petDao = db.petDao()
    private val campaignDao = db.campaignDao()
    private val shopDao = db.shopDao()
    private val lootDao = db.lootDao()

    // App context captured for Sound/Haptics playback inside the reward loop.
    private val ctx = appContext.applicationContext

    private fun today() = LocalDate.now().toEpochDay()
    private fun now() = System.currentTimeMillis()

    // ---- Serialization (moved from AppViewModel, same discipline) --------------------------------
    /** Serializes the XP/gold award so two near-simultaneous completions can't lose a credit. */
    private val xpMutex = Mutex()
    /** Serializes campaign-gate credit / typed-boss damage / boss defeat against each other. */
    private val campaignMutex = Mutex()

    // ---- Observable reward state (the VM re-exposes these verbatim) ------------------------------
    private val _combat = MutableSharedFlow<AppViewModel.CombatEvent>(extraBufferCapacity = 4)
    val combat: SharedFlow<AppViewModel.CombatEvent> = _combat

    private val _consumedMsg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val consumedMsg: SharedFlow<String> = _consumedMsg

    private val _worldTierUp = MutableSharedFlow<AppViewModel.WorldTierEvent>(extraBufferCapacity = 2)
    val worldTierUp: SharedFlow<AppViewModel.WorldTierEvent> = _worldTierUp

    // Public emit passthroughs so the VM's OTHER reward sites (defeatBoss loot, scroll-skip, blessing,
    // focus-complete) push onto the SAME shared streams the widget/app observe — one source of truth.
    fun emitCombat(event: AppViewModel.CombatEvent) { _combat.tryEmit(event) }
    fun emitConsumed(msg: String) { _consumedMsg.tryEmit(msg) }
    fun emitWorldTierUp(event: AppViewModel.WorldTierEvent) { _worldTierUp.tryEmit(event) }

    // Lock accessors: the engine OWNS the two serialization locks, but several VM-side paths still
    // do their own read-modify-writes on the same rows — the fidget/tavern XP awards (pet XP/gold)
    // and the boss defeat / hero-downed / campaign-select (milestone state). They must share the
    // SAME lock as the engine's awardXp/creditCampaignProgress or two locks would race on one row.
    // Exposing the locks (rather than moving every method in) keeps this refactor surgical.
    suspend fun <T> withXpLock(block: suspend () -> T): T = xpMutex.withLock { block() }
    suspend fun <T> withCampaignLock(block: suspend () -> T): T = campaignMutex.withLock { block() }

    // ---- Combo streak (ephemeral, in-memory) -----------------------------------------------------
    private val COMBO_IDLE_DECAY_MS = 15 * 60 * 1000L
    private val COMBO_MULTS = doubleArrayOf(1.0, 1.0, 1.2, 1.5, 1.8, 2.0)
    private val _comboCount = MutableStateFlow(0)
    val comboCount: StateFlow<Int> = _comboCount
    private var lastComboWinAt = 0L

    private fun bumpCombo(): Double {
        val t = now()
        _comboCount.value = if (t - lastComboWinAt > COMBO_IDLE_DECAY_MS) 1 else _comboCount.value + 1
        lastComboWinAt = t
        return COMBO_MULTS[_comboCount.value.coerceIn(0, COMBO_MULTS.size - 1)]
    }

    fun coolCombo() { _comboCount.value = 0; lastComboWinAt = 0L }

    private fun playComboFeedback(combo: Int) {
        when {
            combo >= 4 -> {
                com.addhd.app.util.Haptics.lockPop(ctx)
                com.addhd.app.util.Sound.play(ctx, com.addhd.app.util.Sfx.SFX_DICE_CRIT)
            }
            combo >= 2 -> {
                com.addhd.app.util.Haptics.boardClear(ctx)
                com.addhd.app.util.Sound.play(ctx, com.addhd.app.util.Sfx.SFX_QUICK_WIN)
            }
            else -> com.addhd.app.util.Haptics.runePop(ctx)
        }
    }

    // ---- Potion of Focus (primed, single-shot) ---------------------------------------------------
    private val _xpPotionPrimed = MutableStateFlow(false)
    val xpPotionPrimed: StateFlow<Boolean> = _xpPotionPrimed

    /** Prime the next XP award to double. Returns false (no prime) if already primed. */
    fun primeFocusPotion(): Boolean {
        if (_xpPotionPrimed.value) return false
        _xpPotionPrimed.value = true
        _consumedMsg.tryEmit("🧪 Focus primed — your next win earns Double XP")
        return true
    }

    // ---- Shop buffs (read the owned-items table live) --------------------------------------------
    private suspend fun equippedBuff(kind: com.addhd.app.campaign.BuffKind): Double {
        val equippedIds = shopDao.ownedNow().filter { it.equipped }.map { it.itemId }
        return com.addhd.app.campaign.ShopCatalog.buffTotal(equippedIds, kind)
    }

    /** Whole-task boss-gate reduction from equipped GATE_REDUCTION artifacts (applied post-scaling). */
    private suspend fun gateReductionNow(): Int =
        Math.round(equippedBuff(com.addhd.app.campaign.BuffKind.GATE_REDUCTION)).toInt()

    /** Gold for an XP award: [GOLD_PER_XP] of raw XP, floored, scaled by tier + GOLD_GENERATION. */
    private fun goldFor(xpAmount: Int, worldTier: Int, goldBuff: Double): Int {
        val tierMult = WorldTier.scalar(worldTier)
        val raw = xpAmount * GOLD_PER_XP * tierMult * (1.0 + goldBuff)
        return Math.round(raw).toInt().coerceAtLeast(MIN_GOLD_PER_AWARD)
    }

    // =============================================================================================
    //  THE HEADLESS COMPLETION ENTRY POINT — what both the VM's toggleTask and the widget call.
    // =============================================================================================
    /**
     * Run the full reward cascade for completing [task] (the newDone branch of the old toggleTask):
     * write done+completedAt, sync the time-block alarm via [onTaskWritten], play the toggle SFX,
     * bump the combo (top-level quests only), fire the combat/loot encounter, award XP+gold, credit
     * campaign progress (top-level only), and land a Shadow-Specter hit for time-blocked quests.
     *
     * [onTaskWritten] lets the caller re-arm/cancel the task's alarm without this engine depending on
     * the notification scheduler — the VM passes its syncTimeBlockAlarm; the widget passes the same.
     * Safe to call from any coroutine on any dispatcher; the mutexes serialize the shared state.
     */
    suspend fun completeTask(task: TaskEntity, onTaskWritten: (TaskEntity) -> Unit = {}) {
        val updated = task.copy(done = true, completedAtEpochDay = today())
        taskDao.update(updated)
        onTaskWritten(updated)

        com.addhd.app.util.Sound.play(ctx, com.addhd.app.util.Sfx.SFX_TASK_TOGGLE)
        // Combo: only top-level quests feed it (micro-steps can't be spammed for combo).
        val comboMult = if (task.parentId == null) bumpCombo() else 1.0
        playComboFeedback(if (task.parentId == null) _comboCount.value else 0)

        val baseXp = 15 + task.energy * 5
        emitCombatForWin(baseXp = baseXp, energy = task.energy)
        // bigQuest rides the SAME pet write as the XP award rather than taking a second
        // read-modify-write on the row, so STR can't be lost to a race.
        awardXp(baseXp, comboMult = comboMult, bigQuest = task.energy >= 2)
        // NB: the chapter is NOT credited here any more. A task clears a dungeon room; the chapter
        // advances only when that quest's room-10 boss falls (see [emitCombatForWin]). The two used
        // to run in parallel, which put two unrelated bosses on Home at the same time.
        // Shadow Specter interceptor: only TIME-BLOCKED tasks harm the phantom (no-op otherwise).
        if (task.blockStartMin != null) {
            damageTypedBoss(BossType.SHADOW_SPECTER, BossType.SHADOW_SPECTER_TIMED)
        }
    }

    /**
     * XP + gold award (moved verbatim from AppViewModel.awardXp). Reads buffs outside [xpMutex],
     * then does the read-modify-write of pet XP/level/gold/streak inside it, consuming the primed
     * potion atomically. Level-up fanfare fires outside the lock. Returns after the fanfare.
     */
    suspend fun awardXp(
        amount: Int,
        bonusGold: Int = 0,
        comboMult: Double = 1.0,
        /** True when this award accompanies a completed medium/big quest — bumps lifetime STR. */
        bigQuest: Boolean = false,
    ) {
        val xpBuff = equippedBuff(com.addhd.app.campaign.BuffKind.XP_GENERATION)
        val goldBuff = equippedBuff(com.addhd.app.campaign.BuffKind.GOLD_GENERATION)
        // The bard's commission: a timed, bought-with-gold XP multiplier. Read outside the lock
        // (like the artifact buffs) since it's a plain timestamp comparison.
        val bardMult = if ((db.strongholdDao().get()?.bardExpiresAtMillis ?: 0L) > now()) {
            BARD_XP_MULT
        } else {
            1.0
        }
        var leveledUp = false
        xpMutex.withLock {
            val p = petDao.get() ?: PetEntity()
            val t = today()
            val potionMult = if (_xpPotionPrimed.value) { _xpPotionPrimed.value = false; POTION_XP_MULT } else 1.0
            // Momentum: settle any idle-day decay, then credit today. The multiplier now comes
            // from a number the hero actually earns, instead of from how long ago the clean streak
            // started — which climbed on its own and could never be lost.
            val settled = effectiveMomentum(p.momentum, p.momentumDay, t)
            val momentum = if (p.momentumDay == t) {
                settled                                   // already credited today
            } else {
                (settled + MOMENTUM_GAIN_PER_ACTIVE_DAY).coerceAtMost(MOMENTUM_MAX)
            }
            val mult = momentumMultiplier(momentum)
            val tierMult = WorldTier.scalar(p.worldTier)
            var xp = p.xp +
                Math.round(amount * mult * tierMult * (1.0 + xpBuff) * potionMult * comboMult * bardMult).toInt()
            var level = p.level
            while (xp >= level * 100) { xp -= level * 100; level++ }
            leveledUp = level > p.level
            val streak = when {
                p.lastFedEpochDay == t -> p.streakDays
                p.lastFedEpochDay == t - 1 -> p.streakDays + 1
                else -> 1
            }
            val gold = p.gold + goldFor(amount, p.worldTier, goldBuff) + bonusGold
            petDao.upsert(
                p.copy(
                    xp = xp, level = level, lastFedEpochDay = t, streakDays = streak, gold = gold,
                    totalBigQuestsCompleted = p.totalBigQuestsCompleted + if (bigQuest) 1 else 0,
                    momentum = momentum, momentumDay = t,
                )
            )
        }
        if (leveledUp) {
            com.addhd.app.util.Sound.play(ctx, com.addhd.app.util.Sfx.SFX_LEVEL_UP)
            com.addhd.app.util.Haptics.boardClear(ctx)
        }
    }

    /**
     * Hero combat stats (STR/DEX/CON/WIS) recomputed from the same tables the VM's heroStats flow
     * reads, PLUS equipped-gear bonuses — so a widget-driven win rolls the identical damage/crit the
     * app would. Kept here (rather than reusing the VM flow) so the engine is self-contained and can
     * run with no VM alive, e.g. from the widget's background callback.
     */
    private suspend fun combatStatsNow(p: PetEntity): HeroStats {
        val dopa = db.dopamineDao().all().first()
        // Lifetime counter, NOT a live count of the tasks table — completed quests purge after 30
        // days, which used to make STR shrink as history aged out.
        val str = p.totalBigQuestsCompleted
        val dex = db.focusDao().completedCountNow()
        val con = cleanDays(p.cleanStreakStartEpochDay, today())
        val wis = dopa.count { it.rodeItOut }
        var bStr = 0; var bDex = 0; var bCon = 0; var bWis = 0
        lootDao.allNow().filter { it.equipped }.forEach { li ->
            LootTable.def(li.defId)?.let { d ->
                when (d.stat) {
                    com.addhd.app.campaign.LootStat.STR -> bStr += d.bonus
                    com.addhd.app.campaign.LootStat.DEX -> bDex += d.bonus
                    com.addhd.app.campaign.LootStat.CON -> bCon += d.bonus
                    com.addhd.app.campaign.LootStat.WIS -> bWis += d.bonus
                }
            }
        }
        return HeroStats(str + bStr, dex + bDex, con + bCon, wis + bWis)
    }

    /** Combat encounter + loot roll (moved verbatim from AppViewModel.emitCombatForWin). */
    suspend fun emitCombatForWin(baseXp: Int, energy: Int, minor: Boolean = false) {
        val p = petDao.get() ?: PetEntity()
        val stats = combatStatsNow(p)
        val hero = HeroClass.from(p.heroClass)

        val roomBeforeWin = Campaign.roomInQuest(p.roomsCleared)
        val isBossRoom = roomBeforeWin >= Campaign.TOTAL_ROOMS
        val questIdx = Campaign.questIndex(p.roomsCleared)
        val quest = Campaign.currentQuest(p.roomsCleared)

        val seed = Campaign.totalLifetimeXp(p.xp, p.level) + energy
        val crit = (seed * 7) % 100 < stats.critChancePct
        val dmg = (stats.bossDamage + energy * 3).let { if (crit) it * 2 else it }
        val enemy = if (isBossRoom) quest.boss else Campaign.enemyFor(seed, p.roomsCleared)

        // ---- The boss actually has HP now ----
        // Previously arriving at room 10 meant the next completed task killed the boss outright,
        // so STR (boss damage) and WIS (crit) were computed and then thrown away. The climax was
        // indistinguishable from clearing room 3. Now the damage lands on something.
        var bossFelled = false
        var bossHpAfter = 0
        var bossMax = 0
        if (isBossRoom) {
            bossMax = quest.bossMaxHp
            // Arm the encounter on arrival, or on moving to a different quest's boss.
            val armed = if (p.bossQuestIndex == questIdx && p.bossHpRemaining > 0) {
                p.bossHpRemaining
            } else {
                bossMax
            }
            bossHpAfter = (armed - dmg).coerceAtLeast(0)
            bossFelled = bossHpAfter <= 0
            petDao.upsert(
                petDao.get()?.copy(
                    bossQuestIndex = questIdx,
                    // Clear the encounter on a kill so the next quest's boss arms fresh.
                    bossHpRemaining = if (bossFelled) 0 else bossHpAfter,
                    // Felling the boss is what clears room 10 and moves the campaign on.
                    roomsCleared = if (bossFelled) p.roomsCleared + 1 else p.roomsCleared,
                ) ?: return
            )
            // Felling the room-10 boss completes the dungeon quest, and THAT is what advances the
            // campaign chapter. Ten rooms -> boss -> +1 chapter -> (when the gate fills) the
            // chapter boss.
            if (bossFelled) creditCampaignProgress()
        } else {
            // Rooms 1-9: a win clears a room — a quest, a No-Brainer, or a focus block, i.e.
            // every path that reaches this encounter. Read from a counter now rather than
            // inferred from the XP total.
            petDao.upsert(petDao.get()?.copy(roomsCleared = p.roomsCleared + 1) ?: return)
        }

        val baseLine = if (minor) Campaign.minorCombatLine(seed, enemy, dmg, baseXp)
        else Campaign.combatLine(hero, enemy, dmg, baseXp)
        val line = buildString {
            if (crit) {
                // A named effect plus an authored line, instead of a bare "CRIT!" every time.
                append(com.addhd.app.campaign.CombatFlavor.pick(CombatFlavor.criticalEffects, seed))
                append("!  ")
            }
            append(baseLine)
            if (crit) {
                append("  ")
                append(CombatFlavor.pick(CombatFlavor.criticalHit, seed / 3))
            }
            if (isBossRoom) {
                if (bossFelled) {
                    append("  ")
                    append(CombatFlavor.pick(CombatFlavor.victory, seed))
                } else {
                    // Say what the damage DID, then let the boss answer — thirteen silent
                    // subtractions from a progress bar is not a fight.
                    append("  $enemy: $bossHpAfter/$bossMax HP left.")
                    // No article: half these bosses are proper names, and "The Acererak" reads
                    // as a mistake. "Acererak reaches out…" works for both.
                    append("  $enemy ")
                    append(CombatFlavor.legendaryAction(enemy, seed + bossHpAfter))
                    append(".")
                }
            } else if (!minor && (seed % 5) == 0) {
                // Occasional kill prose on rank-and-file, so rooms 1-9 aren't one template either.
                append("  ")
                append(CombatFlavor.pick(CombatFlavor.kill, seed / 7))
            }
        }

        // Loot drops on the KILL, not merely on reaching the boss room, or every swing at a
        // multi-hit boss would pay out.
        val dropsLoot = bossFelled || kotlin.random.Random.nextInt(100) < TASK_LOOT_DROP_CHANCE_PCT
        if (dropsLoot) {
            val owned = lootDao.allNow().map { it.defId }.toSet()
            val drop = LootTable.rollDrop(Campaign.lootTierFor(questIdx), seed + lootDao.count(), owned)
            lootDao.insert(LootEntity(defId = drop.id, acquiredAtMillis = now()))
            com.addhd.app.util.Haptics.diceCrit(ctx)
            _combat.tryEmit(
                AppViewModel.CombatEvent(
                    line = if (bossFelled) "$line  $enemy falls! You claim ${drop.emoji} ${drop.name}!"
                    else "$line  Loot! ${drop.emoji} ${drop.name}",
                    crit = crit,
                    bossDefeated = bossFelled,
                    lootName = drop.name,
                    lootEmoji = drop.emoji,
                )
            )
        } else {
            _combat.tryEmit(AppViewModel.CombatEvent(line = line, crit = crit))
        }
    }

    /** Campaign-gate credit (moved verbatim from AppViewModel.creditCampaignProgress). */
    suspend fun creditCampaignProgress() {
        campaignMutex.withLock {
            val active = campaignDao.campaigns().first().firstOrNull { it.active && !it.completed }
                ?: return@withLock
            val current = campaignDao.firstOpenMilestone(active.campaignKey) ?: return@withLock
            // Gate counts DUNGEON QUESTS now, not tasks — this is only called when a room-10 boss
            // falls. See [Campaign.chapterGateInQuests].
            val gate = WorldTier.effectiveGate(
                WorldTier.chapterGateInQuests(current.tasksRequired),
                petDao.get()?.worldTier ?: 1,
                gateReductionNow(),
            )
            if (current.tasksDone >= gate) return@withLock
            val bumped = (current.tasksDone + 1).coerceAtMost(gate)
            val justWoke = bumped >= gate &&
                BossType.fromContent(current.bossType) != BossType.STANDARD &&
                current.bossAnchorMillis == 0L
            campaignDao.updateMilestone(
                current.copy(
                    tasksDone = bumped,
                    bossAnchorMillis = if (justWoke) now() else current.bossAnchorMillis,
                )
            )
        }
    }

    /** Typed-boss damage interceptor (moved verbatim from AppViewModel.damageTypedBoss). */
    suspend fun damageTypedBoss(forType: BossType, cap: Int) {
        campaignMutex.withLock {
            val active = campaignDao.campaigns().first().firstOrNull { it.active && !it.completed }
                ?: return@withLock
            val current = campaignDao.firstOpenMilestone(active.campaignKey) ?: return@withLock
            if (BossType.fromContent(current.bossType) != forType) return@withLock
            if (current.bossAnchorMillis == 0L) return@withLock
            if (current.bossProgress >= cap) return@withLock
            campaignDao.updateMilestone(current.copy(bossProgress = current.bossProgress + 1))
            com.addhd.app.util.Sound.play(ctx, com.addhd.app.util.Sfx.SFX_BOSS_HIT)
        }
    }

    companion object {
        // ---- Reward economy constants (moved from AppViewModel.Companion) ----
        const val GOLD_PER_XP = 0.5
        const val MIN_GOLD_PER_AWARD = 1
        const val POTION_XP_MULT = 2.0
        const val TASK_LOOT_DROP_CHANCE_PCT = 30
        const val BOSS_CLEAR_XP = 20

        fun cleanDays(startEpochDay: Long, todayEpochDay: Long): Int =
            if (startEpochDay <= 0L) 0 else (todayEpochDay - startEpochDay).coerceAtLeast(0).toInt()

        fun streakMultiplier(cleanDays: Int): Float =
            1.0f + (cleanDays.coerceIn(0, 15) * 0.01f)

        // ---- Momentum ----
        /** Ceiling, which is also the max XP bonus percentage. */
        const val MOMENTUM_MAX = 15
        /** Gained per day on which the hero earns anything at all. */
        const val MOMENTUM_GAIN_PER_ACTIVE_DAY = 1
        /** LOST per fully idle day. Deliberately harsher than the gain: ground is easier to lose. */
        const val MOMENTUM_LOSS_PER_IDLE_DAY = 2

        /**
         * Momentum as of [today], after charging for any idle days since [lastActiveDay].
         *
         * Today itself is never counted as idle — you still have the whole day to act, and docking
         * someone at 00:01 for a day they haven't lived yet would be both wrong and dispiriting.
         * So the decay covers only whole days that have already passed unused.
         */
        fun effectiveMomentum(stored: Int, lastActiveDay: Long, today: Long): Int {
            if (lastActiveDay <= 0L) return stored.coerceIn(0, MOMENTUM_MAX)
            val idleDays = (today - lastActiveDay - 1).coerceAtLeast(0)
            return (stored - idleDays.toInt() * MOMENTUM_LOSS_PER_IDLE_DAY).coerceIn(0, MOMENTUM_MAX)
        }

        /** +1% XP per point of momentum, so the cap pays +15%. */
        fun momentumMultiplier(momentum: Int): Float =
            1.0f + (momentum.coerceIn(0, MOMENTUM_MAX) * 0.01f)

        /** How much momentum a fully idle day from now would cost — the stake, shown in the UI. */
        fun momentumAtRisk(current: Int): Int =
            minOf(current, MOMENTUM_LOSS_PER_IDLE_DAY)

        // ---- The bard's commission ----
        /**
         * XP multiplier while a commissioned bard is playing.
         *
         * Sized to be felt on the very next completion — which is the whole point, and exactly
         * what the inn it replaced failed at. +50% on a 25-XP quest is visible immediately,
         * whereas the inn's +2% needed thousands of quests to repay its own price.
         */
        const val BARD_XP_MULT = 1.5

        @Volatile private var INSTANCE: RewardEngine? = null

        /** Process-wide singleton. Both the VM and the widget callback resolve THIS instance. */
        fun get(context: Context): RewardEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RewardEngine(context.applicationContext).also { INSTANCE = it }
            }
    }
}
