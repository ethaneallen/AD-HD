package com.addhd.app.campaign

/**
 * Code-side loot definitions, organized per-dungeon so new themes drop in cleanly.
 *
 * To add content later:
 *   1. Add LootDef entries to `allItems` (id must be unique — it's what gets persisted).
 *   2. List their ids in the matching DungeonLoot pool (or `commonPool` for any-dungeon drops).
 *   3. Add a new DungeonLoot to `dungeons` to theme a new dungeon's drops.
 * No database changes needed — LootEntity only stores the item's `id`.
 */
enum class LootStat { STR, DEX, CON, WIS }

/**
 * Drop rarity, ordered common -> legendary. `weight` is the relative pull in the weighted
 * roll: a Common is 50× as likely to surface as a Legendary from the same candidate set.
 * Rarity is DERIVED from a piece's [LootDef.bonus] (see [LootTable.rarityOf]) — bonus IS the
 * tier — so no per-item rarity field is needed and the existing catalog stays untouched.
 */
enum class Rarity(val weight: Int) {
    // Steepened so the top tiers feel genuinely scarce: a Common is now 60× as likely as a
    // Legendary and 30× an Epic (was 50× / 10×). Epic 5→2 and Rare 14→8 pull the curve down hard
    // at the top end while commons/uncommons still carry the bulk of drops.
    COMMON(60), UNCOMMON(30), RARE(8), EPIC(2), LEGENDARY(1),
}

data class LootDef(
    val id: String,
    val name: String,
    val emoji: String,
    val slot: String,          // one equipped per slot (Weapon, Boots, Ring, …)
    val stat: LootStat,
    val bonus: Int,
    val flavor: String,
)

/** A dungeon's themed drop pool. `itemIds` reference entries in LootTable.allItems. */
data class DungeonLoot(
    val dungeonName: String,
    val itemIds: List<String>,
)

object LootTable {

    // ---- Item catalog (all items across all dungeons) ----
    val allItems: Map<String, LootDef> = listOf(
        // Dungeon 1 — Cleanse the Caves (goblins, rats, damp stone)
        LootDef("cave_boots_promptness", "Boots of Promptness", "🥾", "Boots", LootStat.DEX, 2, "They itch until you get started."),
        LootDef("cave_blade_focus", "Rusty Cave Blade", "🗡️", "Weapon", LootStat.STR, 2, "Chipped, but it bites."),
        LootDef("cave_ring_resolve", "Ring of Resolve", "💍", "Ring", LootStat.WIS, 2, "Cools the hand when an urge rises."),
        LootDef("cave_charm_grit", "Goblin-Tooth Charm", "🦷", "Trinket", LootStat.CON, 2, "Proof you made it out."),

        // Dungeon 2 — Reclaim the Ruins (undead, dust, old magic)
        LootDef("ruin_amulet_grit", "Amulet of Grit", "🔶", "Amulet", LootStat.CON, 3, "Warm on the days you show up."),
        LootDef("ruin_gauntlets_power", "Gauntlets of Power", "🧤", "Hands", LootStat.STR, 3, "Big tasks feel a little smaller."),
        LootDef("ruin_helm_clarity", "Helm of Clarity", "⛑️", "Head", LootStat.DEX, 3, "The next step stays in view."),
        LootDef("ruin_tome_focus", "Dusty War Tome", "📕", "Trinket", LootStat.WIS, 3, "Old words, steady mind."),

        // Dungeon 3 — Storm the Spire (frost, height, wind)
        LootDef("spire_cloak_calm", "Cloak of Calm", "🧥", "Cloak", LootStat.WIS, 4, "The noise fades when you wear it."),
        LootDef("spire_shield_steadfast", "Shield of the Steadfast", "🛡️", "Shield", LootStat.CON, 4, "Turns a slip into a scratch."),
        LootDef("spire_axe_momentum", "Frostbite Axe", "🪓", "Weapon", LootStat.STR, 4, "The first swing is the hardest."),
        LootDef("spire_boots_swift", "Windstep Boots", "👢", "Boots", LootStat.DEX, 4, "Distraction can't keep up."),

        // Dungeon 4 — Descend the Depths (shadow, dragons, the deep)
        LootDef("depths_charm_luck", "Charm of Lucky Starts", "🍀", "Trinket", LootStat.WIS, 5, "Crits come a little easier."),
        LootDef("depths_blade_dragon", "Dragonfang Greatsword", "⚔️", "Weapon", LootStat.STR, 5, "Made for boss fights."),
        LootDef("depths_plate_deep", "Deepplate Armor", "🥼", "Chest", LootStat.CON, 5, "Nothing down here scares you now."),
        LootDef("depths_ring_shadow", "Shadowstep Ring", "💠", "Ring", LootStat.DEX, 5, "You move before doubt does."),
        // Plus the author's own D&D arsenal: 51 named campaign pieces ([CampaignGear]) and the
        // 55-piece base kit including the +1/+2/+3 magic weapons ([GenericGear]).
    ).plus(CampaignGear.items).plus(GenericGear.items).associateBy { it.id }

    // ---- Per-dungeon drop pools (index matches Campaign quest order) ----
    // Each tier carries its original hand-written four PLUS that setting's campaign gear, so the
    // candidate set is wide enough that a drop is a surprise rather than a trip round the same
    // sixteen pieces.
    private val dungeons: List<DungeonLoot> = listOf(
        DungeonLoot("The Borderlands", listOf("cave_boots_promptness", "cave_blade_focus", "cave_ring_resolve", "cave_charm_grit") + CampaignGear.pools[0]),
        DungeonLoot("The Sword Coast", listOf("ruin_amulet_grit", "ruin_gauntlets_power", "ruin_helm_clarity", "ruin_tome_focus") + CampaignGear.pools[1]),
        DungeonLoot("Karameikos", listOf("spire_cloak_calm", "spire_shield_steadfast", "spire_axe_momentum", "spire_boots_swift") + CampaignGear.pools[2]),
        DungeonLoot("Barovia", listOf("depths_charm_luck", "depths_blade_dragon", "depths_plate_deep", "depths_ring_shadow") + CampaignGear.pools[3]),
        // Fifth tier: [Campaign.lootTierFor] now yields 0..4, so without this the final five
        // quests clamped back onto Barovia's pool.
        DungeonLoot("Chult", CampaignGear.pools[4]),
    )

    /** Rarity for a piece, derived from its stat [LootDef.bonus] (bonus IS the tier). */
    fun rarityOf(def: LootDef): Rarity = when (def.bonus) {
        in Int.MIN_VALUE..2 -> Rarity.COMMON
        3 -> Rarity.UNCOMMON
        4 -> Rarity.RARE
        5 -> Rarity.EPIC
        else -> Rarity.LEGENDARY   // bonus >= 6 (reserved for the expanded pool in Pass 2)
    }

    /**
     * Roll a boss drop. Three fixes over the old raw modulo-index pick (which clustered the same
     * item across consecutive kills):
     *
     *  1. Cumulative, mixed-rarity pool — the candidate set is the current dungeon PLUS every
     *     earlier (lower-tier) dungeon, not just the current one. This is what makes the rarity
     *     weights actually bite: each dungeon's own pool is single-tier (Dungeon 4 = all Epic), so
     *     a per-dungeon draw is a coin-flip among equals and the weights are dead. Mixing tiers in
     *     means the current dungeon's signature Epic/Legendary piece surfaces only on a rare
     *     weighted hit instead of on every single kill.
     *  2. Anti-clustering / duplicate protection — while the pool still has items the hero
     *     doesn't own, the draw is restricted to those UNOWNED items, so you can't be handed
     *     a piece you already have until you've collected everything available.
     *     Once fully owned, it falls back to the whole pool (dupes-to-sell are all that's left).
     *  3. Weighted-by-rarity — the winner is chosen by a deterministic weighted draw over
     *     [Rarity.weight] rather than a flat index, so commons surface often and epics/legendaries
     *     feel rare instead of every slot being equally likely.
     *
     * Deterministic in `seed` so it stays resume-safe. `ownedDefIds` is the set of defIds the
     * hero currently holds (equipped or in the bag); pass an empty set for "no exclusion".
     */
    fun rollDrop(dungeonIndex: Int, seed: Int, ownedDefIds: Set<String> = emptySet()): LootDef {
        val idx = dungeonIndex.coerceIn(0, dungeons.lastIndex)
        // Current dungeon + all lower-tier dungeons, so the weighted roll spans commons → epics.
        // Themed campaign gear is CUMULATIVE (this setting plus every earlier one), which is what
        // lets the rarity weights bite across tiers.
        //
        // The mundane 5e kit is deliberately NOT cumulative — only the current tier's band joins
        // the draw. Merging its 27 low-tier commons into every tier pushed commons from 46% to 68%
        // of all draws and broke `dropsAreNotAllCommons`: a late-game hero would have been wading
        // through daggers and padded armour. Scoped this way, mundane kit is what you turn up
        // locally, while the named campaign pieces still travel with you. Nothing becomes
        // unobtainable, because the 25 quests cycle back through every tier.
        val pool = ((0..idx).flatMap { dungeons[it].itemIds } + GenericGear.pools[idx.coerceIn(0, GenericGear.pools.lastIndex)])
            .mapNotNull { allItems[it] }
        // Prefer unowned candidates.
        val unowned = pool.filter { it.id !in ownedDefIds }
        val candidates = unowned.ifEmpty {
            // Everything is owned, so this drop can only be a duplicate — make it a duplicate
            // worth having. Falling back to the WHOLE pool meant 60% of every later drop was one
            // of the four lowest-tier commons, because commons hold 60 of every 100 tickets.
            // Restricting to the current tier at least hands back something with sell value.
            (dungeons[idx].itemIds + GenericGear.pools[idx.coerceIn(0, GenericGear.pools.lastIndex)])
                .mapNotNull { allItems[it] }.ifEmpty { pool }
        }
        return weightedPick(candidates, seed)
    }

    /**
     * Deterministic weighted selection: each candidate contributes [Rarity.weight] tickets and
     * a seed-derived index into the total ticket count picks the winner. Non-negative seed
     * folding matches the old modulo's sign-safety.
     */
    private fun weightedPick(candidates: List<LootDef>, seed: Int): LootDef {
        val total = candidates.sumOf { rarityOf(it).weight }
        var roll = ((mix(seed) % total) + total) % total
        for (def in candidates) {
            roll -= rarityOf(def).weight
            if (roll < 0) return def
        }
        return candidates.last()   // unreachable; guards against rounding
    }

    /**
     * Bit-mixing finalizer (MurmurHash3's), applied to the seed before it indexes ticket space.
     *
     * The seed is the hero's lifetime XP, which climbs ~26-39 per win — a slow WALK, not a sample.
     * Taken modulo directly, consecutive drops landed inside the same 60-ticket common block and
     * handed out the identical item several times in a row, then marched through the commons in
     * order. Mixing decorrelates neighbouring seeds while staying fully deterministic, so the roll
     * is still resume-safe.
     */
    private fun mix(seed: Int): Int {
        var h = seed
        h = h xor (h ushr 16); h *= 0x7feb352d.toInt()
        h = h xor (h ushr 15); h *= -0x7b7d1b7b           // 0x846ca68b
        h = h xor (h ushr 16)
        return h
    }

    fun def(id: String): LootDef? = allItems[id]

    /**
     * Gold paid when a piece of loot is sold back. There is no rarity field — a piece's stat
     * [LootDef.bonus] IS its tier (cave gear=2 → Common, ruins=3 → Rare, spire/depths=4-5 → Epic),
     * so we map the sell value off that.
     */
    fun sellValue(def: LootDef): Int = when (def.bonus) {
        2 -> 5       // Common
        3 -> 20      // Rare
        else -> 50   // Epic (bonus 4-5)
    }
}
