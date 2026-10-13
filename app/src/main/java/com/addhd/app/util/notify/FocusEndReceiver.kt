package com.addhd.app.util.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires the "block complete" notification at the exact moment a focus block ends, even if the app
 * has been frozen by Doze for the whole session.
 *
 * It deliberately does NOT credit the block. Crediting is owned solely by the ViewModel, gated on
 * the persisted end-time anchor so it happens exactly once; adding a second writer here would
 * risk a session being paid out twice. The ViewModel records the block against its true end time,
 * so a late credit doesn't distort the time-of-day insights either — meaning the only thing
 * actually missing while frozen was the user being TOLD, which is all this does.
 */
class FocusEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FOCUS_END) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        if (minutes <= 0) return
        val appCtx = context.applicationContext
        Notifier.ensureChannels(appCtx)
        Notifier.showFocusComplete(appCtx, minutes)
        // The countdown in the shade is now stale — the block is over.
        FocusTimerService.stop(appCtx)
    }

    companion object {
        const val ACTION_FOCUS_END = "com.addhd.app.FOCUS_END"
        const val EXTRA_MINUTES = "minutes"
    }
}
