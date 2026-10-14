package com.addhd.app.campaign

/**
 * The D&D campaign domain layer. Maps the existing "pet" data onto RPG concepts without
 * touching the database schema:
 *   - PetEntity.xp / .level  -> character XP & level (same thresholds as before)
 *   - task/habit/focus wins   -> combat encounters that deal damage & grant XP
 *   - cumulative day progress -> "rooms cleared" toward the boss of the current quest
 */

enum class HeroClass(val id: String, val display: String, val emoji: String, val weaponVerb: String, val blurb: String) {
    BARBARIAN("BARBARIAN", "Barbarian", "🪓", "cleaves", "Raw power. Built to smash through anything in the way."),
    ROGUE("ROGUE", "Rogue", "🗡️", "backstabs", "Fast and precise. Turns small wins into big crits."),
    WIZARD("WIZARD", "Wizard", "🪄", "blasts", "Clever and strategic. Bends the day to your will.");

    companion object {
        fun from(id: String): HeroClass? = entries.firstOrNull { it.id == id }
    }
}

/** The four behavior-driven stats. All derived from existing tables — no stored columns. */
data class HeroStats(
    val str: Int,  // Medium/Big tasks completed — boss damage
    val dex: Int,  // Focus blocks completed — dodge / focus resilience
    val con: Int,  // Clean-streak days — HP to survive slips
    val wis: Int,  // Urges ridden in SOS — crit chance (double XP)
) {
    val maxHp: Int get() = 20 + con * 5
    val critChancePct: Int get() = (wis * 3).coerceAtMost(50)
    val bossDamage: Int get() = 5 + str * 2
    val dodgePct: Int get() = (dex * 2).coerceAtMost(40)
}

/** The class's main-stat attack modifier added to a boss-fight d20 roll, plus its label. */
data class AttackMod(val value: Int, val statLabel: String)

/**
 * Boss-fight attack modifier: each class swings off its signature stat (Barbarian STR, Rogue DEX,
 * Wizard INT≈WIS here), plus a small level bonus so progression is felt. Halved so it nudges the
 * odds without trivializing the AC-10 gate. Capped to keep it grounded.
 */
fun attackModifier(heroClass: HeroClass?, stats: HeroStats, level: Int): AttackMod {
    val (raw, label) = when (heroClass) {
        HeroClass.BARBARIAN -> stats.str to "STR"
        HeroClass.ROGUE -> stats.dex to "DEX"
        HeroClass.WIZARD -> stats.wis to "INT"
        null -> stats.str to "STR"
    }
    val mod = (raw / 2 + level / 2).coerceIn(0, 8)
    return AttackMod(mod, label)
}

/** Quests, in order. Each has a name and a boss; rooms-per-quest is fixed at TOTAL_ROOMS. */
data class Quest(val name: String, val boss: String)

/**
 * New Game Plus / World Tier scaling. Tier 1 is the base game and is deliberately UNSCALED
 * (multiplier 1.0) — scaling grows with (tier - 1) so a fresh save never feels inflated. Each
 * tier above 1 adds [STEP] (15%) to both the boss task-gate and the XP rewards, so difficulty
 * and payoff rise together and the loop stays satisfying instead of grindy.
 */
object WorldTier {
    /** Per-tier increment applied above tier 1 (0.15 = +15% each). */
    const val STEP = 0.15

    /** Reward/difficulty scalar for [tier]. Tier 1 -> 1.0, tier 2 -> 1.15, tier 3 -> 1.30, … */
    fun scalar(tier: Int): Double = 1.0 + (tier.coerceAtLeast(1) - 1) * STEP

    /**
     * Boss task-gate for the given tier: the base milestone requirement scaled up and rounded
     * up (ceil) so higher tiers always demand at least one more task, never fewer. Floored at
     * the base value so a malformed tier can't make a chapter trivially short.
     */
    fun scaledTaskGate(baseRequired: Int, tier: Int): Int =
        Math.ceil(baseRequired * scalar(tier)).toInt().coerceAtLeast(baseRequired)

    /**
     * A chapter's gate expressed in DUNGEON QUESTS rather than individual tasks.
     *
     * The two progressions are now nested: ten rooms plus a felled room-boss completes a dungeon
     * quest, and that is what advances the chapter. Before this they ran in parallel — one
     * completed task credited a room AND a chapter tick independently — which is why Home could
     * show two unrelated bosses demanding attention at once.
     *
     * `tasksRequired` is authored in tasks (every chapter in campaigns.json says 5), so it is
     * converted at [Campaign.TOTAL_ROOMS] tasks per dungeon quest and floored at one. Keeping the authored
     * number as the gate would have made a chapter cost fifty rooms instead of ten, turning a
     * 36-chapter campaign set into several thousand completed quests.
     */
    fun chapterGateInQuests(tasksRequired: Int): Int =
        ((tasksRequired + Campaign.TOTAL_ROOMS - 1) / Campaign.TOTAL_ROOMS).coerceAtLeast(1)

    /**
     * THE single source of truth for a chapter's boss gate. Applies World Tier scaling FIRST,
     * then subtracts the shop's GATE_REDUCTION artifact buff, and floors the result at 1 so a
     * gate can never drop to zero (which would auto-open the boss with no work). Every read site
     * — the reactive activeQuest projection AND the three campaign write paths — must call this so
     * the number the UI shows and the number the engine enforces are computed identically and can
     * never drift.
     */
    fun effectiveGate(baseRequired: Int, tier: Int, gateReduction: Int): Int =
        (scaledTaskGate(baseRequired, tier) - gateReduction.coerceAtLeast(0)).coerceAtLeast(1)

    /** XP/gold reward scaled for the tier, rounded to the nearest whole point. */
    fun scaledReward(baseXp: Int, tier: Int): Int =
        Math.round(baseXp * scalar(tier)).toInt()

    /** Human label for the Character sheet & celebration banner ("World Tier 2 · NG+1"). */
    fun label(tier: Int): String {
        val t = tier.coerceAtLeast(1)
        return if (t <= 1) "World Tier 1" else "World Tier $t · NG+${t - 1}"
    }
}

object Campaign {

    const val TOTAL_ROOMS = 10

    /**
     * 25 quests drawn from the author's own D&D campaigns — see [Bestiary]. Each carries its own
     * boss (with real, halved 5e HP) and its own enemy pool, replacing four quest names and ten
     * enemy names that looped every 40 rooms.
     */
    private val quests: List<Bestiary.RoomQuest> get() = Bestiary.quests

    /**
     * Rooms cleared, from the stored per-quest counter.
     *
     * This used to be `lifetimeXP / 20`, which made "Room 7/10" and the XP bar the same number in
     * two different costumes. [com.addhd.app.data.PetEntity.roomsCleared] now counts completed
     * quests, so a room is something you actually did.
     */
    fun roomsClearedTotal(roomsCleared: Int): Int = roomsCleared.coerceAtLeast(0)

    /** Rebuild lifetime XP from current level + partial xp using the same thresholds (level*100). */
    fun totalLifetimeXp(xp: Int, level: Int): Int {
        var total = xp
        for (l in 1 until level) total += l * 100
        return total
    }

    val questCount: Int get() = quests.size

    /** Which dungeon (0-based) the hero is currently in — also used to pick the loot pool. */
    fun questIndex(roomsCleared: Int): Int =
        (roomsClearedTotal(roomsCleared) / TOTAL_ROOMS) % quests.size

    fun currentQuest(roomsCleared: Int): Bestiary.RoomQuest = quests[questIndex(roomsCleared)]

    /** Room within the current quest, 1..TOTAL_ROOMS. */
    fun roomInQuest(roomsCleared: Int): Int {
        val cleared = roomsClearedTotal(roomsCleared) % TOTAL_ROOMS
        return (cleared + 1).coerceAtMost(TOTAL_ROOMS)
    }

    /**
     * Which loot pool a quest draws from.
     *
     * [Bestiary] holds 25 quests laid out as five difficulty tiers of five settings, while
     * [LootTable] has four pools. Passing the raw quest index would clamp every quest past the
     * fourth to the same pool, freezing loot quality for 21 of the 25. Grouping by tier keeps gear
     * escalating alongside the bestiary instead.
     */
    fun lootTierFor(questIndex: Int): Int = questIndex / 5

    /** Rank-and-file drawn from the CURRENT quest's own pool, so the bestiary tracks the setting. */
    fun enemyFor(seed: Int, roomsCleared: Int): String {
        val pool = currentQuest(roomsCleared).enemies.ifEmpty { listOf("Goblin") }
        return pool[(seed % pool.size + pool.size) % pool.size]
    }

    /** Build the combat-log line shown when a task is completed. */
    fun combatLine(heroClass: HeroClass?, enemy: String, damage: Int, xp: Int): String {
        val cls = heroClass?.display ?: "Hero"
        val verb = heroClass?.weaponVerb ?: "strikes"
        return "Your $cls $verb the $enemy for $damage damage!  +$xp XP"
    }

    /** Scrappy improvised attacks for tiny "No-Brainer" wins — still part of the fight. */
    private val minorMoves = listOf(
        "hurl a rock at", "throw a quick jab at", "kick dirt at",
        "swing a torch at", "fling a dagger at", "shoulder-check",
    )

    fun minorCombatLine(seed: Int, enemy: String, damage: Int, xp: Int): String {
        val move = minorMoves[(seed % minorMoves.size + minorMoves.size) % minorMoves.size]
        return "You $move the $enemy for $damage damage!  +$xp XP"
    }

    // ---- Gritty flavor for the Home hero card. No wellness tone. ----

    private val lvl1Lines = listOf(
        "%s is restive. Sharpen your blade.",
        "The tavern is quiet. Steel yourself for the road ahead.",
        "Fresh out of the guildhall. The dungeon won't clear itself.",
        "Your gear is untested. Prove it in the dark.",
    )
    private val midDungeonLines = listOf(
        "You smell sulfur ahead. Keep your shield raised.",
        "The shadows are moving. Watch your step.",
        "Something skitters in the dark. Press on.",
        "Torchlight flickers. The next room won't wait.",
        "Blood on the stones — not yours yet. Advance.",
    )
    private val bossLines = listOf(
        "The beast is near. Steel your resolve.",
        "%s waits in the dark. Finish this.",
        "The air goes cold. The warlord knows you're here.",
    )

    data class Flavor(val headline: String, val line: String)

    /**
     * Dynamic hero-card flavor by class/level/room. `seed` (e.g. lifetime XP + hour) keeps a
     * given state stable but varies across states. Replaces the old wellness copy.
     */
    fun flavorFor(className: String, roomsCleared: Int, level: Int, seed: Int): Flavor {
        val room = roomInQuest(roomsCleared)
        val quest = currentQuest(roomsCleared)
        return when {
            room >= TOTAL_ROOMS -> {
                val l = bossLines[(seed % bossLines.size + bossLines.size) % bossLines.size]
                Flavor("⚔️ Warlord ahead", l.format(quest.boss))
            }
            level <= 1 && roomsClearedTotal(roomsCleared) == 0 -> {
                val l = lvl1Lines[(seed % lvl1Lines.size + lvl1Lines.size) % lvl1Lines.size]
                Flavor("The campaign begins", l.format(className))
            }
            else -> {
                val l = midDungeonLines[(seed % midDungeonLines.size + midDungeonLines.size) % midDungeonLines.size]
                Flavor(quest.name, l)
            }
        }
    }
}
