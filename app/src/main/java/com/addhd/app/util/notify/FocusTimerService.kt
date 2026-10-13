package com.addhd.app.util.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.addhd.app.MainActivity
import com.addhd.app.R

/**
 * Keeps a live, ticking countdown in the notification shade and on the lock screen for the whole
 * duration of a Focus block.
 *
 * Why a foreground service rather than a plain notification: **time blindness**. Once the phone is
 * face-down the block becomes invisible, and "how long is left?" costs a full context switch back
 * into the app — the exact switch the block exists to prevent. A persistent countdown answers it
 * at a glance and removes the low-grade "is it even still running?" doubt that otherwise pulls you
 * back to the screen every few minutes.
 *
 * The service is deliberately NOT the timer. [com.addhd.app.vm.AppViewModel] still owns the block,
 * anchored to a persisted wall-clock end time; this is a display of that anchor, driven by
 * [Notification.Builder.setUsesChronometer] so the OS renders the countdown itself — no per-second
 * wakeups from us, and the digits stay correct even if the process is frozen. The service merely
 * has to exist for the shade entry to stay put.
 */
class FocusTimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
            }
            else -> {
                val endAt = intent?.getLongExtra(EXTRA_END_AT, 0L) ?: 0L
                val minutes = intent?.getIntExtra(EXTRA_MINUTES, 0) ?: 0
                if (endAt <= System.currentTimeMillis()) {
                    // Nothing left to show (stale restart) — don't strand an empty notification.
                    stopForegroundCompat()
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification(endAt, minutes))
            }
        }
        // Not sticky: if the OS kills us, the ViewModel's persisted anchor is still the source of
        // truth and will re-credit the block. A zombie service restarted with a null intent would
        // only be able to show a wrong countdown.
        return START_NOT_STICKY
    }

    private fun buildNotification(endAtMillis: Long, minutes: Int): Notification {
        ensureChannel(this)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("🔥 Focus block · $minutes min")
            .setContentText("Eyes on the path, hero.")
            .setContentIntent(open)
            // The OS owns the ticking: counts DOWN to the wall-clock end time, so it can't drift
            // and costs us no wakeups.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAtMillis)
            .setOngoing(true)            // not swipe-dismissible while the block is live
            .setOnlyAlertOnce(true)      // silent: the block already started, don't interrupt it
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)   // readable on the lock screen
            .build()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL = "focus_timer"
        private const val NOTIF_ID = 1004
        private const val ACTION_START = "com.addhd.app.FOCUS_TIMER_START"
        private const val ACTION_STOP = "com.addhd.app.FOCUS_TIMER_STOP"
        private const val EXTRA_END_AT = "endAt"
        private const val EXTRA_MINUTES = "minutes"

        /**
         * Its own LOW-importance channel: this is an ambient status readout, not an alert. Giving
         * it the time-block channel's HIGH importance would make every focus block buzz.
         */
        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Focus countdown", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "A live countdown while a focus block is running."
                    setShowBadge(false)
                },
            )
        }

        /** Show (or re-anchor) the countdown for a block ending at [endAtMillis]. */
        fun start(context: Context, endAtMillis: Long, minutes: Int) {
            val i = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_END_AT, endAtMillis)
                putExtra(EXTRA_MINUTES, minutes)
            }
            // A failed status readout must never take down a focus session, so every failure mode
            // here (background-start limits, OEM restrictions) is swallowed — the block itself
            // lives in the ViewModel and is unaffected.
            runCatching { context.startForegroundService(i) }
        }

        /** Tear the countdown down (block finished, paused, or abandoned). */
        fun stop(context: Context) {
            val i = Intent(context, FocusTimerService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(i) }
        }
    }
}
