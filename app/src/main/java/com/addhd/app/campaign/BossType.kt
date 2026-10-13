package com.addhd.app.campaign

/**
 * A chapter boss's mechanical demand — what the hero must do (beyond charging the task gate) to
 * land the killing blow. STANDARD is the default and preserves the original task-count-only clear.
 *
 * Authored per-milestone in campaigns.json and stored as a plain String on MilestoneEntity (Room-
 * native, no TypeConverter — content stays out of the DB, mapped by name at the use site, same as
 * loot/shop items). Parsed leniently so unknown or missing values fall back to STANDARD: old
 * content and typos never crash the import.
 */
enum class BossType {
    STANDARD,        // task count only — original behavior
    IRON_BEHEMOTH,   // K completed Focus Sessions since the boss woke
    HYDRA,           // maintain an M-day clean streak, unbroken since the encounter began
    SHADOW_SPECTER;  // J time-blocked tasks (blockStartMin != null) completed since the boss woke

    companion object {
        /** Lenient parse for JSON content — case-insensitive, unknown/blank → STANDARD. */
        fun fromContent(raw: String?): BossType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: STANDARD

        // Typed-demand thresholds. PLACEHOLDER values for the display pass — the combat layer will
        // read these same constants when it computes/enforces bossProgress, so the UI denominator
        // and the eventual enforcement can't drift. Retune here in one place.
        const val IRON_BEHEMOTH_SESSIONS = 1   // K: Focus Sessions to break the armor (tuned down for testing/casual)
        const val SHADOW_SPECTER_TIMED = 1     // J: time-blocked tasks to banish the phantom (tuned down for testing/casual)
    }
}
