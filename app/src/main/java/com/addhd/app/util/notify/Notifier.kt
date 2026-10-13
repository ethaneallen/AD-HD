package com.addhd.app.util.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.addhd.app.MainActivity
import com.addhd.app.R

/**
 * Central notification surface: owns the channels, the per-type preference gate, and the actual
 * posting. Everything user-facing about notifications flows through here so the scheduling side
 * (AlarmManager receivers, WorkManager workers) never has to know about channels or prefs.
 *
 * Preferences live in the same "tavern_prefs" SharedPreferences the ViewModel uses for sound, so
 * a toggle flipped in Settings is visible here immediately with no wiring. Keys are duplicated as
 * consts (not shared with the VM) intentionally — this object must be usable from a BroadcastReceiver
 * or Worker with no ViewModel in scope.
 */
object Notifier {
    // --- Channels ---
    private const val CH_TIME_BLOCK = "time_block"
    private const val CH_STREAK = "streak_risk"
    private const val CH_MORNING = "morning_kickoff"

    // --- Preference keys (mirrored in AppViewModel; see NotifPrefs) ---
    const val PREFS = "tavern_prefs"
    const val KEY_MASTER = "notif_master"
    const val KEY_TIME_BLOCK = "notif_time_block"
    const val KEY_STREAK = "notif_streak"
    const val KEY_MORNING = "notif_morning"

    /** Stable notification ids. Time-block notifications add the task id on top of this base. */
    const val ID_STREAK = 1001
    const val ID_MORNING = 1002
    const val ID_FOCUS_DONE = 1003
    const val ID_TIME_BLOCK_BASE = 2000
    /** Separate id space so the runway heads-up doesn't replace the start reminder. */
    const val ID_TIME_BLOCK_SOON_BASE = 3000

    /** Create all channels. Idempotent — safe to call on every app start and from receivers. */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(CH_TIME_BLOCK, "Time-block reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a scheduled quest's time block begins."
            },
            NotificationChannel(CH_STREAK, "Streak warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Evening heads-up when your streak is at risk before midnight."
            },
            NotificationChannel(CH_MORNING, "Morning kickoff", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A gentle morning nudge with your Rule of Three for the day."
            },
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    // --- Preference gate ---
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Master switch defaults ON; per-type default ON. A type fires only if master AND its key are on. */
    fun typeEnabled(context: Context, key: String): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_MASTER, true) && p.getBoolean(key, true)
    }

    // --- Posting helpers (each checks its own prefs + POST_NOTIFICATIONS grant) ---

    fun showTimeBlock(context: Context, taskId: Long, title: String) {
        if (!typeEnabled(context, KEY_TIME_BLOCK)) return
        val id = ID_TIME_BLOCK_BASE + taskId.toInt()
        post(
            context,
            id = id,
            channel = CH_TIME_BLOCK,
            title = "⏰ Time to start",
            body = title,
            actions = blockActions(context, id, taskId, title),
        )
    }

    /**
     * The transition runway: fired [ReminderScheduler.RUNWAY_MIN] minutes BEFORE a block so there's
     * time to reach a stopping point. Uses its own notification id so it sits alongside — rather
     * than replaces — the start reminder that follows.
     */
    fun showTimeBlockSoon(context: Context, taskId: Long, title: String) {
        if (!typeEnabled(context, KEY_TIME_BLOCK)) return
        val id = ID_TIME_BLOCK_SOON_BASE + taskId.toInt()
        post(
            context,
            id = id,
            channel = CH_TIME_BLOCK,
            title = "🕰️ Up in ${ReminderScheduler.RUNWAY_MIN} min",
            body = "$title — start finding a stopping point.",
            actions = blockActions(context, id, taskId, title),
        )
    }

    /** Snooze + Complete buttons, so a reminder can be answered without opening the app. */
    private fun blockActions(
        context: Context,
        notifId: Int,
        taskId: Long,
        title: String,
    ): List<NotificationCompat.Action> {
        fun pi(action: String, requestOffset: Int): PendingIntent {
            val i = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
                putExtra(NotificationActionReceiver.EXTRA_TITLE, title)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            }
            return PendingIntent.getBroadcast(
                context, notifId * 4 + requestOffset, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return listOf(
            NotificationCompat.Action.Builder(
                0, "Snooze ${ReminderScheduler.SNOOZE_MIN}m", pi(NotificationActionReceiver.ACTION_SNOOZE, 1),
            ).build(),
            NotificationCompat.Action.Builder(
                0, "Complete", pi(NotificationActionReceiver.ACTION_COMPLETE, 2),
            ).build(),
        )
    }

    fun showStreakRisk(context: Context, streakDays: Int) {
        if (!typeEnabled(context, KEY_STREAK)) return
        post(
            context,
            id = ID_STREAK,
            channel = CH_STREAK,
            title = "🔥 Your $streakDays-day streak ends at midnight",
            body = "Complete one quest — or use a Streak-Freeze — to keep it alive.",
        )
    }

    fun showMorningKickoff(context: Context, topThree: List<String>) {
        if (!typeEnabled(context, KEY_MORNING)) return
        val body = if (topThree.isEmpty()) {
            "A fresh day. Pick one small quest and just start."
        } else {
            "Your Rule of Three:\n" + topThree.joinToString("\n") { "• $it" }
        }
        post(
            context,
            id = ID_MORNING,
            channel = CH_MORNING,
            title = "☀️ Good morning, hero",
            body = body,
            bigText = true,
        )
    }

    /** Fired when a Focus block completes while the app is backgrounded. Not user-gated by type —
     *  it's a direct result of an action the user started, so only the master switch applies. */
    fun showFocusComplete(context: Context, minutes: Int) {
        if (!prefs(context).getBoolean(KEY_MASTER, true)) return
        post(
            context,
            id = ID_FOCUS_DONE,
            channel = CH_TIME_BLOCK,
            title = "🔥 Focus block complete",
            body = "$minutes minutes done. The hearth roars — claim your XP.",
        )
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        bigText: Boolean = false,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        // Tapping any notification opens the app (single-task, no deep link needed yet).
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (bigText) builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        actions.forEach { builder.addAction(it) }

        // On API 33+ posting silently no-ops without the runtime grant; guard so we never crash.
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) { /* permission not granted — nothing to show */ }
    }
}
