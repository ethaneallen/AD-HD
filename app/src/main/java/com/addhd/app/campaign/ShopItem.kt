package com.addhd.app.campaign

/**
 * The Adventurer's Shop catalog. Like LootTable, all item CONTENT lives in code (only the item
 * id is persisted in OwnedItemEntity) so new stock drops in with no schema migration.
 *
 * Two kinds of item, modeled as a sealed hierarchy so buy/use/equip logic can branch exhaustively:
 *   - [ShopItem.Consumable]  — one-shot, stacks in inventory, spent to trigger an [ConsumableEffect].
 *   - [ShopItem.Artifact]    — persistent, unique, equipped into a passive-buff slot granting a
 *                              standing [ArtifactBuff] (economy/meta — NOT the combat STR/DEX/CON/WIS
 *                              stats, which remain the exclusive domain of boss loot).
 */
sealed class ShopItem {
    abstract val id: String
    abstract val name: String
    abstract val emoji: String
    abstract val cost: Int
    abstract val flavor: String

    data class Consumable(
        override val id: String,
        override val name: String,
        override val emoji: String,
        override val cost: Int,
        override val flavor: String,
        val effect: ConsumableEffect,
        /** Safety cap so a stack can't be hoarded absurdly; buys past this are rejected. */
        val maxStack: Int = 5,
    ) : ShopItem()

    data class Artifact(
        override val id: String,
        override val name: String,
        override val emoji: String,
        override val cost: Int,
        override val flavor: String,
        /** Passive-buff slot. One equipped Artifact per slot (mirrors the loot paperdoll rule). */
        val slot: ArtifactSlot,
        val buff: ArtifactBuff,
    ) : ShopItem()
}

/** What using one consumable does. Interpreted by the ViewModel when the item is consumed. */
enum class ConsumableEffect {
    SKIP_TASK,        // Scroll of Teleportation — instantly credit/skip one active task
    XP_MULTIPLIER,    // Potion of Focus — next completion (or session window) pays extra XP
    SOS_BUFFER,       // Elixir of Willpower — absorbs one SOS use without breaking the clean streak
}

/** Passive-buff artifact slots. One equipped artifact each. */
enum class ArtifactSlot { RELIC, TOME, SIGIL }

/**
 * Standing passive buffs granted by an equipped [ShopItem.Artifact]. `magnitude` meaning depends
 * on `kind`: a fractional bonus for GOLD_GENERATION (0.10 = +10%), a whole-number reduction for
 * GATE_REDUCTION (1 = -1 task on campaign boss gates).
 */
data class ArtifactBuff(val kind: BuffKind, val magnitude: Double)

enum class BuffKind {
    GOLD_GENERATION,  // +magnitude fraction to gold earned (e.g. 0.10 = +10%)
    GATE_REDUCTION,   // -magnitude tasks required on every campaign boss gate (floored at 1)
    XP_GENERATION,    // +magnitude fraction to XP earned
}

object ShopCatalog {

    val items: Map<String, ShopItem> = listOf(
        // ---- Consumables ----
        ShopItem.Consumable(
            id = "scroll_teleport",
            name = "Scroll of Teleportation",
            emoji = "📜",
            cost = 60,
            flavor = "Blink past one task you just can't face today.",
            effect = ConsumableEffect.SKIP_TASK,
        ),
        ShopItem.Consumable(
            id = "potion_focus",
            name = "Potion of Focus",
            emoji = "🧪",
            cost = 45,
            flavor = "Your next win hits harder. Double XP, once.",
            effect = ConsumableEffect.XP_MULTIPLIER,
        ),
        ShopItem.Consumable(
            id = "elixir_willpower",
            name = "Elixir of Willpower",
            emoji = "🫙",
            cost = 80,
            flavor = "Slip up once without losing your clean streak — it holds the line for you.",
            effect = ConsumableEffect.SOS_BUFFER,
            maxStack = 3,
        ),

        // ---- Artifacts (persistent passive buffs) ----
        ShopItem.Artifact(
            id = "relic_midas",
            name = "Coincatcher Relic",
            emoji = "🪙",
            cost = 250,
            flavor = "Every victory jingles a little louder. +10% gold.",
            slot = ArtifactSlot.RELIC,
            buff = ArtifactBuff(BuffKind.GOLD_GENERATION, 0.10),
        ),
        ShopItem.Artifact(
            id = "tome_shortcut",
            name = "Tome of Shortcuts",
            emoji = "📖",
            cost = 400,
            flavor = "The path to each boss is one step shorter. -1 task per gate.",
            slot = ArtifactSlot.TOME,
            buff = ArtifactBuff(BuffKind.GATE_REDUCTION, 1.0),
        ),
        ShopItem.Artifact(
            id = "sigil_scholar",
            name = "Sigil of the Scholar",
            emoji = "🔯",
            cost = 300,
            flavor = "Wisdom compounds. +10% XP from everything.",
            slot = ArtifactSlot.SIGIL,
            buff = ArtifactBuff(BuffKind.XP_GENERATION, 0.10),
        ),
    ).associateBy { it.id }

    fun item(id: String): ShopItem? = items[id]
    fun consumable(id: String): ShopItem.Consumable? = items[id] as? ShopItem.Consumable
    fun artifact(id: String): ShopItem.Artifact? = items[id] as? ShopItem.Artifact

    /**
     * Sum the magnitude of a given [BuffKind] across a set of owned-item ids that are equipped.
     * THE one place buff totals are computed, so the reactive gate-reduction stream and the
     * suspend gateReductionNow() (and any future buff reader) can't drift in how they tally.
     * Pass the ids of currently-equipped owned items.
     */
    fun buffTotal(equippedItemIds: Collection<String>, kind: BuffKind): Double =
        equippedItemIds.asSequence()
            .mapNotNull { artifact(it) }
            .filter { it.buff.kind == kind }
            .sumOf { it.buff.magnitude }
}
