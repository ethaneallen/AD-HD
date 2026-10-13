package com.addhd.app.util.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms and are cleared on reboot, so we re-arm everything once the device finishes booting:
 * recreate channels, re-register the daily nudge workers, and re-schedule every upcoming
 * time-block alarm from the DB. Uses goAsync() to keep the receiver alive across the DB read.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Notifier.ensureChannels(context)
        ReminderScheduler.ensureDailyWork(context)

        // A focus block armed before the reboot lost its end-of-block alarm with everything else.
        // The block itself survives (its end time is persisted and it's credited on next launch),
        // but without re-arming this the hero would never be told it finished.
        val prefs = context.getSharedPreferences(Notifier.PREFS, Context.MODE_PRIVATE)
        val endAt = prefs.getLong(KEY_FOCUS_END_AT, 0L)
        val minutes = prefs.getInt(KEY_FOCUS_MINUTES, 0)
        if (endAt > System.currentTimeMillis() && minutes > 0) {
            ReminderScheduler.scheduleFocusEnd(context, endAt, minutes)
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAllTimeBlocks(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        // Mirrors AppViewModel's focus-anchor keys. Duplicated rather than shared because this
        // receiver must work with no ViewModel in scope (same reasoning as Notifier's pref keys).
        private const val KEY_FOCUS_END_AT = "focus_end_at"
        private const val KEY_FOCUS_MINUTES = "focus_minutes"
    }
}
