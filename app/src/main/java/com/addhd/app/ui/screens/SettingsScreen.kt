package com.addhd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.ui.components.SectionCard

/**
 * One OS-capability readout. Shows a plain state plus a route to the system screen that changes
 * it — never nags, and the copy always says what still works when the answer is "no".
 */
@Composable
private fun ReliabilityRow(
    ok: Boolean,
    title: String,
    good: String,
    bad: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            if (ok) "✅" else "⚠️",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (ok) good else bad,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ok) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** One label + switch row in the Notifications card. Dimmed and inert when [enabled] is false. */
@Composable
private fun NotifRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Switch(checked = checked && enabled, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(
    onResetAll: () -> Unit,
    soundEnabled: Boolean = true,
    onSoundEnabledChange: (Boolean) -> Unit = {},
    reduceMotion: Boolean = false,
    onReduceMotionChange: (Boolean) -> Unit = {},
    // Notification prefs: a master switch plus three per-type toggles. Per-type rows are disabled
    // (greyed) while the master is off so the hierarchy is obvious.
    notifMaster: Boolean = true,
    onNotifMasterChange: (Boolean) -> Unit = {},
    notifTimeBlock: Boolean = true,
    onNotifTimeBlockChange: (Boolean) -> Unit = {},
    notifStreak: Boolean = true,
    onNotifStreakChange: (Boolean) -> Unit = {},
    notifMorning: Boolean = true,
    onNotifMorningChange: (Boolean) -> Unit = {},
) {
    var confirm by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sound effects", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tavern clinks, dice, and victory stings on actions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = soundEnabled, onCheckedChange = onSoundEnabledChange)
            }
            // Audition the kit without having to win something. Every effect is synthesized at
            // runtime rather than shipped as a file, so being able to hear it on the actual device
            // speaker is the only way to judge it.
            if (soundEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Win" to com.addhd.app.util.Sfx.SFX_QUICK_WIN,
                        "Level up" to com.addhd.app.util.Sfx.SFX_LEVEL_UP,
                        "Boss hit" to com.addhd.app.util.Sfx.SFX_BOSS_HIT,
                        "Dice" to com.addhd.app.util.Sfx.SFX_DICE_ROLL,
                    ).forEach { (label, sfx) ->
                        androidx.compose.material3.OutlinedButton(
                            onClick = { com.addhd.app.util.Sound.play(ctx, sfx) },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 4.dp, vertical = 8.dp,
                            ),
                            colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                            border = com.addhd.app.ui.components.cardOutlinedBorder(),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ----- Reliability -----
        // Two OS settings decide whether a long focus block and a to-the-minute reminder actually
        // work, and both are invisible from inside the app. A block is credited regardless (its end
        // time is persisted), but being TOLD it finished depends on an alarm the system may refuse
        // to honour. Surfaced as plain status with a direct route to the right screen.
        SectionCard {
            Text("Reliability", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            val exactOk = com.addhd.app.util.notify.ReminderScheduler.canScheduleExact(ctx)
            ReliabilityRow(
                ok = exactOk,
                title = "To-the-minute alarms",
                good = "Reminders and focus-block endings fire exactly on time.",
                bad = "Android is batching our alarms, so reminders may land minutes late.",
                actionLabel = "Allow exact alarms",
                onAction = {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${ctx.packageName}"),
                            ),
                        )
                    }
                },
            )

            val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
            Spacer(Modifier.height(10.dp))
            ReliabilityRow(
                ok = unrestricted,
                title = "Background freedom",
                good = "Long focus blocks keep their live countdown while the phone is locked.",
                bad = "Battery optimisation may freeze the countdown on a long block. " +
                    "Your minutes are still banked either way.",
                actionLabel = "Open battery settings",
                onAction = {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                            ),
                        )
                    }
                },
            )
        }

        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reduce motion", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Hold the looping animations still — the bobbing hero, the breathing " +
                            "focus familiar, the Sanctuary sigil. Rewards and transitions still play.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = reduceMotion, onCheckedChange = onReduceMotionChange)
            }
        }

        SectionCard {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "Gentle nudges so the app reaches out when it matters — out of sight shouldn't mean out of mind.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            NotifRow(
                title = "Enable notifications",
                subtitle = "Master switch for all reminders below.",
                checked = notifMaster,
                onChange = onNotifMasterChange,
            )
            NotifRow(
                title = "⏰ Time-block reminders",
                subtitle = "Alerts when a scheduled quest's block begins.",
                checked = notifTimeBlock,
                enabled = notifMaster,
                onChange = onNotifTimeBlockChange,
            )
            NotifRow(
                title = "🔥 Streak warnings",
                subtitle = "Evening heads-up before your streak breaks at midnight.",
                checked = notifStreak,
                enabled = notifMaster,
                onChange = onNotifStreakChange,
            )
            NotifRow(
                title = "☀️ Morning kickoff",
                subtitle = "A 9am nudge with your Rule of Three for the day.",
                checked = notifMorning,
                enabled = notifMaster,
                onChange = onNotifMorningChange,
            )
        }

        SectionCard {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "AD&HD keeps everything on this device — no account, no internet, no tracking. Your data never leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
            Text(
                "Wipe everything and start completely fresh: all tasks, habits, mood logs, " +
                    "journal entries, focus history, XP, level, and your streak. Your hero returns to " +
                    "Level 1 and the clock resets to Day 0. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Clear All App Data & Reset") }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Erase everything?") },
            text = {
                Text(
                    "This permanently deletes all your data and resets your hero to a fresh install " +
                        "(Level 1, Day 0). There's no undo. Are you sure?"
                )
            },
            confirmButton = {
                TextButton(onClick = { onResetAll(); confirm = false; done = true }) {
                    Text("Erase & start fresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            },
        )
    }

    if (done) {
        AlertDialog(
            onDismissRequest = { done = false },
            icon = { Text("⚔️", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative()) },
            title = { Text("Fresh start") },
            text = { Text("All cleared. Your hero is Level 1 and your streak begins today. Make it count.") },
            confirmButton = { TextButton(onClick = { done = false }) { Text("Let's go") } },
        )
    }
}
