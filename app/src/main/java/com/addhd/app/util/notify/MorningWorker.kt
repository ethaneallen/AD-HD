package com.addhd.app.util.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.addhd.app.data.AppDatabase

/**
 * ~9 AM daily: a gentle kickoff nudge surfacing the day's Rule of Three — the three lowest-effort
 * open quests. Mirrors the ViewModel's quickStrikeTasks ordering (energy then estimate, top-level
 * only) so the notification and the in-app list agree. Fires even with an empty backlog (a "just
 * pick one small thing" prompt), so the morning ritual is consistent.
 */
class MorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notifier.ensureChannels(applicationContext)
        if (!Notifier.typeEnabled(applicationContext, Notifier.KEY_MORNING)) return Result.success()

        val db = AppDatabase.get(applicationContext)
        // openList() is already: done = 0, parentId IS NULL. Sort matches quickStrikeTasks.
        val topThree = db.taskDao().openList()
            .sortedBy { it.energy * 100 + it.estimateMin }
            .take(3)
            .map { it.title }

        Notifier.showMorningKickoff(applicationContext, topThree)
        return Result.success()
    }
}
