package com.addhd.app.util

import com.addhd.app.data.TaskEntity

/**
 * Low-pressure auto-scheduler with smart sequencing.
 *
 * Rules:
 *  - PINNED quests (user set an exact time) are fixed anchors — they keep their slot exactly.
 *  - FLEXIBLE quests are packed back-to-back: each starts after the previous one's end plus a
 *    small grace buffer, rounded up to the nearest 5 minutes, and are slotted into the gaps
 *    AROUND the pinned anchors so nothing ever overlaps.
 *  - Flexible order front-loads lower-energy tasks (easy wins first) — an ADHD-friendly warm-up.
 */
object Scheduler {

    data class Plan(val taskId: Long, val startMin: Int, val endMin: Int)

    private fun roundUpTo5(min: Int): Int = ((min + 4) / 5) * 5

    fun autoSchedule(
        tasks: List<TaskEntity>,
        dayStartHour: Int = 9,
        dayEndHour: Int = 18,
        breakMin: Int = 5,
    ): List<Plan> {
        val dayStart = dayStartHour * 60
        val dayEnd = dayEndHour * 60

        fun dur(t: TaskEntity) = t.estimateMin.coerceAtLeast(5)

        // Pinned anchors: keep their exact start, build occupied windows to avoid.
        val pinned = tasks.filter { it.blockPinned && it.blockStartMin != null }
            .sortedBy { it.blockStartMin }
        val occupied = pinned.map { it.blockStartMin!! to (it.blockStartMin!! + dur(it)) }.toMutableList()
        val plans = pinned.map { Plan(it.id, it.blockStartMin!!, it.blockStartMin!! + dur(it)) }.toMutableList()

        // Would a block [start, start+len) collide with any occupied window?
        fun overlaps(start: Int, len: Int): Pair<Int, Int>? =
            occupied.firstOrNull { (s, e) -> start < e && (start + len) > s }

        // Flexible quests: easy wins first, then bigger.
        val flexible = tasks.filter { !it.blockPinned }.sortedWith(compareBy({ it.energy }, { it.sort }))
        var cursor = dayStart
        for (t in flexible) {
            val len = dur(t)
            // Advance the cursor past any pinned window it would hit, then re-check (a slot may
            // butt up against several anchors).
            var guard = 0
            while (guard++ < 64) {
                val hit = overlaps(cursor, len) ?: break
                cursor = roundUpTo5(hit.second + breakMin) // jump to just after that anchor
            }
            if (cursor + len > dayEnd) break // out of daylight — leave the rest unscheduled
            plans += Plan(t.id, cursor, cursor + len)
            occupied += cursor to (cursor + len)
            cursor = roundUpTo5(cursor + len + breakMin)
        }
        return plans.sortedBy { it.startMin }
    }

    fun fmt(min: Int): String {
        val h = (min / 60) % 24
        val m = min % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return "%d:%02d %s".format(h12, m, ampm)
    }
}
