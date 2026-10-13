package com.addhd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.ui.theme.Radii

/**
 * The Clean Streak tracker. Leans on loss aversion (a number you don't want to zero)
 * while keeping the relapse reset explicitly shame-free.
 */
@Composable
fun StreakScreen(
    cleanDays: Int,
    multiplier: Float,
    elixirCount: Int = 0,     // Elixirs of Willpower owned — will auto-absorb the next reset
    streakFreezeCharges: Int = 0,   // Chrono-Anchor Streak-Freeze charges remaining
    hasChronoAnchor: Boolean = false,
    onReset: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    var justReset by remember { mutableStateOf(false) }
    // When a reset is buffered by an Elixir, the streak is preserved — flag it so the confirm
    // copy and the follow-up message reflect "your streak held" instead of "back to Day 1".
    val willBuffer = elixirCount > 0
    // Captured at the moment of reset (before the elixirCount prop reactively drops), so the
    // post-reset dialog shows the right outcome even after the Elixir is consumed.
    var bufferedAtReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Your streak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.card),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🛡️", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.decorative())
                Spacer(Modifier.height(8.dp))
                Text(
                    if (cleanDays == 0) "Day 1" else "$cleanDays ${if (cleanDays == 1) "day" else "days"} strong",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You know what this number means. Keep it climbing.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        SectionCard {
            Text("Streak perk", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            val bonusPct = ((multiplier - 1f) * 100).toInt()
            Text(
                "You're earning +$bonusPct% XP right now thanks to your streak. " +
                    "It climbs +1% every clean day, capped at +15% — so protecting this number levels your hero faster.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Streak protection status — only shown once the Chrono-Anchor relic is owned. It auto-guards
        // a planned day off, so surfacing the remaining charges reassures the hero their streak is
        // safe (non-punitive protection) without any manual action.
        if (hasChronoAnchor) {
            SectionCard {
                Text("🛡️ Streak-Freeze", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (streakFreezeCharges > 0)
                        "The Chrono-Anchor holds $streakFreezeCharges charge${if (streakFreezeCharges == 1) "" else "s"}. Take a planned day off and one will auto-guard your streak — no action needed."
                    else
                        "The Chrono-Anchor is spent for now. Its charges refill automatically each month to guard your next planned day off.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (streakFreezeCharges > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (streakFreezeCharges > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard {
            Text("Slipped up?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "It happens. Resetting the clock doesn't erase what you've learned — your hero, XP, and level all stay. This just starts a fresh count.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (willBuffer) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🫙 You're carrying $elixirCount Elixir${if (elixirCount == 1) "" else "s"} of Willpower — reset now and one will hold the line, keeping your $cleanDays-day streak.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            // A soft-filled TONAL button (not a bright primary): it reads clearly as a real,
            // tappable control instead of a hollow outline, but deliberately stays quiet — reset is
            // a shame-free-yet-undesirable action, so the loud teal CTA is reserved for the streak
            // card above (the true visual anchor). Don't promote resetting to the cheerful primary.
            FilledTonalButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(Radii.button),
            ) { Text(if (willBuffer) "Reset — Elixir will protect it" else "Reset my streak") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Start Day 1 right now?") },
            text = {
                Text("Resetting the clock doesn't reset your progress. No shame — just a clean slate. Ready?")
            },
            confirmButton = {
                TextButton(onClick = { bufferedAtReset = willBuffer; onReset(); confirmReset = false; justReset = true }) {
                    Text(if (willBuffer) "Use Elixir & reset 🫙" else "Start fresh ⚔️")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Never mind") }
            },
        )
    }

    if (justReset) {
        AlertDialog(
            onDismissRequest = { justReset = false },
            icon = { Text(if (bufferedAtReset) "🫙" else "⚔️", style = MaterialTheme.typography.headlineMedium) },
            title = { Text(if (bufferedAtReset) "Your streak held!" else "Clock reset to Day 1") },
            text = {
                Text(
                    if (bufferedAtReset)
                        "An Elixir of Willpower shattered to absorb the slip — your clean streak is intact. Nothing lost."
                    else "The progress you made doesn't disappear. Let's build the next streak starting right now."
                )
            },
            confirmButton = {
                TextButton(onClick = { justReset = false }) { Text("Let's go") }
            },
        )
    }
}
