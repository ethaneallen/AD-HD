package com.addhd.app.util.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.addhd.app.data.AppDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Owns all reminder scheduling: to-the-minute time-block alarms via [AlarmManager] and the two
 * recurring daily nudges via [WorkManager]. Static/object so receivers and the ViewModel can both
 * reach it without an instance.
 *
 * Time-block precision: we ask for an exact alarm; if the OS won't grant exact-alarm access
 * (canScheduleExactAlarms() == false on some API 31+ OEM builds), we fall back to an inexact
 * window alarm rather than crashing — the reminder still fires, just ±a few minutes.
 */
object ReminderScheduler {
    // Fixed nudge hours (24h). Chosen as sensible defaults; a future build can make these settings.
    const val MORNING_HOUR = 9   // 9:00 AM — Rule-of-Three kickoff
    const val EVENING_HOUR = 20  // 8:00 PM — streak-at-risk check

    private const val WORK_MORNING = "morning_kickoff_work"
    private const val WORK_EVENING = "evening_streak_work"

    // Fixed request code: there is only ever one focus block armed at a time.
    private const val FOCUS_END_REQUEST = 90_001

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ---- Time-block alarms ----------------------------------------------------------------

    /**
     * How long BEFORE a time block the heads-up fires. A reminder that arrives exactly at the
     * start time is already too late to act on: switching tasks takes a run-up, and being yanked
     * mid-activity with no warning is where the plan usually falls apart. Five minutes is enough
     * to find a stopping point rather than abandon one.
     */
    const val RUNWAY_MIN = 5

    /** How far "Snooze" pushes a time block when tapped from the notification. */
    const val SNOOZE_MIN = 10

    /**
     * Each task owns two alarms: the heads-up and the start itself. They're told apart by intent
     * ACTION rather than by request code — PendingIntent identity uses Intent.filterEquals, which
     * compares the action, so the two never overwrite each other despite sharing a request code.
     */
    private fun blockPendingIntent(
        context: Context,
        taskId: Long,
        title: String,
        action: String,
    ): PendingIntent {
        val intent = Intent(context, TimeBlockReceiver::class.java).apply {
            this.action = action
            putExtra(TimeBlockReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TimeBlockReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Schedule (or replace) BOTH reminders for one time-blocked task: the [RUNWAY_MIN] heads-up
     * and the on-the-minute start. Either is skipped individually if its moment has already
     * passed, so a block scheduled two minutes out still gets its start alarm.
     */
    fun scheduleTimeBlock(context: Context, taskId: Long, title: String, epochDay: Long, startMin: Int) {
        val when_ = LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.of(startMin / 60, startMin % 60))
        val startAt = when_.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleBlockAlarmsAt(context, taskId, title, startAt)
    }

    /** Arm the heads-up + start alarms around an absolute start time. Shared with snooze. */
    fun scheduleBlockAlarmsAt(context: Context, taskId: Long, title: String, startAtMillis: Long) {
        val now = System.currentTimeMillis()
        val runwayAt = startAtMillis - RUNWAY_MIN * 60_000L
        if (runwayAt > now) {
            setAlarm(context, runwayAt, blockPendingIntent(context, taskId, title, TimeBlockReceiver.ACTION_SOON))
        }
        if (startAtMillis > now) {
            setAlarm(context, startAtMillis, blockPendingIntent(context, taskId, title, TimeBlockReceiver.ACTION_START))
        }
    }

    private fun setAlarm(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = alarmManager(context)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Graceful fallback: an inexact window so the reminder still lands, just not to the minute.
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pi)
        }
    }

    /** Cancel BOTH of a task's reminders (task completed, deleted, or unscheduled). */
    fun cancelTimeBlock(context: Context, taskId: Long) {
        val am = alarmManager(context)
        am.cancel(blockPendingIntent(context, taskId, "", TimeBlockReceiver.ACTION_START))
        am.cancel(blockPendingIntent(context, taskId, "", TimeBlockReceiver.ACTION_SOON))
    }

    /** Push a task's block [SNOOZE_MIN] minutes out from now (from the notification's Snooze action). */
    fun snoozeTimeBlock(context: Context, taskId: Long, title: String) {
        cancelTimeBlock(context, taskId)
        scheduleBlockAlarmsAt(context, taskId, title, System.currentTimeMillis() + SNOOZE_MIN * 60_000L)
    }

    // ---- Focus block end (doze backstop) ---------------------------------------------------

    private fun focusEndPendingIntent(context: Context, minutes: Int): PendingIntent {
        val intent = Intent(context, FocusEndReceiver::class.java).apply {
            action = FocusEndReceiver.ACTION_FOCUS_END
            putExtra(FocusEndReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context, FOCUS_END_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Fire a wakeup at the moment a focus block ends.
     *
     * The countdown itself doesn't need this — it's derived from a persisted wall-clock anchor and
     * is re-credited on next launch regardless. The NOTIFICATION does: it was posted by the ticker
     * coroutine, so if the OS froze the app (phone face-down for 45 minutes is precisely the Doze
     * case this app encourages) the block would end in silence and the hero would never be told.
     * An alarm is the only thing that reliably runs while the process is idle.
     */
    fun scheduleFocusEnd(context: Context, endAtMillis: Long, minutes: Int) {
        if (endAtMillis <= System.currentTimeMillis()) return
        setAlarm(context, endAtMillis, focusEndPendingIntent(context, minutes))
    }

    fun cancelFocusEnd(context: Context, minutes: Int = 0) {
        alarmManager(context).cancel(focusEndPendingIntent(context, minutes))
    }

    /** True when the OS will honour to-the-minute alarms. Surfaced in Settings. */
    fun canScheduleExact(context: Context): Boolean = alarmManager(context).canScheduleExactAlarms()

    /**
     * Re-arm every upcoming time-block alarm from scratch. Called on app start and after a reboot
     * (alarms don't survive either). Reads directly from the DB so it works from a BroadcastReceiver.
     */
    suspend fun rescheduleAllTimeBlocks(context: Context) {
        val today = LocalDate.now().toEpochDay()
        val blocks = AppDatabase.get(context).taskDao().scheduledBlocksFrom(today)
        blocks.forEach { t ->
            val start = t.blockStartMin ?: return@forEach
            val day = t.blockDayEpochDay ?: return@forEach
            scheduleTimeBlock(context, t.id, t.title, day, start)
        }
    }

    // ---- Recurring daily nudges (WorkManager) ---------------------------------------------

    /** Register both daily workers. Idempotent (KEEP policy) so repeated calls don't reset the clock. */
    fun ensureDailyWork(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK_MORNING,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MorningWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayToHour(MORNING_HOUR), TimeUnit.MILLISECONDS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            WORK_EVENING,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<EveningWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayToHour(EVENING_HOUR), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /** Milliseconds from now until the next occurrence of [hour]:00 local time. */
    private fun initialDelayToHour(hour: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val zone = ZoneId.systemDefault()
        return next.atZone(zone).toInstant().toEpochMilli() - now.atZone(zone).toInstant().toEpochMilli()
    }
}
