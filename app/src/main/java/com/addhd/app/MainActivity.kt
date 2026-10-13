package com.addhd.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.addhd.app.ui.nav.AppNav
import com.addhd.app.ui.theme.AddhdTheme
import com.addhd.app.util.notify.Notifier

class MainActivity : ComponentActivity() {

    // Set true when the app is launched by the widget's Quick-Capture button (see QuestWidget). Read
    // once by AppNav to auto-open the voice quick-capture overlay, then cleared so it fires only once.
    private val openQuickCapture = mutableStateOf(false)
    // Registered before onCreate returns; result is ignored (posting is guarded either way) — the
    // ask is what matters. Declining just means notifications silently no-op until re-granted.
    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Channels must exist before any notification posts; cheap + idempotent.
        Notifier.ensureChannels(this)
        maybeRequestNotifPermission()
        openQuickCapture.value = intent?.getBooleanExtra(EXTRA_QUICK_CAPTURE, false) == true
        enableEdgeToEdge()
        setContent {
            AddhdTheme {
                AppNav(openQuickCaptureOnce = openQuickCapture)
            }
        }
    }

    // Warm launch (app already running) from the widget's Quick-Capture button: onNewIntent, not
    // onCreate, delivers the flag, so re-raise it here for AppNav's LaunchedEffect to consume.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QUICK_CAPTURE, false)) openQuickCapture.value = true
    }

    companion object {
        /** Intent extra the widget sets to route straight to voice quick-capture on open. */
        const val EXTRA_QUICK_CAPTURE = "com.addhd.app.OPEN_QUICK_CAPTURE"
    }

    /** API 33+ gates notifications behind a runtime grant. Ask once on launch if not yet decided. */
    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
