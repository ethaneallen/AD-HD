package com.addhd.app.campaign

/**
 * Combat prose, taken from the author's own D&D game (`C:/webapp/dnd-game-web`:
 * DRAMATIC_DESCRIPTIONS, CRITICAL_HIT_EFFECTS, SIGNATURE_LEGENDARY_ACTIONS).
 *
 * Why: the combat line is the single most-read sentence in the app — it fires on every completed
 * quest — and it had exactly ONE template. By the second week it is wallpaper: the reward still
 * fires but it stops landing, which for an ADHD audience matters more than most, because novelty
 * is the reinforcer.
 *
 * Two deliberate omissions from the source data:
 *  - `fumble` and `nearDeath` lines are NOT used. They narrate the hero failing or dying, and
 *    nothing in this app may punish the user for completing a task.
 *  - Legendary actions are FLAVOUR ONLY. The boss gets menace; it never deals damage, imposes a
 *    penalty, or heals. A boss that undid your progress would be the same mistake as a streak
 *    that resets.
 */
object CombatFlavor {

    /** Spoken when a crit lands. Replaces a bare "CRIT!" prefix. */
    val criticalHit = listOf(
        "With supernatural precision, your blow finds its mark!",
        "Time seems to slow as your weapon arcs through the air, striking true!",
        "The Fates themselves guide your hand in this moment!",
        "A perfect strike! Your enemy reels from the devastating blow!",
        "Your attack is a thing of deadly beauty, striking with unerring accuracy!",
    )

    /** Named crit effects, for a short tag alongside the line. */
    val criticalEffects = listOf(
        "Devastating Blow", "Stunning Strike", "Disarming Strike",
        "Precise Hit", "Bleeding Wound", "Crushing Impact",
    )

    /** Spoken when anything dies — rank-and-file or boss. */
    val kill = listOf(
        "With a final, desperate cry, your foe crumples to the ground.",
        "The light fades from your enemy's eyes as they fall.",
        "Victory! Your opponent is vanquished!",
        "The creature's threat ends with your decisive blow.",
        "Your enemy falls, never to rise again.",
    )

    /** Spoken when a room-10 boss is felled. */
    val victory = listOf(
        "The battle is won! You stand triumphant!",
        "Against all odds, you have prevailed!",
        "Your enemies lie defeated before you!",
        "Glory! The day is yours!",
        "The sweet taste of victory fills your soul!",
    )

    /**
     * What a boss does between your blows — pure theatre, no mechanical effect.
     *
     * Keyed by archetype so the right creature does the right thing; [archetypeFor] maps a boss's
     * name onto one of these. Without it, a 13-exchange fight against Acererak was 13 silent
     * subtractions from a progress bar.
     */
    private val legendaryActions: Map<String, List<String>> = mapOf(
        "dragon" to listOf(
            "sweeps its massive tail",
            "beats its wings, buffeting you with gale-force wind",
            "scans the battlefield with predatory eyes",
            "fixes you with a terrible draconic glare",
        ),
        "lich" to listOf(
            "reaches out with a withered, glowing hand",
            "turns its hollow gaze upon you",
            "siphons your vitality through the air",
            "unleashes a wave of necrotic force",
        ),
        "vampire" to listOf(
            "lunges with gleaming fangs",
            "meets your eyes with hypnotic intensity",
            "melts into mist and repositions",
            "rakes you with unnatural speed",
        ),
        "demon" to listOf(
            "tears at you with abyssal claws",
            "vanishes and reappears at an angle",
            "reveals its true abyssal form",
            "flings a gout of hellfire",
        ),
        "giant" to listOf(
            "swings its weapon in a wide arc",
            "raises a tree-trunk leg and stomps down",
            "hurls a massive rock",
        ),
        "aberration" to listOf(
            "lashes out with a writhing tentacle",
            "assaults your thoughts directly",
            "bends the space around it",
        ),
        "beast" to listOf(
            "circles, hackles raised",
            "snarls and snaps at your heels",
            "lunges low, going for the legs",
        ),
        "humanoid" to listOf(
            "presses the attack with practised footwork",
            "shouts an order and gives ground",
            "feints, then comes again",
        ),
    )

    /** Best-guess archetype for a boss name, falling back to a competent humanoid. */
    fun archetypeFor(boss: String): String {
        val n = boss.lowercase()
        return when {
            listOf("dragon", "wyvern", "drake", "rex", "saurus", "raptor", "triceratops").any { it in n } -> "dragon"
            listOf("lich", "acererak", "wraith", "specter", "skeleton", "zombie", "wight", "ghast",
                   "ghoul", "revenant", "banshee", "atropal", "bodak", "mormesk").any { it in n } -> "lich"
            listOf("vampire", "strahd", "spawn", "nosferatu").any { it in n } -> "vampire"
            listOf("demon", "devil", "chimera", "nycaloth", "shadow", "hag", "slaad", "yeenoghu").any { it in n } -> "demon"
            listOf("giant", "troll", "ogre", "ettin", "minotaur", "golem", "behemoth", "froghemoth",
                   "sasquatch").any { it in n } -> "giant"
            listOf("beholder", "xanathar", "aberration", "mind", "flail snail", "soulmonger",
                   "mantrap", "assassin vine").any { it in n } -> "aberration"
            listOf("wolf", "bear", "spider", "boar", "bat", "owlbear", "girallon", "allosaurus",
                   "crocodile", "snake", "ape", "baboon", "warg").any { it in n } -> "beast"
            else -> "humanoid"
        }
    }

    /** A boss's riposte for this exchange. Deterministic in [seed] so it is resume-safe. */
    fun legendaryAction(boss: String, seed: Int): String {
        val pool = legendaryActions[archetypeFor(boss)] ?: return "presses the attack"
        return pool[(seed % pool.size + pool.size) % pool.size]
    }

    /** Index-safe pick from any of the line pools above. */
    fun pick(pool: List<String>, seed: Int): String =
        if (pool.isEmpty()) "" else pool[(seed % pool.size + pool.size) % pool.size]
}
