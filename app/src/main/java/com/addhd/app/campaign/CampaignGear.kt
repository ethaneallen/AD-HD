package com.addhd.app.campaign

/**
 * Campaign gear, GENERATED from the author's own D&D game (`C:/webapp/dnd-game-web`,
 * GAME_DATA.campaignItems), so the dungeon, the Tavern and the loot all share one canon.
 *
 * Why this exists: the hand-written catalogue in [LootTable] held 16 pieces. Once a hero owned all
 * 16, `rollDrop`'s `unowned.ifEmpty { pool }` fallback handed back the FULL pool, so every drop
 * after that was a duplicate — and since commons carry 60 of every 100 weighted tickets, 60% of
 * those duplicates were the same four cave commons. On a save with 574 rooms cleared, that is
 * exactly the reported symptom: the same crappy items, forever.
 *
 * Tiers follow [Bestiary]'s quest order so a setting's gear drops in that setting. A piece listed
 * in its campaign's `bossLoot` is worth a tier more than a common find, because `bonus` IS the
 * rarity here (see [LootTable.rarityOf]).
 */
object CampaignGear {

    val items: List<LootDef> = listOf(
        LootDef("borderlands_castellan_s_blade", "Castellan's Blade", "⚔️", "Weapon", LootStat.STR, 3, "A +1 longsword gifted by the Castellan for exceptional service."),
        LootDef("borderlands_orc_cleaver", "Orc Cleaver", "⚔️", "Weapon", LootStat.STR, 2, "A brutal two-handed weapon taken from an orc chieftain."),
        LootDef("borderlands_kobold_crossbow", "Kobold Crossbow", "🗡️", "Weapon", LootStat.DEX, 2, "A light crossbow rigged with a kobold hair-trigger."),
        LootDef("borderlands_chaos_mace", "Chaos Mace", "🪓", "Weapon", LootStat.STR, 3, "A +1 mace crackling with dark energy from the Temple of Evil Chaos."),
        LootDef("borderlands_goblin_shortbow", "Goblin Shortbow", "🗡️", "Weapon", LootStat.DEX, 2, "A crude but effective goblin shortbow."),
        LootDef("borderlands_guard_s_chain", "Guard's Chain", "🥼", "Chest", LootStat.CON, 2, "Standard-issue chain mail from the Keep's armory."),
        LootDef("borderlands_orc_hide_armor", "Orc Hide Armor", "🥼", "Chest", LootStat.CON, 2, "Crude armor pieced together from various hides by orcs."),
        LootDef("borderlands_temple_cult_robes", "Temple Cult Robes", "🥼", "Chest", LootStat.CON, 3, "Dark robes from the Temple of Evil Chaos. Faintly unsettling."),
        LootDef("borderlands_castellan_s_plate", "Castellan's Plate", "🥼", "Chest", LootStat.CON, 3, "Polished plate armor awarded to the Keep's greatest champion."),
        LootDef("borderlands_keep_shield", "Keep Shield", "🛡️", "Shield", LootStat.CON, 2, "A sturdy shield bearing the emblem of the Keep."),
        LootDef("swordcoast_talon", "Talon", "⚔️", "Weapon", LootStat.STR, 4, "A +1 longsword found in the Redbrand hideout. Its blade glows faintly blue."),
        LootDef("swordcoast_hew", "Hew", "⚔️", "Weapon", LootStat.STR, 4, "A +1 battleaxe made for a human hero long ago. It was used to clear forests. Extra damage vs plant creatures."),
        LootDef("swordcoast_lightbringer", "Lightbringer", "🪓", "Weapon", LootStat.STR, 4, "A +1 mace that glows bright as a torch on command. Extra 1d6 radiant damage vs undead."),
        LootDef("swordcoast_dragonguard_halberd", "Dragonguard Halberd", "⚔️", "Weapon", LootStat.STR, 4, "A +1 halberd etched with dragon imagery from Cragmaw Castle."),
        LootDef("swordcoast_spider_staff", "Spider Staff", "🪓", "Weapon", LootStat.STR, 4, "Nezznar's staff, carved to resemble black spider legs. +1 quarterstaff."),
        LootDef("swordcoast_redbrand_cloak", "Redbrand Cloak", "🥼", "Chest", LootStat.CON, 2, "Dyed red leather armor worn by Redbrand thugs."),
        LootDef("swordcoast_miner_s_breastplate", "Miner's Breastplate", "🥼", "Chest", LootStat.CON, 4, "Reinforced breastplate made for the miners of Wave Echo Cave."),
        LootDef("swordcoast_dragonguard", "Dragonguard", "🥼", "Chest", LootStat.CON, 4, "A suit of armor adorned with dragon motifs. Grants advantage on saves vs. dragon breath."),
        LootDef("swordcoast_phandalin_shield", "Phandalin Shield", "🛡️", "Shield", LootStat.CON, 2, "A sturdy wooden shield painted with the Phandalin coat of arms."),
        LootDef("karameikos_karameikan_saber", "Karameikan Saber", "⚔️", "Weapon", LootStat.DEX, 5, "A curved cavalry saber favored by Karameikan nobles."),
        LootDef("karameikos_goblin_cleaver", "Goblin Cleaver", "⚔️", "Weapon", LootStat.STR, 3, "A jagged blade designed for fighting goblins. +1 weapon, +1 extra damage vs goblinoids."),
        LootDef("karameikos_wolf_fang_dagger", "Wolf Fang Dagger", "🗡️", "Weapon", LootStat.DEX, 3, "A dagger made from a dire wolf's fang."),
        LootDef("karameikos_iron_ring_whip", "Iron Ring Whip", "⚔️", "Weapon", LootStat.DEX, 3, "A cruel weapon used by Iron Ring slavers."),
        LootDef("karameikos_hutaakan_khopesh", "Hutaakan Khopesh", "⚔️", "Weapon", LootStat.STR, 5, "An ancient curved blade from the Hutaakan ruins. A +1 magical weapon."),
        LootDef("karameikos_karameikan_scale", "Karameikan Scale", "🥼", "Chest", LootStat.CON, 5, "Scale armor decorated with the Karameikan eagle."),
        LootDef("karameikos_wolfskin_cloak", "Wolfskin Cloak", "🥼", "Chest", LootStat.CON, 3, "Armor made from dire wolf pelts. Grants cold resistance."),
        LootDef("karameikos_hutaakan_bronze_plate", "Hutaakan Bronze Plate", "🥼", "Chest", LootStat.CON, 5, "Ancient ceremonial armor from the Lost Valley."),
        LootDef("karameikos_sukiskyn_buckler", "Sukiskyn Buckler", "🛡️", "Shield", LootStat.CON, 3, "A small shield bearing the Sukiskyn horse emblem."),
        LootDef("barovia_silver_blade", "Silver Blade", "⚔️", "Weapon", LootStat.STR, 6, "A silvered longsword. +1 weapon. Full damage against undead and lycanthropes."),
        LootDef("barovia_vampire_hunter_s_stake", "Vampire Hunter's Stake", "🗡️", "Weapon", LootStat.DEX, 3, "A wooden stake blessed by the Morning Lord. Critical hits vs vampires are instant kills."),
        LootDef("barovia_barovian_crossbow", "Barovian Crossbow", "🗡️", "Weapon", LootStat.DEX, 3, "A heavy crossbow designed for hunting monsters."),
        LootDef("barovia_sunsword_replica", "Sunsword Replica", "✨", "Weapon", LootStat.STR, 6, "A blade that glows with faint sunlight. +2 weapon, +1d6 radiant vs undead."),
        LootDef("barovia_ravenloft_mace", "Ravenloft Mace", "🪓", "Weapon", LootStat.STR, 6, "A mace bearing the symbol of the Morning Lord. A +1 magical weapon."),
        LootDef("barovia_vistani_leathers", "Vistani Leathers", "🥼", "Chest", LootStat.CON, 3, "Supple leather armor worn by Vistani travelers."),
        LootDef("barovia_vampire_hunter_s_coat", "Vampire Hunter's Coat", "🥼", "Chest", LootStat.CON, 3, "A reinforced leather coat with hidden stakes and holy symbols."),
        LootDef("barovia_knight_of_the_order_armor", "Knight of the Order Armor", "🥼", "Chest", LootStat.CON, 6, "Plate armor from the Order of the Silver Dragon."),
        LootDef("barovia_strahd_s_dark_gift", "Strahd's Dark Gift", "🥼", "Chest", LootStat.CON, 3, "Cursed armor that grants power at a terrible cost."),
        LootDef("barovia_holy_shield", "Holy Shield", "🛡️", "Shield", LootStat.CON, 3, "A shield emblazoned with the symbol of the Morning Lord. Glows faintly."),
        LootDef("barovia_silver_dragon_shield", "Silver Dragon Shield", "🛡️", "Shield", LootStat.CON, 6, "A shield from the Order of the Silver Dragon."),
        LootDef("chult_chultan_machete", "Chultan Machete", "⚔️", "Weapon", LootStat.DEX, 4, "A jungle blade essential for cutting through vegetation."),
        LootDef("chult_dinosaur_bone_club", "Dinosaur Bone Club", "🪓", "Weapon", LootStat.STR, 4, "A massive club made from a T-Rex femur."),
        LootDef("chult_yklwa", "Yklwa", "🗡️", "Weapon", LootStat.STR, 4, "A traditional Chultan short spear."),
        LootDef("chult_pterafolk_wing_blade", "Pterafolk Wing Blade", "⚔️", "Weapon", LootStat.DEX, 4, "A curved blade made from pterafolk wing bones."),
        LootDef("chult_trickster_god_blade", "Trickster God Blade", "⚔️", "Weapon", LootStat.STR, 6, "A blade touched by the Nine Gods. +2 weapon. Unpredictable magic."),
        LootDef("chult_yuan_ti_fang_sword", "Yuan-ti Fang Sword", "🗡️", "Weapon", LootStat.DEX, 6, "A serpentine blade coated with poison. +1 weapon, extra 1d4 poison damage on hit."),
        LootDef("chult_jungle_explorer_s_gear", "Jungle Explorer's Gear", "🥼", "Chest", LootStat.CON, 4, "Light armor designed for jungle travel. Resists humidity."),
        LootDef("chult_dinosaur_hide_armor", "Dinosaur Hide Armor", "🥼", "Chest", LootStat.CON, 6, "Armor crafted from dinosaur scales."),
        LootDef("chult_yuan_ti_scale_mail", "Yuan-ti Scale Mail", "🥼", "Chest", LootStat.CON, 6, "Armor made from shed yuan-ti scales."),
        LootDef("chult_tomb_guardian_plate", "Tomb Guardian Plate", "🥼", "Chest", LootStat.CON, 6, "Ancient armor from the Tomb of the Nine Gods."),
        LootDef("chult_turtle_shell_shield", "Turtle Shell Shield", "🛡️", "Shield", LootStat.CON, 4, "A shield made from a giant turtle shell."),
        LootDef("chult_omu_relic_shield", "Omu Relic Shield", "🛡️", "Shield", LootStat.CON, 6, "An ancient shield bearing symbols of the Nine Gods."),
    )

    /** One pool per setting, ordered to match [Bestiary]'s five tiers. */
    val pools: List<List<String>> = listOf(
        // keep_on_borderlands
        listOf(
            "borderlands_castellan_s_blade", "borderlands_orc_cleaver", "borderlands_kobold_crossbow",
            "borderlands_chaos_mace", "borderlands_goblin_shortbow", "borderlands_guard_s_chain",
            "borderlands_orc_hide_armor", "borderlands_temple_cult_robes", "borderlands_castellan_s_plate",
            "borderlands_keep_shield",
        ),
        // lost_mine_of_phandelver
        listOf(
            "swordcoast_talon", "swordcoast_hew", "swordcoast_lightbringer",
            "swordcoast_dragonguard_halberd", "swordcoast_spider_staff", "swordcoast_redbrand_cloak",
            "swordcoast_miner_s_breastplate", "swordcoast_dragonguard", "swordcoast_phandalin_shield",
        ),
        // nights_dark_terror
        listOf(
            "karameikos_karameikan_saber", "karameikos_goblin_cleaver", "karameikos_wolf_fang_dagger",
            "karameikos_iron_ring_whip", "karameikos_hutaakan_khopesh", "karameikos_karameikan_scale",
            "karameikos_wolfskin_cloak", "karameikos_hutaakan_bronze_plate", "karameikos_sukiskyn_buckler",
        ),
        // curse_of_strahd
        listOf(
            "barovia_silver_blade", "barovia_vampire_hunter_s_stake", "barovia_barovian_crossbow",
            "barovia_sunsword_replica", "barovia_ravenloft_mace", "barovia_vistani_leathers",
            "barovia_vampire_hunter_s_coat", "barovia_knight_of_the_order_armor", "barovia_strahd_s_dark_gift",
            "barovia_holy_shield", "barovia_silver_dragon_shield",
        ),
        // tomb_of_annihilation
        listOf(
            "chult_chultan_machete", "chult_dinosaur_bone_club", "chult_yklwa",
            "chult_pterafolk_wing_blade", "chult_trickster_god_blade", "chult_yuan_ti_fang_sword",
            "chult_jungle_explorer_s_gear", "chult_dinosaur_hide_armor", "chult_yuan_ti_scale_mail",
            "chult_tomb_guardian_plate", "chult_turtle_shell_shield", "chult_omu_relic_shield",
        ),
    )
}
