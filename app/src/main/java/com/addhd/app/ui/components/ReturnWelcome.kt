package com.addhd.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.ui.theme.Radii
import com.addhd.app.vm.AppViewModel

/**
 * Shown when the hero returns after a gap of [AppViewModel.LAPSE_DAYS] or more.
 *
 * This is the single highest-stakes screen in the app and it previously didn't exist. Use of a tool
 * like this is bursty — weeks on, weeks off — so a return is the NORMAL case, not a failure. What
 * used to greet it was a dead daily streak, a quest log that had quietly purged itself, and no
 * acknowledgement of any kind: a reliable "why bother" moment.
 *
 * Three deliberate rules here:
 *  - **Never mention the gap as a loss.** The day count is stated plainly, with no "you missed…".
 *  - **Lead with what survived.** Lifetime totals are the proof the time wasn't wasted, which is
 *    exactly what a lapse makes someone doubt. This is why [AppViewModel.AllTime] had to exist.
 *  - **Exactly one way forward.** A returning brain does not need a dashboard; it needs one small
 *    named quest, already picked.
 */
@Composable
fun ReturnWelcomeDialog(
    welcome: AppViewModel.ReturnWelcome?,
    onStartSuggestion: (com.addhd.app.data.TaskEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    if (welcome == null) return
    val stats = welcome.allTime

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🔥", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative()) },
        title = {
            Text(
                "The hearth is still lit",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    if (welcome.daysAway >= 30) {
                        "It's been a while — ${welcome.daysAway} days. The tavern kept your seat."
                    } else {
                        "${welcome.daysAway} days away. Nothing's lost; picking it back up is the whole skill."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (stats.hasHistory) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "What you've already banked",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatLine("⚔️", "${stats.questsCompleted}", "quests cleared, all time")
                    StatLine("⏳", focusLabel(stats.focusMinutes), "focused")
                    StatLine("🧙", "Level ${stats.level}", "and it stays yours")
                }

                welcome.suggestion?.let { t ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "One small thing to restart on:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            val suggestion = welcome.suggestion
            if (suggestion != null) {
                Button(
                    onClick = { onStartSuggestion(suggestion) },
                    shape = RoundedCornerShape(Radii.button),
                ) { Text("Start there") }
            } else {
                Button(onClick = onDismiss, shape = RoundedCornerShape(Radii.button)) {
                    Text("Back to it")
                }
            }
        },
        dismissButton = {
            if (welcome.suggestion != null) {
                TextButton(onClick = onDismiss) { Text("Not that") }
            }
        },
    )
}

/** "2h 20m" / "45m" — hours only once there are hours, so small totals don't read as zero. */
internal fun focusLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h <= 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

@Composable
private fun StatLine(emoji: String, value: String, label: String) {
    Row(
        Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
