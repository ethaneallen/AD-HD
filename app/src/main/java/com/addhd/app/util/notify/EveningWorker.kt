package com.addhd.app.util.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.addhd.app.data.AppDatabase
import java.time.LocalDate

/**
 * ~8 PM daily: warn if the productivity streak is about to break at midnight. Fires only when ALL:
 *   • there's a live streak to lose (streakDays > 0),
 *   • no quest has been completed today (lastFedEpochDay != today), and
 *   • no Chrono-Anchor Streak-Freeze charge is available to auto-cover the gap.
 * The freeze check is the point of the "before midnight" heads-up — if the hero is protected, we
 * stay quiet rather than nagging.
 */
class EveningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notifier.ensureChannels(applicationContext)
        if (!Notifier.typeEnabled(applicationContext, Notifier.KEY_STREAK)) return Result.success()

        val db = AppDatabase.get(applicationContext)
        val pet = db.petDao().get() ?: return Result.success()
        val today = LocalDate.now().toEpochDay()

        val hasStreak = pet.streakDays > 0
        val doneToday = pet.lastFedEpochDay == today
        val protectedByFreeze = (db.strongholdDao().get()?.chronoCharges ?: 0) > 0

        if (hasStreak && !doneToday && !protectedByFreeze) {
            Notifier.showStreakRisk(applicationContext, pet.streakDays)
        }
        return Result.success()
    }
}
