package com.addhd.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addhd.app.campaign.ArtifactBuff
import com.addhd.app.campaign.BuffKind
import com.addhd.app.campaign.ConsumableEffect
import com.addhd.app.campaign.ShopCatalog
import com.addhd.app.campaign.ShopItem
import com.addhd.app.data.OwnedItemEntity

/**
 * The Adventurer's Shop. Spend gold on consumables (stackable one-shots) and artifacts
 * (unique, equippable passive buffs). Consumes the ViewModel's gold + ownedItems state; all
 * mutations go through onBuy / onEquip so the screen stays a pure projection of VM state.
 */
@Composable
fun ShopScreen(
    gold: Int,
    owned: List<OwnedItemEntity>,
    xpPotionPrimed: Boolean,
    onBuy: (String) -> Unit,
    onEquip: (String, Boolean) -> Unit,
    onUsePotion: () -> Unit,
) {
    val ownedById = owned.associateBy { it.itemId }
    val consumables = ShopCatalog.items.values.filterIsInstance<ShopItem.Consumable>()
    val artifacts = ShopCatalog.items.values.filterIsInstance<ShopItem.Artifact>()

    // Full-width span for headers/banners; items default to 1 cell (half width).
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(2) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item(span = fullSpan) {
          Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Adventurer's Shop",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Gold balance pill.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "🪙 $gold",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Spend gold earned from quests and boss clears.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (xpPotionPrimed) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        "🧪 Focus primed — your next completed task earns Double XP!",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
          }
        }

        item(span = fullSpan) {
            Text("Consumables", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(consumables, key = { it.id }) { c ->
            val row = ownedById[c.id]
            ShopItemCard(
                emoji = c.emoji,
                name = c.name,
                flavor = c.flavor,
                cost = c.cost,
                effectLabel = effectLabel(c.effect),
                gold = gold,
                ownedQty = row?.qty ?: 0,
                maxStack = c.maxStack,
                equipped = false,
                isArtifact = false,
                onBuy = { onBuy(c.id) },
                onEquipToggle = {},
                // Only the Potion of Focus has a wired effect so far -> give it a Use button.
                onUse = if (c.effect == ConsumableEffect.XP_MULTIPLIER) onUsePotion else null,
            )
        }

        item(span = fullSpan) {
          Column {
            Spacer(Modifier.height(4.dp))
            Text("Artifacts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Persistent passive buffs. One equipped per slot.",
                style = MaterialTheme.typography.bodyMedium,
                // onBackground (deep ink) — this sits on the parchment background, not a surface
                // card, so onSurfaceVariant washed out. Matches the header subtitle above.
                color = MaterialTheme.colorScheme.onBackground,
            )
          }
        }
        items(artifacts, key = { it.id }) { a ->
            val row = ownedById[a.id]
            ShopItemCard(
                emoji = a.emoji,
                name = a.name,
                flavor = a.flavor,
                cost = a.cost,
                effectLabel = buffLabel(a.buff),
                gold = gold,
                ownedQty = if (row != null) 1 else 0,
                maxStack = 1,
                equipped = row?.equipped == true,
                isArtifact = true,
                onBuy = { onBuy(a.id) },
                onEquipToggle = { onEquip(a.id, row?.equipped != true) },
            )
        }
    }
}

@Composable
private fun ShopItemCard(
    emoji: String,
    name: String,
    flavor: String,
    cost: Int,
    effectLabel: String,
    gold: Int,
    ownedQty: Int,
    maxStack: Int,
    equipped: Boolean,
    isArtifact: Boolean,
    onBuy: () -> Unit,
    onEquipToggle: () -> Unit,
    onUse: (() -> Unit)? = null,
) {
    val owned = ownedQty > 0
    val maxed = ownedQty >= maxStack
    val canAfford = gold >= cost
    // Grid cell: content stacked vertically so it fits a half-width column. fillMaxHeight lets
    // side-by-side cells in a row match heights; the action button pins to the bottom via weight.
    Card(
        Modifier.fillMaxWidth().height(276.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.headlineMedium)
                if (!isArtifact && ownedQty > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "×$ownedQty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = com.addhd.app.ui.theme.GoldText.onCard,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                effectLabel,
                style = MaterialTheme.typography.labelMedium,
                color = com.addhd.app.ui.theme.GoldText.onCard,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                flavor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Push price + action to the bottom so all cells align regardless of flavor length.
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🪙 $cost",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!canAfford && !owned && !maxed) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "need ${cost - gold}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            CellAction(
                isArtifact = isArtifact, owned = owned, maxed = maxed, equipped = equipped,
                onBuy = onBuy, onEquipToggle = onEquipToggle, onUse = onUse,
            )
        }
    }
}

/**
 * The bottom action for a grid cell. Full-width buttons (no room for side-by-side in a half cell):
 * owned artifact -> Equip/Unequip; owned+usable consumable -> a Use row above Buy; else -> Buy.
 * Buy stays bright even when unaffordable (buyItem re-checks balance and fires the toast).
 */
@Composable
private fun CellAction(
    isArtifact: Boolean,
    owned: Boolean,
    maxed: Boolean,
    equipped: Boolean,
    onBuy: () -> Unit,
    onEquipToggle: () -> Unit,
    onUse: (() -> Unit)?,
) {
    if (isArtifact && owned) {
        Button(
            onClick = onEquipToggle,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (equipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                contentColor = if (equipped) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
            ),
        ) { Text(if (equipped) "Equipped ✓" else "Equip") }
        return
    }
    if (onUse != null && owned) {
        Button(
            onClick = onUse,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("Use") }
        Spacer(Modifier.height(6.dp))
    }
    val label = if (maxed) (if (isArtifact) "Owned" else "Max") else "Buy"
    Button(
        onClick = onBuy,
        enabled = !maxed,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f),
        ),
    ) { Text(label) }
}

private fun effectLabel(e: ConsumableEffect): String = when (e) {
    ConsumableEffect.SKIP_TASK -> "Skip one task"
    ConsumableEffect.XP_MULTIPLIER -> "Double XP, once"
    ConsumableEffect.SOS_BUFFER -> "Protect your streak once"
}

private fun buffLabel(b: ArtifactBuff): String = when (b.kind) {
    BuffKind.GOLD_GENERATION -> "+${(b.magnitude * 100).toInt()}% gold"
    BuffKind.GATE_REDUCTION -> "-${b.magnitude.toInt()} task per boss gate"
    BuffKind.XP_GENERATION -> "+${(b.magnitude * 100).toInt()}% XP"
}
