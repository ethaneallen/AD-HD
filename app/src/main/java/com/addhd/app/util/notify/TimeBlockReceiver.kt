package com.addhd.app.util.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires a time-block reminder. Each block arms TWO alarms, told apart by intent action:
 * [ACTION_SOON] a few minutes ahead (the transition runway) and [ACTION_START] on the minute.
 */
class TimeBlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        if (taskId < 0) return
        Notifier.ensureChannels(context)
        when (intent.action) {
            ACTION_SOON -> Notifier.showTimeBlockSoon(context, taskId, title)
            else -> Notifier.showTimeBlock(context, taskId, title)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TITLE = "title"

        /** The block starts now. */
        const val ACTION_START = "com.addhd.app.TIME_BLOCK"

        /** The block starts in [ReminderScheduler.RUNWAY_MIN] minutes — time to wrap up. */
        const val ACTION_SOON = "com.addhd.app.TIME_BLOCK_SOON"
    }
}
