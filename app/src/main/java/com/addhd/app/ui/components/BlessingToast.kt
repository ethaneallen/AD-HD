package com.addhd.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addhd.app.vm.AppViewModel.BlessingReward

/**
 * Transient "reward acquired" toast for the Sanctuary's Invoke-a-Blessing flow. Slides in from the
 * top and auto-dismisses (timing owned by the caller, mirroring [CombatOverlay]/PurchaseToast).
 *
 * Unlike the static DoneCard badge, this fires the INSTANT the reward lands — driven by the VM's
 * replay=1 `blessing` flow — so a drop is never silent. It states the icon, name, and rarity tier
 * for gear ("Loot Acquired: Dragonfang Greatsword · Epic"), or the explicit boon for gold/XP.
 * Haptics are fired by the caller as the toast is shown (see SosScreen).
 */
@Composable
fun BlessingToast(reward: BlessingReward?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = reward != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val r = reward ?: return@AnimatedVisibility
            val accent = accentFor(r)
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(icon(r), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            heading(r),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            title(r),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun icon(r: BlessingReward): String = when (r.kind) {
    BlessingReward.Kind.GEAR -> r.lootEmoji ?: "🎁"
    BlessingReward.Kind.GOLD -> "🪙"
    BlessingReward.Kind.XP_BOON -> "✨"
}

private fun heading(r: BlessingReward): String = when (r.kind) {
    BlessingReward.Kind.GEAR -> "LOOT ACQUIRED"
    BlessingReward.Kind.GOLD -> "GOLD GRANTED"
    BlessingReward.Kind.XP_BOON -> "BOON GRANTED"
}

private fun title(r: BlessingReward): String = when (r.kind) {
    BlessingReward.Kind.GEAR ->
        "${r.lootName ?: "Gear"}${r.rarity?.let { " · $it" } ?: ""}"
    BlessingReward.Kind.GOLD -> "+${r.gold} Gold  ·  +${r.xp} XP"
    BlessingReward.Kind.XP_BOON -> "+${r.xp} XP  ·  a jolt of resolve"
}

/** Rarity-tinted accent for the heading; boons use the theme secondary/tertiary tones. */
@Composable
private fun accentFor(r: BlessingReward): Color = when (r.kind) {
    BlessingReward.Kind.GEAR -> when (r.rarity?.lowercase()) {
        "common" -> Color(0xFFB0B7BF)
        "uncommon" -> Color(0xFF5FC46B)
        "rare" -> Color(0xFF4EA3F5)
        "epic" -> Color(0xFFB86BE0)
        "legendary" -> Color(0xFFF5A623)
        else -> MaterialTheme.colorScheme.tertiary
    }
    BlessingReward.Kind.GOLD -> Color(0xFFF5A623)
    BlessingReward.Kind.XP_BOON -> MaterialTheme.colorScheme.tertiary
}
