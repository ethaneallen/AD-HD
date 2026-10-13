package com.addhd.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.campaign.HeroClass
import com.addhd.app.campaign.HeroStats
import com.addhd.app.campaign.LootStat
import com.addhd.app.campaign.LootTable
import com.addhd.app.data.LootEntity
import com.addhd.app.data.PetEntity
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.ui.theme.Radii

/**
 * Gear-slot display order + empty-slot placeholder glyph. Covers every slot the loot table can
 * produce (not just 6) so an equipped item can never be orphaned when the inventory list hides
 * equipped gear. The player's requested six lead; the rest follow.
 */
private val GEAR_SLOTS: List<Pair<String, String>> = listOf(
    "Head" to "⛑️",
    "Weapon" to "⚔️",
    "Boots" to "👢",
    "Amulet" to "🔶",
    "Ring" to "💍",
    "Trinket" to "🔮",
    "Chest" to "🥼",
    "Cloak" to "🧥",
    "Shield" to "🛡️",
    "Hands" to "🧤",
)

@Composable
fun CharacterScreen(
    pet: PetEntity?,
    heroClass: HeroClass?,
    stats: HeroStats,
    loot: List<LootEntity>,
    lifetimeQuests: Int,
    worldTier: Int,
    ascended: Boolean,
    ownedItems: List<com.addhd.app.data.OwnedItemEntity>,
    onToggleEquip: (Long, Boolean) -> Unit,
    onToggleArtifact: (String, Boolean) -> Unit,
    onSellLoot: (Long) -> Unit,
) {
    // Sell Mode: when on, inventory cards swap their Equip pill for a "Sell for Xg" action so a
    // tap liquidates the piece instead of equipping it. Only unequipped gear is in the bag, so
    // this can never target worn gear. Reset implicitly whenever the screen leaves composition.
    var sellMode by remember { mutableStateOf(false) }
    // Owned shop artifacts split into equipped (active buffs) and owned-but-not. Consumables are
    // shown as a held-items tally. Distinct from loot gear — these carry economy/meta buffs.
    val artifacts = remember(ownedItems) {
        ownedItems.mapNotNull { row ->
            com.addhd.app.campaign.ShopCatalog.artifact(row.itemId)?.let { it to row.equipped }
        }
    }
    val heldConsumables = remember(ownedItems) {
        ownedItems.mapNotNull { row ->
            com.addhd.app.campaign.ShopCatalog.consumable(row.itemId)?.let { it to row.qty }
        }.filter { it.second > 0 }
    }
    // Sum equipped loot bonuses per stat so the sheet shows base + gear.
    val equippedBonuses = remember(loot) {
        val map = mutableMapOf(LootStat.STR to 0, LootStat.DEX to 0, LootStat.CON to 0, LootStat.WIS to 0)
        loot.filter { it.equipped }.forEach { li ->
            LootTable.def(li.defId)?.let { d -> map[d.stat] = (map[d.stat] ?: 0) + d.bonus }
        }
        map
    }

    // Equipped item per slot (for the Active Gear row). One-per-slot is enforced on equip, so
    // firstOrNull is authoritative. Any slot not present here is empty.
    val equippedBySlot = remember(loot) {
        loot.filter { it.equipped }
            .mapNotNull { li -> LootTable.def(li.defId)?.let { it.slot to li } }
            .toMap()
    }
    // Bag list = UNEQUIPPED items only, grouped by slot. Equipped gear lives in the top row now,
    // so the bag stays clean with no duplicate clutter.
    val grouped = remember(loot) {
        loot.filter { !it.equipped }
            .mapNotNull { li -> LootTable.def(li.defId)?.let { it.slot to li } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
    }
    val unequippedCount = remember(loot) { loot.count { !it.equipped } }
    // Which slot sections are collapsed. Default: all expanded (tap a header to collapse).
    val collapsedSlots = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            Text("Character", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar. When Ascended, the class emoji sits in a glowing tertiary ring with a
                    // ✨ marker — an unmistakable visual evolution of the hero's core identity.
                    if (ascended) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f))
                                    .border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(999.dp)),
                            )
                            Text(heroClass?.emoji ?: "🧙", style = MaterialTheme.typography.headlineLarge)
                            Text(
                                "✨",
                                style = MaterialTheme.typography.titleMedium,
                                // The only marker of an ascended hero — must be announced.
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .describedAs("Ascended"),
                            )
                        }
                    } else {
                        Text(heroClass?.emoji ?: "🧙", style = MaterialTheme.typography.headlineLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pet?.name ?: "The Hero",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    // Title. Ascension prefixes the class ("Ascended Barbarian") to reflect the evolution.
                    val classLabel = heroClass?.display ?: "Adventurer"
                    Text(
                        "${if (ascended) "Ascended $classLabel" else classLabel}  ·  Level ${pet?.level ?: 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    // Ascended badge — a status pill mirroring the World Tier badge below it.
                    if (ascended) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "✨ Ascended Class",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Permanent lifetime high score — survives the 30-day archive purge.
                    Text(
                        "🏆 $lifetimeQuests quests completed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    // New Game Plus badge. Shown only past tier 1, with the live reward multiplier.
                    if (worldTier > 1) {
                        Spacer(Modifier.height(8.dp))
                        val scalarPct = Math.round((com.addhd.app.campaign.WorldTier.scalar(worldTier) - 1) * 100).toInt()
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "🔥 ${com.addhd.app.campaign.WorldTier.label(worldTier)}  ·  +$scalarPct% rewards",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Text("Stats", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                StatRow("STR", "Strength", stats.str, equippedBonuses[LootStat.STR] ?: 0, "Boss damage: ${stats.bossDamage}")
                StatRow("DEX", "Dexterity", stats.dex, equippedBonuses[LootStat.DEX] ?: 0, "Dodge: ${stats.dodgePct}%")
                StatRow("CON", "Constitution", stats.con, equippedBonuses[LootStat.CON] ?: 0, "Max HP: ${stats.maxHp}")
                StatRow("WIS", "Wisdom", stats.wis, equippedBonuses[LootStat.WIS] ?: 0, "Crit: ${stats.critChancePct}%")
                Spacer(Modifier.height(8.dp))
                Text(
                    "STR = medium/big tasks · DEX = focus blocks · CON = clean streak · WIS = urges ridden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ===== Shop Artifacts & Active Buffs — economy/meta buffs, distinct from loot gear. =====
        if (artifacts.isNotEmpty() || heldConsumables.isNotEmpty()) {
            item {
                SectionCard {
                    Text("Artifacts & Buffs", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (artifacts.isEmpty()) {
                        Text(
                            "No artifacts yet — buy them in the Adventurer's Shop.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    artifacts.forEach { (def, equipped) ->
                        ArtifactRow(def, equipped, onToggleArtifact)
                    }
                    if (heldConsumables.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Held consumables", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        heldConsumables.forEach { (def, qty) ->
                            Text(
                                "${def.emoji}  ${def.name}  ×$qty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // ===== Active Gear Slots — the equipped paperdoll, horizontally scrollable. =====
        item {
            Text("Equipped Gear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GEAR_SLOTS.forEach { (slot, placeholder) ->
                    GearSlot(
                        slot = slot,
                        placeholder = placeholder,
                        equipped = equippedBySlot[slot],
                        onTap = { li -> onToggleEquip(li.id, false) }, // tap equipped -> unequip
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Inventory ($unequippedCount)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Sell Mode toggle — only offered when there's unequipped gear to sell.
                if (unequippedCount > 0) {
                    SellModePill(active = sellMode, onClick = { sellMode = !sellMode })
                }
            }
            if (sellMode) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap a piece to sell it for gold. Equipped gear is safe — unequip it first to sell.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (unequippedCount == 0) {
            item {
                Text(
                    if (loot.isEmpty())
                        "No loot yet. Defeat a dungeon boss (clear Room 10) to earn your first drop."
                    else "Bag empty — all your gear is equipped above. 🎒",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // One collapsible section per slot (unequipped items only — equipped gear is up top).
        grouped.forEach { (slot, itemsInSlot) ->
            val collapsed = collapsedSlots[slot] == true
            item(key = "hdr-$slot") {
                Row(
                    Modifier.fillMaxWidth().clickable { collapsedSlots[slot] = !collapsed }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${if (collapsed) "▸" else "▾"}  $slot (${itemsInSlot.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!collapsed) {
                items(itemsInSlot, key = { it.id }) { li ->
                    LootItemCard(li, sellMode, onToggleEquip, onSellLoot)
                }
            }
        }
    }
}

/**
 * A single 64dp gear slot. Filled = teal square with the item emoji + a stat-bonus badge in the
 * corner (tap to unequip). Empty = faint outlined square with a ghosted placeholder glyph and
 * the slot name beneath.
 */
@Composable
private fun GearSlot(
    slot: String,
    placeholder: String,
    equipped: LootEntity?,
    onTap: (LootEntity) -> Unit,
) {
    val def = equipped?.let { LootTable.def(it.defId) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (def != null) Modifier
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onTap(equipped) }
                    else Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (def != null) {
                Text(def.emoji, style = MaterialTheme.typography.headlineMedium)
                // Stat-bonus badge, bottom-end corner: cream pill with dark ink.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        "+${def.bonus} ${def.stat.name}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                }
            } else {
                // Ghosted placeholder for an empty slot.
                Text(
                    placeholder,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            slot,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (def != null) 1f else 0.5f),
            fontWeight = if (def != null) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * A single inventory item card. Colours track equipped (gold) vs not (leather) for contrast.
 * In [sellMode] the card's tap and trailing pill switch from equip to sell-for-gold; the bag
 * only ever holds unequipped gear, so a sell here can't strip the worn paperdoll.
 */
@Composable
private fun LootItemCard(
    li: LootEntity,
    sellMode: Boolean,
    onToggleEquip: (Long, Boolean) -> Unit,
    onSellLoot: (Long) -> Unit,
) {
    val d = LootTable.def(li.defId) ?: return
    val titleColor = if (li.equipped) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    val descColor = if (li.equipped) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val cardTap: () -> Unit =
        if (sellMode) { { onSellLoot(li.id) } } else { { onToggleEquip(li.id, !li.equipped) } }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = cardTap),
        shape = RoundedCornerShape(Radii.card),
        colors = CardDefaults.cardColors(
            containerColor = if (li.equipped) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, color = titleColor)
                Text(
                    "+${d.bonus} ${d.stat.name} · ${d.flavor}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = descColor,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (sellMode) {
                SellPill(gold = LootTable.sellValue(d), onClick = { onSellLoot(li.id) })
            } else {
                EquipPill(equipped = li.equipped, onClick = { onToggleEquip(li.id, !li.equipped) })
            }
        }
    }
}

/** Header toggle that flips the inventory into Sell Mode. Active = solid error/gold; idle = outline. */
@Composable
private fun SellModePill(active: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (active) Modifier.background(accent)
                else Modifier.border(1.5.dp, accent, RoundedCornerShape(50))
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            if (active) "Done" else "Sell",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.onTertiary else accent,
        )
    }
}

/** Per-item sell action shown in Sell Mode: "Sell · Xg". Solid tertiary to read as a distinct action. */
@Composable
private fun SellPill(gold: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiary)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            "Sell · ${gold}g",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

/**
 * Equip status/toggle pill. Equipped = solid vibrant teal with dark ink (crisp "active" look);
 * not equipped = teal outline + teal text (clear affordance to equip). Both high-contrast.
 */
@Composable
private fun EquipPill(equipped: Boolean, onClick: () -> Unit) {
    val teal = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (equipped) Modifier.background(teal)
                else Modifier.border(1.5.dp, teal, RoundedCornerShape(50))
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            if (equipped) "Equipped" else "Equip",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (equipped) MaterialTheme.colorScheme.onPrimary else teal,
        )
    }
}

/**
 * A shop artifact on the Character sheet: emoji + name + its passive-buff label, with an
 * Equip/Equipped toggle (reuses EquipPill). Equipped artifacts are the "active buffs".
 */
@Composable
private fun ArtifactRow(
    def: com.addhd.app.campaign.ShopItem.Artifact,
    equipped: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(def.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(def.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                artifactBuffLabel(def.buff),
                style = MaterialTheme.typography.bodyMedium,
                color = if (equipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (equipped) FontWeight.Bold else FontWeight.Normal,
            )
        }
        EquipPill(equipped = equipped, onClick = { onToggle(def.id, !equipped) })
    }
}

private fun artifactBuffLabel(b: com.addhd.app.campaign.ArtifactBuff): String = when (b.kind) {
    com.addhd.app.campaign.BuffKind.GOLD_GENERATION -> "+${(b.magnitude * 100).toInt()}% gold earned"
    com.addhd.app.campaign.BuffKind.GATE_REDUCTION -> "-${b.magnitude.toInt()} task per boss gate"
    com.addhd.app.campaign.BuffKind.XP_GENERATION -> "+${(b.magnitude * 100).toInt()}% XP earned"
}

@Composable
private fun StatRow(abbr: String, name: String, base: Int, bonus: Int, derived: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(abbr, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(derived, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (bonus > 0) "${base + bonus}  (${base}+$bonus)" else "$base",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
