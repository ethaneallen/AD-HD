package com.addhd.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.campaign.WorldTier

/**
 * High-impact full-screen celebration shown once when the campaign rotation wraps and the
 * World Tier increases (New Game Plus). Dims the whole app behind a scrim and pops a pulsing
 * banner in the centre. Auto-dismisses (timed in AppNav, mirroring CombatOverlay).
 */
@Composable
fun WorldTierOverlay(newTier: Int?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = newTier != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(320), initialScale = 0.7f),
            exit = fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0.85f),
        ) {
            val tier = newTier ?: return@AnimatedVisibility

            // Gentle pulse on the headline so the banner feels alive while it's up.
            val scale = com.addhd.app.ui.theme.pulse(
                from = 1f, to = 1.08f, durationMs = 620, label = "tierPulse",
            )

            // Full-bleed scrim to sell the "everything stops" moment.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    Modifier.fillMaxWidth().padding(24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("⚔️🔥⚔️", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative())
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "WORLD TIER INCREASES!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.scale(scale),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            WorldTier.label(tier),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "The world grows more dangerous, but the rewards are legendary.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }
    }
}
