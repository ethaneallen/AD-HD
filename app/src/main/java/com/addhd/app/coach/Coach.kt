package com.addhd.app.coach

/**
 * The local "smart coach" that powers the hero's voice. It's rule-based, not an LLM:
 * it reads a snapshot of the user's real state and returns a context-aware nudge,
 * a hero mood, and (when relevant) a suggested action. Everything runs offline.
 *
 * Design notes for the ADHD use case:
 * - Never shame. Low output gets gentle, tiny-step suggestions, not guilt.
 * - Dopamine-aware: if the user reports low dopamine / an urge, steer toward a
 *   healthy boost (move your body, splash water, 2-min tidy) and normalize the dip.
 * - Momentum: celebrate streaks and any completed thing, however small.
 */
data class CoachSnapshot(
    val hour: Int,                 // 0..23 local hour (passed in; no clock in this pure layer)
    val tasksDoneToday: Int,
    val tasksOpen: Int,
    val focusMinutesToday: Int,
    val habitsDueToday: Int,
    val habitsDoneToday: Int,
    val lastMood: Int?,            // 1..5
    val lastEnergy: Int?,          // 1..5
    val petStreakDays: Int,
    val petLevel: Int,
    val dopamineRidesToday: Int,   // urges ridden out today
    val minutesSinceLastWin: Long? // null if unknown
)

enum class PetMood { THRIVING, HAPPY, CONTENT, SLEEPY, WILTING }

data class CoachResult(
    val petMood: PetMood,
    val headline: String,
    val nudge: String,
    val suggestedAction: SuggestedAction?,
)

enum class SuggestedAction(val label: String, val route: String) {
    DOPAMINE_SOS("Seek Refuge in the Sanctuary", "sos"),   // enum name kept; display label rebranded
    START_FOCUS("Start a 25-min focus", "focus"),
    TINY_TASK("Strike a Minor Blow", "tasks"),
    CHECK_MOOD("Check your Status", "wellbeing"),
    JOURNAL("Brain dump", "journal"),
    BRAIN_GAME("Roll the dice", "fidget"),
}

object Coach {

    /** Deterministic pick so the same snapshot doesn't flicker between recompositions,
     *  but different states surface different lines. */
    private fun <T> List<T>.pick(seed: Int): T = this[(seed % size + size) % size]

    fun evaluate(s: CoachSnapshot): CoachResult {
        val seed = s.tasksDoneToday * 7 + s.focusMinutesToday + s.hour + s.habitsDoneToday * 3

        val mood = petMood(s)

        // Highest-priority intervention: a reported low mood/energy -> dopamine steering.
        val lowEnergy = (s.lastEnergy ?: 3) <= 2
        val lowMood = (s.lastMood ?: 3) <= 2
        val nothingYet = s.tasksDoneToday == 0 && s.focusMinutesToday == 0 && s.habitsDoneToday == 0

        if (lowEnergy || lowMood) {
            return CoachResult(
                petMood = mood,
                headline = headlineLowDopamine.pick(seed),
                nudge = nudgeLowDopamine.pick(seed),
                // Low mood/energy — steer to the Sanctuary, whose "Invoke a Blessing" menu holds the
                // real movement/reset micro-actions (Move for 5 min, Step outside, Cold water…).
                suggestedAction = SuggestedAction.DOPAMINE_SOS,
            )
        }

        if (nothingYet && s.hour >= 10) {
            return CoachResult(
                petMood = mood,
                headline = headlineStuck.pick(seed),
                nudge = nudgeStuck.pick(seed),
                suggestedAction = SuggestedAction.TINY_TASK,
            )
        }

        if (s.tasksOpen > 0 && s.focusMinutesToday == 0 && s.hour in 8..20) {
            return CoachResult(
                petMood = mood,
                headline = headlineFocus.pick(seed),
                nudge = nudgeFocus.pick(seed),
                suggestedAction = SuggestedAction.START_FOCUS,
            )
        }

        if (s.habitsDueToday > s.habitsDoneToday && s.hour >= 12) {
            return CoachResult(
                petMood = mood,
                headline = "Habit nudge",
                nudge = nudgeHabit.pick(seed),
                suggestedAction = null,
            )
        }

        if (s.dopamineRidesToday > 0) {
            return CoachResult(
                petMood = mood,
                headline = "You rode it out 🌊",
                nudge = nudgeRodeUrge.pick(seed),
                suggestedAction = null,
            )
        }

        // Doing well — celebrate + keep light.
        return CoachResult(
            petMood = mood,
            headline = headlineThriving.pick(seed),
            nudge = nudgeThriving.pick(seed),
            suggestedAction = if (s.hour >= 20) SuggestedAction.JOURNAL else null,
        )
    }

    private fun petMood(s: CoachSnapshot): PetMood {
        val wins = s.tasksDoneToday + s.habitsDoneToday + (s.focusMinutesToday / 25)
        return when {
            s.petStreakDays >= 3 && wins >= 3 -> PetMood.THRIVING
            wins >= 2 -> PetMood.HAPPY
            wins == 1 -> PetMood.CONTENT
            s.hour >= 22 || s.hour < 6 -> PetMood.SLEEPY
            else -> PetMood.WILTING
        }
    }

    fun petFace(mood: PetMood): String = when (mood) {
        PetMood.THRIVING -> "🔥"   // roaring hearth — hero at full might
        PetMood.HAPPY -> "⚔️"      // blade ready
        PetMood.CONTENT -> "🛡️"    // holding the line
        PetMood.SLEEPY -> "🕯️"     // candle burning low
        PetMood.WILTING -> "💀"    // resolve fading
    }

    // ---- Nudge libraries (ADHD-affirming, shame-free) ----

    private val headlineLowDopamine = listOf(
        "Shields battered? Rest, warrior.",
        "Resolve Wavers 🌫️",
        "The Spirit Falters",
    )
    private val nudgeLowDopamine = listOf(
        "A drained spirit is a vulnerability, not a failure. Even the greatest heroes must rest and sharpen their blade before the next strike.",
        "The urge to stray from the path is a test of endurance. Answer it with action: move your body, step into the sun, or shock yourself back to the present.",
        "The fog of fatigue is transient. Hold the line for a few short moments—this mental siege will lift. Are you ready to steel your resolve?",
    )
    private val headlineStuck = listOf("Can't start? Shrink it.", "Let's find the door.", "One tiny thing.")
    private val nudgeStuck = listOf(
        "Executive dysfunction isn't cowardice. Pick the smallest possible version of one quest — 2 minutes counts. The campaign advances while you do it.",
        "Don't plan the whole day. Just name one 2-minute task and do only that. Momentum is easier to find than motivation.",
        "The hardest part is starting. Choose a tiny task and start a 5-minute timer. You can stop when it dings.",
    )
    private val headlineFocus = listOf("Ready to lock in?", "Let's block out the noise.", "Focus fuel available.")
    private val nudgeFocus = listOf(
        "You've got open tasks — a 25-minute focus block with me watching could knock one out. I level up when you finish.",
        "Try a Pomodoro: 25 on, 5 off. Phone face-down. I'll celebrate at the end.",
        "One focused block beats a whole distracted afternoon. Want to start?",
    )
    private val nudgeHabit = listOf(
        "A daily contract still stands. Streaks fuel your resolve 🔥 — even a half-effort check-in keeps the fire lit.",
        "Don't break the chain! Quick habit check when you get a sec.",
    )
    private val nudgeRodeUrge = listOf(
        "You felt the pull and chose differently. That's the whole skill. I'm proud of you.",
        "Every urge you ride out makes the next one quieter. Nice work.",
    )
    private val headlineThriving = listOf("Look at you go 🌟", "We're thriving.", "Momentum!")
    private val nudgeThriving = listOf(
        "The party's marching well today. Keep it light — you don't have to earn rest.",
        "Great rhythm. Remember to drink water and stretch between wins.",
        "You're doing the thing. Proud of you — let's coast a bit.",
    )
}
