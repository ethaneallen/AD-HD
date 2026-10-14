package com.addhd.app.campaign

/**
 * The base 5e arsenal, GENERATED from the author's D&D game (GAME_DATA.weapons / armor / shields).
 *
 * Complements [CampaignGear]'s 51 named, setting-specific pieces with the common kit — and, more
 * importantly, the magic +1/+2/+3 variants, which are the only genuinely top-tier weapons in the
 * source data.
 *
 * Tiering is derived from the statblock rather than flat-rated, because `bonus` IS the rarity in
 * this app (see [LootTable.rarityOf]) and a Common carries 60 of every 100 weighted tickets.
 * Adding 55 items as commons would have pushed commons to ~85% of all drops, re-creating the
 * "same crappy items" problem these imports exist to solve. So:
 *   - `+3` -> 6 (Legendary), `+2` -> 5 (Epic), `+1` -> 4 (Rare)
 *   - plain weapons by damage die: 2d6/1d12/1d10 -> 3, 1d8/1d6 -> 2, 1d4 -> 1
 *   - armour by AC: 18 -> 5, 16-17 -> 4, 14-15 -> 3, below -> 2
 *
 * Pools are keyed by that derived tier instead of by setting, since a Longsword belongs to no
 * campaign in particular.
 */
object GenericGear {

    val items: List<LootDef> = listOf(
        LootDef("kit_longsword", "Longsword", "⚔️", "Weapon", LootStat.STR, 2, "The classic knight's weapon. Versatile and reliable."),
        LootDef("kit_shortsword", "Shortsword", "🗡️", "Weapon", LootStat.DEX, 2, "A nimble blade favored by rogues. Quick and precise."),
        LootDef("kit_greatsword", "Greatsword", "⚔️", "Weapon", LootStat.STR, 3, "A massive two-handed sword. Devastating damage potential."),
        LootDef("kit_greataxe", "Greataxe", "⚔️", "Weapon", LootStat.STR, 3, "An enormous axe requiring two hands. Maximum damage per swing."),
        LootDef("kit_battleaxe", "Battleaxe", "⚔️", "Weapon", LootStat.STR, 2, "A heavy axe that cleaves through armor. Can be used one or two-handed."),
        LootDef("kit_handaxes", "Handaxes", "⚔️", "Weapon", LootStat.STR, 2, "A pair of small throwing axes. Can be used in melee or thrown at enemies."),
        LootDef("kit_rapier", "Rapier", "🗡️", "Weapon", LootStat.DEX, 2, "A thin, pointed blade for precise thrusts. The duelist's weapon."),
        LootDef("kit_scimitar", "Scimitar", "⚔️", "Weapon", LootStat.DEX, 2, "A curved blade with a slashing edge. Light and elegant."),
        LootDef("kit_dagger", "Dagger", "🗡️", "Weapon", LootStat.DEX, 1, "A small blade perfect for close combat or throwing. Light and concealable."),
        LootDef("kit_mace", "Mace", "🪓", "Weapon", LootStat.STR, 2, "A heavy club with a metal head. Effective against armored foes."),
        LootDef("kit_warhammer", "Warhammer", "🪓", "Weapon", LootStat.STR, 2, "A heavy hammer for crushing blows. Excellent against undead."),
        LootDef("kit_quarterstaff", "Quarterstaff", "🪓", "Weapon", LootStat.STR, 2, "A versatile wooden staff. Can be wielded one or two-handed."),
        LootDef("kit_longbow", "Longbow", "🗡️", "Weapon", LootStat.DEX, 2, "A tall bow with excellent range. The archer's weapon of choice."),
        LootDef("kit_shortbow", "Shortbow", "🗡️", "Weapon", LootStat.DEX, 2, "A compact bow for quick shots. Requires two hands to use."),
        LootDef("kit_light_crossbow", "Light Crossbow", "🗡️", "Weapon", LootStat.DEX, 2, "A mechanical ranged weapon. Easy to use but slow to reload."),
        LootDef("kit_hand_crossbow", "Hand Crossbow", "🗡️", "Weapon", LootStat.DEX, 2, "A small crossbow that can be used one-handed. Popular with rogues."),
        LootDef("kit_javelin", "Javelin", "🗡️", "Weapon", LootStat.STR, 2, "A light spear designed for throwing. Good range for a simple weapon."),
        LootDef("kit_spear", "Spear", "🗡️", "Weapon", LootStat.STR, 2, "A simple polearm with a pointed tip. Can be thrown or used in melee."),
        LootDef("kit_flail", "Flail", "🪓", "Weapon", LootStat.STR, 2, "A spiked ball on a chain. Difficult to parry."),
        LootDef("kit_morningstar", "Morningstar", "🗡️", "Weapon", LootStat.STR, 2, "A spiked club that deals piercing damage. Brutal and effective."),
        LootDef("kit_glaive", "Glaive", "⚔️", "Weapon", LootStat.STR, 3, "A blade on a long pole. Excellent reach in combat."),
        LootDef("kit_halberd", "Halberd", "⚔️", "Weapon", LootStat.STR, 3, "An axe-blade on a pole with a spike. Versatile polearm."),
        LootDef("kit_1_longsword", "+1 Longsword", "⚔️", "Weapon", LootStat.STR, 4, "Enchanted longsword — the steel corrects your aim."),
        LootDef("kit_1_shortsword", "+1 Shortsword", "🗡️", "Weapon", LootStat.DEX, 4, "Enchanted shortsword — the steel corrects your aim."),
        LootDef("kit_1_greatsword", "+1 Greatsword", "⚔️", "Weapon", LootStat.STR, 4, "Enchanted greatsword — the steel corrects your aim."),
        LootDef("kit_1_rapier", "+1 Rapier", "🗡️", "Weapon", LootStat.DEX, 4, "Enchanted rapier — the steel corrects your aim."),
        LootDef("kit_1_battleaxe", "+1 Battleaxe", "⚔️", "Weapon", LootStat.STR, 4, "Enchanted battleaxe — the steel corrects your aim."),
        LootDef("kit_1_longbow", "+1 Longbow", "🗡️", "Weapon", LootStat.DEX, 4, "Enchanted longbow — the steel corrects your aim."),
        LootDef("kit_1_warhammer", "+1 Warhammer", "🪓", "Weapon", LootStat.STR, 4, "Enchanted warhammer — the steel corrects your aim."),
        LootDef("kit_1_mace", "+1 Mace", "🪓", "Weapon", LootStat.STR, 4, "Enchanted mace — the steel corrects your aim."),
        LootDef("kit_2_longsword", "+2 Longsword", "⚔️", "Weapon", LootStat.STR, 5, "Enchanted longsword — the steel corrects your aim."),
        LootDef("kit_2_greatsword", "+2 Greatsword", "⚔️", "Weapon", LootStat.STR, 5, "Enchanted greatsword — the steel corrects your aim."),
        LootDef("kit_2_rapier", "+2 Rapier", "🗡️", "Weapon", LootStat.DEX, 5, "Enchanted rapier — the steel corrects your aim."),
        LootDef("kit_2_battleaxe", "+2 Battleaxe", "⚔️", "Weapon", LootStat.STR, 5, "Enchanted battleaxe — the steel corrects your aim."),
        LootDef("kit_2_longbow", "+2 Longbow", "🗡️", "Weapon", LootStat.DEX, 5, "Enchanted longbow — the steel corrects your aim."),
        LootDef("kit_3_longsword", "+3 Longsword", "⚔️", "Weapon", LootStat.STR, 6, "Enchanted longsword — the steel corrects your aim."),
        LootDef("kit_3_greatsword", "+3 Greatsword", "⚔️", "Weapon", LootStat.STR, 6, "Enchanted greatsword — the steel corrects your aim."),
        LootDef("kit_3_rapier", "+3 Rapier", "🗡️", "Weapon", LootStat.DEX, 6, "Enchanted rapier — the steel corrects your aim."),
        LootDef("kit_flaming_sword", "Flaming Sword", "⚔️", "Weapon", LootStat.STR, 2, "A longsword wreathed in magical flame. +1 weapon, +1d6 fire damage."),
        LootDef("kit_frost_bow", "Frost Bow", "🗡️", "Weapon", LootStat.DEX, 2, "A longbow that chills the air. +1 weapon, +1d6 cold damage."),
        LootDef("kit_robes", "Robes", "🧥", "Cloak", LootStat.CON, 2, "Armour of the plainer sort. AC 10."),
        LootDef("kit_padded_armor", "Padded Armor", "🥼", "Chest", LootStat.CON, 2, "Quilted layers of cloth. Better than nothing."),
        LootDef("kit_leather_armor", "Leather Armor", "🥼", "Chest", LootStat.CON, 2, "Cured leather protection. Light and flexible."),
        LootDef("kit_studded_leather", "Studded Leather", "🥼", "Chest", LootStat.CON, 2, "Leather reinforced with metal rivets. Good protection, still flexible."),
        LootDef("kit_hide_armor", "Hide Armor", "🥼", "Chest", LootStat.CON, 2, "Thick animal hides. Crude but effective."),
        LootDef("kit_chain_shirt", "Chain Shirt", "🥼", "Chest", LootStat.CON, 2, "A shirt of interlocking metal rings. Good balance of protection and mobility."),
        LootDef("kit_scale_mail", "Scale Mail", "🥼", "Chest", LootStat.CON, 3, "Overlapping metal scales. Solid medium armor."),
        LootDef("kit_breastplate", "Breastplate", "🥼", "Chest", LootStat.CON, 3, "A fitted metal chestpiece. Excellent protection without bulk."),
        LootDef("kit_half_plate", "Half Plate", "🥼", "Chest", LootStat.CON, 3, "A breastplate with arm and leg guards. Heavy but protective."),
        LootDef("kit_ring_mail", "Ring Mail", "🥼", "Chest", LootStat.CON, 3, "Leather with metal rings sewn on. Budget heavy armor."),
        LootDef("kit_chain_mail", "Chain Mail", "🥼", "Chest", LootStat.CON, 4, "Full body chainmail. Classic knight's armor."),
        LootDef("kit_splint_armor", "Splint Armor", "🥼", "Chest", LootStat.CON, 4, "Metal strips riveted to leather. Strong heavy armor."),
        LootDef("kit_plate_armor", "Plate Armor", "🥼", "Chest", LootStat.CON, 5, "Full plate protection. The ultimate in defense."),
        LootDef("kit_shield", "Shield", "🛡️", "Shield", LootStat.CON, 2, "A standard wooden shield. +2 AC when equipped."),
        LootDef("kit_tower_shield", "Tower Shield", "🛡️", "Shield", LootStat.CON, 3, "A large shield offering extra cover. +3 AC when equipped."),
    )

    /** Indexed by tier, matching [CampaignGear.pools]. */
    val pools: List<List<String>> = listOf(
        // tier 0
        listOf(
            "kit_longsword", "kit_shortsword", "kit_battleaxe",
            "kit_handaxes", "kit_rapier", "kit_scimitar",
            "kit_dagger", "kit_mace", "kit_warhammer",
            "kit_quarterstaff", "kit_longbow", "kit_shortbow",
            "kit_light_crossbow", "kit_hand_crossbow", "kit_javelin",
            "kit_spear", "kit_flail", "kit_morningstar",
            "kit_flaming_sword", "kit_frost_bow", "kit_robes",
            "kit_padded_armor", "kit_leather_armor", "kit_studded_leather",
            "kit_hide_armor", "kit_chain_shirt", "kit_shield",
        ),
        // tier 1
        listOf(
            "kit_greatsword", "kit_greataxe", "kit_glaive",
            "kit_halberd", "kit_scale_mail", "kit_breastplate",
            "kit_half_plate", "kit_ring_mail", "kit_tower_shield",
        ),
        // tier 2
        listOf(
            "kit_1_longsword", "kit_1_shortsword", "kit_1_greatsword",
            "kit_1_rapier", "kit_1_battleaxe", "kit_1_longbow",
            "kit_1_warhammer", "kit_1_mace", "kit_chain_mail",
            "kit_splint_armor",
        ),
        // tier 3
        listOf(
            "kit_2_longsword", "kit_2_greatsword", "kit_2_rapier",
            "kit_2_battleaxe", "kit_2_longbow", "kit_plate_armor",
        ),
        // tier 4
        listOf(
            "kit_3_longsword", "kit_3_greatsword", "kit_3_rapier",
        ),
    )
}
