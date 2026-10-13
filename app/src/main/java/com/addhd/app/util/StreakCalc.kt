package com.addhd.app.util

/**
 * Dead-simple habit streak: a plain count of CONSECUTIVE checked days, computed from the
 * set of completed-day epochDays (no stored streak column — history is the source of truth).
 *
 * Rules (no grace days, no hidden buffers):
 *  - Count starts at today if today is checked, otherwise at yesterday (today's box is just
 *    not ticked yet, so the tally from prior days still shows).
 *  - Walk backward one day at a time. Each checked day adds 1. The first unchecked day stops
 *    the count.
 *  - Miss a full day (so neither today nor yesterday is checked) and the streak is 0.
 *
 * Examples (today = D):
 *  - checked {D}                  -> 1  (checked today)
 *  - checked {D-1}, D not ticked  -> 1  (yesterday counts; tick today to make it 2)
 *  - checked {D, D-1, D-2}        -> 3
 *  - checked {D, D-2} (missed D-1)-> 1  (gap stops the count immediately)
 *  - checked {D-2} (missed D-1)   -> 0  (a full day passed unchecked)
 */
object StreakCalc {

    data class Result(val streak: Int, val doneToday: Boolean)

    fun compute(checkedDays: Set<Long>, today: Long): Result {
        val doneToday = checkedDays.contains(today)

        // Anchor at today if ticked, else yesterday (today may still be checked later today).
        var cursor = if (doneToday) today else today - 1

        var streak = 0
        while (checkedDays.contains(cursor)) {
            streak++
            cursor--
        }
        return Result(streak, doneToday)
    }
}
