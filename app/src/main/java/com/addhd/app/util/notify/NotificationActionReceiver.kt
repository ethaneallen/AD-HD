package com.addhd.app.util.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.addhd.app.data.AppDatabase
import com.addhd.app.vm.RewardEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the buttons on a time-block notification so a reminder can be ACTED on where it is
 * read, instead of only opening the app.
 *
 * This matters more than it sounds: the gap between "see the reminder" and "have the app open on
 * the right screen" is exactly where an intention evaporates. Completing or deferring from the
 * shade keeps the whole interaction to one tap.
 *
 * Runs with no Activity and no ViewModel in scope, so completion goes through the shared,
 * process-wide [RewardEngine] — the same instance (and the same mutexes and combo counter) that
 * the app and the home-screen widget use, so a notification tap can't double-credit against an
 * in-app tap.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val appCtx = context.applicationContext

        // Dismiss immediately: the user has answered this reminder either way, and leaving it
        // sitting in the shade after acting on it is its own small annoyance.
        if (notifId >= 0) NotificationManagerCompat.from(appCtx).cancel(notifId)

        when (intent.action) {
            ACTION_SNOOZE -> ReminderScheduler.snoozeTimeBlock(appCtx, taskId, title)

            ACTION_COMPLETE -> {
                // Outlive onReceive: the reward cascade touches the DB. goAsync() keeps the
                // process alive until finish() is called.
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val task = AppDatabase.get(appCtx).taskDao().getById(taskId)
                        // Re-read rather than trusting the notification: it may be minutes old and
                        // the quest already cleared in-app. Awarding twice would inflate XP.
                        if (task != null && !task.done) {
                            ReminderScheduler.cancelTimeBlock(appCtx, taskId)
                            RewardEngine.get(appCtx).completeTask(task)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.addhd.app.NOTIF_COMPLETE"
        const val ACTION_SNOOZE = "com.addhd.app.NOTIF_SNOOZE"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_NOTIF_ID = "notifId"
    }
}
