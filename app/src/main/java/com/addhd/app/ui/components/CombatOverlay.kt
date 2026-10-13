package com.addhd.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addhd.app.vm.AppViewModel

/**
 * Transient combat-log banner shown when a task is completed. Slides in at the top,
 * auto-dismisses. Room clears (old quest, room 10) also announce the loot drop.
 */
@Composable
fun CombatOverlay(event: AppViewModel.CombatEvent?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = event != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val e = event ?: return@AnimatedVisibility
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (e.crit) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.inverseSurface,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        // "bossDefeated" here = the old room-quest room-10 clear, NOT the chapter
                        // boss fight. Worded as a skirmish/room clear so only the deliberate dice
                        // fight uses "boss" language.
                        if (e.bossDefeated) "🏆 ROOM CLEARED" else "⚔️ Skirmish",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        e.line,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    if (e.bossDefeated && e.lootName != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Loot drop: ${e.lootEmoji ?: "🎁"} ${e.lootName}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
        }
    }
}
