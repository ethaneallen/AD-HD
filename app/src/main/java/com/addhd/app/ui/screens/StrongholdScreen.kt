package com.addhd.app.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.ui.theme.Radii
import com.addhd.app.vm.AppViewModel
import com.addhd.app.vm.AppViewModel.StrongholdUiState

/**
 * The Hero's Tavern (Stronghold). Renders the three progressive states of [StrongholdUiState]:
 *   - locked (dilapidated lot) -> Rebuild the Tavern
 *   - unlocked, no garden      -> Clear & Plant Garden + future-plot placeholders
 *   - operational garden       -> live pending/countdown + Harvest
 * Pure projection of VM state; all mutations go through the callbacks.
 */
@Composable
fun StrongholdScreen(
    state: StrongholdUiState,
    gold: Int,
    onBuyTavern: () -> Unit,
    onBuildGarden: () -> Unit,
    onHarvest: () -> Unit,
    onUnlockTrophyRoom: () -> Unit,
    onPurchaseAscension: () -> Unit,
    onPurchaseChronoAnchor: () -> Unit,
    onUnlockVault: () -> Unit,
    onUpgradeVault: () -> Unit,
    onClaimStipend: () -> Unit,
    // The endless drains: a repeatable timed XP buff and a house-edged dice table.
    bardOffer: AppViewModel.BardOffer? = null,
    onCommissionBard: () -> Unit = {},
    diceTable: AppViewModel.DiceTableState? = null,
    lastBet: AppViewModel.BetResult? = null,
    onRollBet: (Int) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "The Hero's Tavern",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            GoldPill(gold)
        }

        if (!state.isUnlocked) {
            LockedTavern(onBuyTavern)
        } else {
            GardenCard(state, onBuildGarden, onHarvest)
            TrophyRoomCard(state, onUnlockTrophyRoom, onPurchaseAscension, onPurchaseChronoAnchor)
            CoinVaultCard(state, onUnlockVault, onUpgradeVault, onClaimStipend)
            // The two rooms that give gold somewhere to go forever, rather than one more
            // thing to buy once and finish.
            bardOffer?.let { BardCard(it, onCommissionBard) }
            diceTable?.let { DiceTableCard(it, lastBet, onRollBet) }
        }
    }
}

/**
 * The Bard — pay gold for a timed XP multiplier.
 *
 * The app's only gold sink that can't be completed. It replaced "a night at the inn", which sold
 * 2 Momentum (+2% XP) for 600 gold: mechanically sound, but that gold cost roughly 1,200 raw XP of
 * work to earn while two active days restored the same 2 points for free — a deal nobody would
 * take, so it drained nothing. The lesson applied here is that a sink has to sell something worth
 * buying, and this one is felt on the very next completion.
 */
@Composable
private fun BardCard(offer: AppViewModel.BardOffer, onCommission: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎵", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative())
            Spacer(Modifier.width(12.dp))
            Text("The Bard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Put coin in the bard's hat and the whole tavern works harder — every XP award earns " +
                "+${offer.bonusPct}% while the music lasts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        StatusLine("Commission", "${offer.cost} Gold")
        Spacer(Modifier.height(6.dp))
        StatusLine(
            "Bought this month",
            if (offer.commissionsUsed == 0) "none yet" else "${offer.commissionsUsed} (price doubles each)",
        )
        Spacer(Modifier.height(6.dp))
        StatusLine(
            "Status",
            if (offer.isPlaying) "Playing — ${humanDuration(offer.remainingMillis)} left" else "Silent",
        )
        Spacer(Modifier.height(14.dp))
        PrimaryTavernButton(
            if (offer.isPlaying) {
                "Keep him playing — another 24h (${offer.cost} 🪙)"
            } else {
                "Commission the bard — +${offer.bonusPct}% XP for 24h (${offer.cost} 🪙)"
            },
            onClick = onCommission,
            enabled = offer.canAfford,
        )
    }
}

/**
 * The Gambling Table — a deliberately losing proposition (8% house edge), which is the point: it
 * has to drain gold on average or it's just another faucet.
 *
 * Only gold is ever at stake — never XP, Momentum, streaks or items — and both the stake and the
 * number of rolls per day are hard-capped, so a bad run can cost you coins and nothing else.
 */
@Composable
private fun DiceTableCard(
    table: AppViewModel.DiceTableState,
    lastBet: AppViewModel.BetResult?,
    onRoll: (Int) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎲", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative())
            Spacer(Modifier.width(12.dp))
            Text("The Gambling Table", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Stake gold on one d20. 11–19 pays back half again, a natural 20 pays triple, " +
                "anything lower and the house keeps it. The house wins over time — that's what " +
                "makes it a tavern and not a treasury.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        StatusLine("Rolls left tonight", "${table.rollsLeftToday}")

        lastBet?.let { b ->
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    b.jackpot -> "🎲 ${b.roll} — a natural twenty! +${b.netGold} gold"
                    b.netGold > 0 -> "🎲 ${b.roll} — the table pays. +${b.netGold} gold"
                    else -> "🎲 ${b.roll} — the house keeps it. ${b.netGold} gold"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (b.netGold > 0) MaterialTheme.colorScheme.primary
                else com.addhd.app.ui.theme.GoldText.onCard,
            )
        }

        Spacer(Modifier.height(14.dp))
        if (table.rollsLeftToday <= 0) {
            Text(
                "The table's closed for tonight. Come back tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Fixed stakes rather than a free-entry field: no escalating "just one more, bigger"
            // chase, and the maximum is always visible.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 150, 500).forEach { stake ->
                    OutlinedButton(
                        onClick = { onRoll(stake) },
                        enabled = table.maxStake >= stake,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                        border = com.addhd.app.ui.components.cardOutlinedBorder(),
                    ) { Text("$stake 🪙", fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun GoldPill(gold: Int) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
    ) {
        Text(
            "🪙 $gold",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** State 0 — the ruined lot awaiting a rebuild. */
@Composable
private fun LockedTavern(onBuyTavern: () -> Unit) {
    SectionCard {
        Text("🏚️", style = MaterialTheme.typography.displaySmall, modifier = Modifier.decorative())
        Spacer(Modifier.height(8.dp))
        Text("A Dilapidated Lot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Weeds choke the foundation of what was once a proud tavern. With enough gold, you could " +
                "rebuild it into a stronghold — a home base that works for you even while you rest.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        // Kept enabled even when short — tapping fires buyTavern(), which safely no-ops and toasts
        // the shortfall (same pattern as the shop Buy buttons).
        Button(
            onClick = onBuyTavern,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Rebuild the Tavern (3,000 🪙)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    }
}

/** States 1 & 2 — the Garden Plot, unbuilt or operational. */
@Composable
private fun GardenCard(state: StrongholdUiState, onBuildGarden: () -> Unit, onHarvest: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌿", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative())
            Spacer(Modifier.width(12.dp))
            Text("Focus Garden", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        if (state.gardenLevel <= 0) {
            // State 1 — empty plot.
            Text(
                "An overgrown patch of land, perfect for cultivation. Plant a Focus Garden and it " +
                    "will brew a Potion of Focus every 3 days — even while you're away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onBuildGarden,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Clear & Plant Garden (1,500 🪙)", fontWeight = FontWeight.Bold) }
        } else {
            // State 2 — operational.
            StatusLine("Level", "Level ${state.gardenLevel} (Focus Garden)")
            Spacer(Modifier.height(6.dp))
            // Potion stack: filled 🧪 per pending, faint ⚗️ for empty slots up to the cap of 3.
            val filled = state.pendingGardenPotions
            StatusLine("Stored", "🧪".repeat(filled) + "·".repeat((3 - filled).coerceAtLeast(0)) + "   $filled / 3")
            Spacer(Modifier.height(6.dp))
            StatusLine(
                "Growth",
                if (filled >= 3) "Storage full"
                else "Next potion in ${humanDuration(state.nextPotionInMillis)}",
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onHarvest,
                enabled = filled > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                ),
            ) { Text(if (filled > 0) "Harvest Potions" else "Nothing to harvest yet", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** State 3 — The Trophy Room. Locked -> unlock CTA; unlocked -> the two endgame purchases. */
@Composable
private fun TrophyRoomCard(
    state: StrongholdUiState,
    onUnlock: () -> Unit,
    onAscend: () -> Unit,
    onChrono: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative())
            Spacer(Modifier.width(12.dp))
            Text("Trophy Room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        if (state.trophyRoomLevel <= 0) {
            // Locked — clear the rubble.
            Text(
                "Rubble buries what was once a hall of legends. Clear it to unlock the endgame " +
                    "storefront — permanent, rule-bending rewards for a seasoned hero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryTavernButton("Unlock Trophy Room (2,000 🪙)", onClick = onUnlock)
        } else {
            // Unlocked — the premium endgame shop.
            Text(
                "Legendary rewards for the seasoned hero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // Ascended Class Evolution
            UpgradeRow(
                emoji = "✨",
                title = "Ascended Class Evolution",
                desc = if (state.ascensionPurchased) "4th Artifact Slot Awakened"
                    else "A permanent evolution: new title and a 4th passive artifact slot.",
                owned = state.ascensionPurchased,
                ownedLabel = "UNLOCKED",
                buyLabel = "Ascend (3,000 🪙)",
                onBuy = onAscend,
            )
            Spacer(Modifier.height(10.dp))
            // Chrono-Anchor
            UpgradeRow(
                emoji = "⏳",
                title = "The Chrono-Anchor",
                desc = if (state.chronoAnchorPurchased) "Auto-guards your streak"
                    else "Legendary relic: 2 Streak-Freeze charges a month to guard a planned day off.",
                owned = state.chronoAnchorPurchased,
                ownedLabel = "${state.chronoCharges}/2 CHARGES",
                buyLabel = "Anchor (8,000 🪙)",
                onBuy = onChrono,
            )
        }
    }
}

/** State 3 — The Coin Vault. Locked -> construct CTA; built -> tier, upgrade, and claim. */
@Composable
private fun CoinVaultCard(
    state: StrongholdUiState,
    onUnlock: () -> Unit,
    onUpgrade: () -> Unit,
    onClaim: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💰", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative())
            Spacer(Modifier.width(12.dp))
            Text("Coin Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        if (state.vaultLevel <= 0) {
            // Locked — construct.
            Text(
                "An empty strongroom. Build the Coin Vault and it will accrue a gold stipend every " +
                    "day — passive income to soften the endgame grind.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryTavernButton("Construct Coin Vault (2,500 🪙)", onClick = onUnlock)
        } else {
            // Built — vault level, daily yield, status, upgrade, claim.
            val perDay = if (state.vaultLevel >= 2) 30 else 15
            val ready = state.nextStipendInMillis <= 0L
            StatusLine("Vault Level", "Tier ${state.vaultLevel}")
            Spacer(Modifier.height(6.dp))
            StatusLine("Daily Yield", "+$perDay Gold / day")
            Spacer(Modifier.height(6.dp))
            StatusLine("Status", if (ready) "Ready to Claim" else "Next yield in ${humanDuration(state.nextStipendInMillis)}")

            if (state.vaultLevel == 1) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                    border = com.addhd.app.ui.components.cardOutlinedBorder(),
                ) { Text("Upgrade to Tier 2 (4,000 🪙)", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClaim,
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                ),
            ) { Text(if (ready) "Claim Gold" else "Next yield in ${humanDuration(state.nextStipendInMillis)}", fontWeight = FontWeight.Bold) }
        }
    }
}

/** A full-width primary Tavern action button (unlock/construct CTAs). Stays enabled when short —
 *  the VM no-ops and toasts the shortfall, matching the Rebuild-the-Tavern pattern. */
@Composable
private fun PrimaryTavernButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
}

/** One purchasable endgame upgrade row: emoji + title + description, and either a Buy button or a
 *  compact owned-state pill on the trailing edge. */
@Composable
private fun UpgradeRow(
    emoji: String,
    title: String,
    desc: String,
    owned: Boolean,
    ownedLabel: String,
    buyLabel: String,
    onBuy: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (owned) {
            // Owned STATUS badge — deliberately NOT a filled pill/button. A hollow, bordered
            // outline chip reads as an indicator, not a tappable action, so it can't be confused
            // with the Buy button it replaces. No onClick.
            Box(
                Modifier
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    ownedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Button(onClick = onBuy, shape = RoundedCornerShape(Radii.button)) {
                Text(buyLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Compact "1d 14h" / "3h 20m" / "45m" style remaining-time string from millis.
 * A garden below its cap always has a potion incoming, so any positive-but-sub-minute
 * remainder (and the harmless boundary tick where the delta rounds to ~0) renders as
 * "under a minute" rather than "now" — the capped case is handled by the caller as
 * "Storage full" and never reaches here.
 */
private fun humanDuration(millis: Long): String {
    val totalMin = millis / 60_000
    val d = totalMin / (60 * 24)
    val h = (totalMin % (60 * 24)) / 60
    val m = totalMin % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "under a minute"
    }
}
