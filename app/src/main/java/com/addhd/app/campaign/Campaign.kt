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

    private val quests = listOf(
        Quest("Cleanse the Caves", "the Goblin King"),
        Quest("Reclaim the Ruins", "the Bone Warden"),
        Quest("Storm the Spire", "the Frost Lich"),
        Quest("Descend the Depths", "the Shadow Drake"),
    )

    /** Enemies encountered when completing tasks — flavor cycles by index. */
    private val enemies = listOf(
        "Goblin", "Skeleton", "Giant Rat", "Cave Bat", "Kobold",
        "Zombie", "Orc Grunt", "Slime", "Bandit", "Wolf",
    )

    /**
     * Total "rooms cleared" across the whole campaign = one room per win.
     * We derive it from the hero's lifetime XP so it needs no new column: roughly one room
     * per ~20 XP (a typical task). Non-destructive and monotonic.
     */
    fun roomsClearedTotal(xp: Int, level: Int): Int {
        val lifetimeXp = totalLifetimeXp(xp, level)
        return lifetimeXp / 20
    }

    /** Rebuild lifetime XP from current level + partial xp using the same thresholds (level*100). */
    fun totalLifetimeXp(xp: Int, level: Int): Int {
        var total = xp
        for (l in 1 until level) total += l * 100
        return total
    }

    val questCount: Int get() = quests.size

    /** Which dungeon (0-based) the hero is currently in — also used to pick the loot pool. */
    fun questIndex(xp: Int, level: Int): Int =
        (roomsClearedTotal(xp, level) / TOTAL_ROOMS) % quests.size

    fun currentQuest(xp: Int, level: Int): Quest = quests[questIndex(xp, level)]

    /** Room within the current quest, 1..TOTAL_ROOMS. */
    fun roomInQuest(xp: Int, level: Int): Int {
        val cleared = roomsClearedTotal(xp, level) % TOTAL_ROOMS
        return (cleared + 1).coerceAtMost(TOTAL_ROOMS)
    }

    fun enemyFor(seed: Int): String = enemies[(seed % enemies.size + enemies.size) % enemies.size]

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
    fun flavorFor(className: String, xp: Int, level: Int, seed: Int): Flavor {
        val room = roomInQuest(xp, level)
        val quest = currentQuest(xp, level)
        return when {
            room >= TOTAL_ROOMS -> {
                val l = bossLines[(seed % bossLines.size + bossLines.size) % bossLines.size]
                Flavor("⚔️ Warlord ahead", l.format(quest.boss))
            }
            level <= 1 && roomsClearedTotal(xp, level) == 0 -> {
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
