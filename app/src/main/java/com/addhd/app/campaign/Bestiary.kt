package com.addhd.app.campaign

/**
 * The room-quest bestiary — 25 quests, their bosses and their enemy pools.
 *
 * GENERATED from the author's own D&D game (`C:/webapp/dnd-game-web`, CAMPAIGNS.monsters, five
 * campaigns x five tiers) rather than hand-invented, so the Tavern and the dungeon share one
 * canon. Regenerate by re-running that project's `extract-gamedata.js` and re-deriving this file.
 *
 * Replaces four hardcoded quests and ten hardcoded enemy names. At one room per completed quest
 * that is 250 rooms before any repeat, where the old list looped every 40 — the Goblin King had
 * already been killed fourteen times on the author's own save.
 *
 * Boss HP is the real 5e statblock halved. Halving (rather than scaling to the hero) keeps the
 * fight honest: a stronger hero genuinely kills faster, because boss damage is 5 + 2 x STR and STR
 * counts lifetime medium/big quests. Wanting to fell Strahd in fewer swings is a reason to take on
 * a bigger quest, which is exactly the behaviour worth rewarding.
 */
object Bestiary {

    data class RoomQuest(
        val name: String,
        val boss: String,
        /** Real 5e HP, halved. The hero chips this down one completed quest at a time. */
        val bossMaxHp: Int,
        /** Rank-and-file encountered in rooms 1..9 of this quest. */
        val enemies: List<String>,
    )

    val quests: List<RoomQuest> = listOf(
        RoomQuest(
            name = "The Borderlands: Approach",
            boss = "Goblin",
            bossMaxHp = 20,
            enemies = listOf("Kobold", "Giant Rat", "Stirge", "Fire Beetle", "Shrieker", "Giant Centipede", "Pit Viper", "Giant Crayfish"),
        ),
        RoomQuest(
            name = "The Sword Coast: Approach",
            boss = "Giant Frog",
            bossMaxHp = 20,
            enemies = listOf("Goblin", "Wolf", "Redbrand Ruffian", "Stirge", "Twig Blight", "Cragmaw Rat", "Needle Blight"),
        ),
        RoomQuest(
            name = "Karameikos: Approach",
            boss = "Rock Baboon",
            bossMaxHp = 20,
            enemies = listOf("Goblin Scout", "Wolf", "Bandit", "Giant Ferret", "Giant Bat", "Robber Fly", "Stirge"),
        ),
        RoomQuest(
            name = "Barovia: Approach",
            boss = "Scarecrow",
            bossMaxHp = 20,
            enemies = listOf("Zombie", "Swarm of Bats", "Strahd Zombie", "Twig Blight", "Shadow", "Crawling Claw", "Swarm of Rats"),
        ),
        RoomQuest(
            name = "Chult: Approach",
            boss = "Deinonychus",
            bossMaxHp = 20,
            enemies = listOf("Velociraptor", "Zombie", "Grung", "Giant Wasp", "Jaculi", "Batiri Goblin", "Zorbo", "Flying Snake", "Chwinga"),
        ),
        RoomQuest(
            name = "The Borderlands: Outskirts",
            boss = "Carrion Crawler",
            bossMaxHp = 20,
            enemies = listOf("Orc", "Hobgoblin", "Giant Spider", "Zombie", "Skeleton", "Lizardfolk", "Gray Ooze", "Piercer", "Berserker"),
        ),
        RoomQuest(
            name = "The Sword Coast: Outskirts",
            boss = "Ochre Jelly",
            bossMaxHp = 22,
            enemies = listOf("Redbrand Guard", "Hobgoblin", "Bugbear", "Skeleton", "Zombie", "Ghoul", "Orc", "Ash Zombie", "Giant Spider", "Cragmaw Goblin Boss", "Vine Blight"),
        ),
        RoomQuest(
            name = "Karameikos: Outskirts",
            boss = "Dire Wolf",
            bossMaxHp = 20,
            enemies = listOf("Goblin Wolf-Rider", "Goblin Warrior", "Iron Ring Thug", "Boar", "Swarm of Bats", "Gnoll", "Thoul", "Giant Lizard", "Goblin Archer"),
        ),
        RoomQuest(
            name = "Barovia: Outskirts",
            boss = "Wight",
            bossMaxHp = 22,
            enemies = listOf("Ghoul", "Dire Wolf", "Specter", "Ghast", "Animated Armor", "Needle Blight", "Vistani Thug", "Rug of Smothering", "Mongrelfolk", "Vine Blight"),
        ),
        RoomQuest(
            name = "Chult: Outskirts",
            boss = "Giant Constrictor Snake",
            bossMaxHp = 30,
            enemies = listOf("Allosaurus", "Ghoul", "Su-monster", "Pterafolk", "Hadrosaurus", "Vegepygmy", "Mantrap", "Aldani", "Crocodile", "Swarm of Poisonous Snakes"),
        ),
        RoomQuest(
            name = "The Borderlands: Deep Halls",
            boss = "Gelatinous Cube",
            bossMaxHp = 42,
            enemies = listOf("Gnoll", "Bugbear", "Ogre", "Orc Chieftain", "Acolyte of Chaos", "Cultist Warrior", "Gnoll Fang of Yeenoghu", "Orc Eye of Gruumsh"),
        ),
        RoomQuest(
            name = "The Sword Coast: Deep Halls",
            boss = "Young Green Dragon",
            bossMaxHp = 68,
            enemies = listOf("Glasstaff", "Nothic", "Grick", "Owlbear", "Ogre", "Druid of the Circle", "Redbrand Captain"),
        ),
        RoomQuest(
            name = "Karameikos: Deep Halls",
            boss = "Wyvern",
            bossMaxHp = 26,
            enemies = listOf("Goblin Shaman", "Bugbear", "Werewolf", "Iron Ring Assassin", "Brown Bear", "Pixie", "Decapus", "Troglodyte"),
        ),
        RoomQuest(
            name = "Barovia: Deep Halls",
            boss = "Night Hag",
            bossMaxHp = 41,
            enemies = listOf("Vampire Spawn", "Werewolf", "Revenant", "Phantom Warrior", "Will-o'-Wisp", "Banshee", "Izek Strazni", "Druid of Yester Hill", "Guardian Portrait"),
        ),
        RoomQuest(
            name = "Chult: Deep Halls",
            boss = "King of Feathers",
            bossMaxHp = 68,
            enemies = listOf("Tyrannosaurus Rex", "Yuan-ti Malison", "Girallon Zombie", "Eblis", "Kamadan", "Assassin Vine", "Triceratops", "Ankylosaurus", "Girallon", "Firenewt"),
        ),
        RoomQuest(
            name = "The Borderlands: Inner Sanctum",
            boss = "Medusa",
            bossMaxHp = 29,
            enemies = listOf("Minotaur", "Owlbear", "Troll", "Hobgoblin Captain", "Wyvern", "Ettin"),
        ),
        RoomQuest(
            name = "The Sword Coast: Inner Sanctum",
            boss = "Mormesk the Wraith",
            bossMaxHp = 37,
            enemies = listOf("King Grol", "Doppelganger", "Flameskull", "Wraith", "Hobgoblin Captain"),
        ),
        RoomQuest(
            name = "Karameikos: Inner Sanctum",
            boss = "Troll",
            bossMaxHp = 26,
            enemies = listOf("King Xitaqa", "Xitaqa's Warg", "Iron Ring Captain", "Hill Giant", "Chevall", "Sasquatch"),
        ),
        RoomQuest(
            name = "Barovia: Inner Sanctum",
            boss = "Wintersplinter",
            bossMaxHp = 46,
            enemies = listOf("Baba Lysaga", "Rahadin", "Nightmare", "Flesh Golem", "Strahd's Animated Armor", "Strahd's Bride"),
        ),
        RoomQuest(
            name = "Chult: Inner Sanctum",
            boss = "Froghemoth",
            bossMaxHp = 92,
            enemies = listOf("Ras Nsi", "Yuan-ti Abomination", "Bodak", "Nycaloth", "Flail Snail", "Sewn Sister", "Tomb Dwarf"),
        ),
        RoomQuest(
            name = "The Borderlands: Final Stand",
            boss = "Chaos Chimera",
            bossMaxHp = 34,
            enemies = listOf("High Priest of Chaos", "Temple Guardian", "Shadow Demon", "Wraith"),
        ),
        RoomQuest(
            name = "The Sword Coast: Final Stand",
            boss = "Nezznar the Black Spider",
            bossMaxHp = 27,
            enemies = listOf("Giant Spider", "Drow", "Spectator", "Black Spider's Drow Elite", "Wave Echo Specter"),
        ),
        RoomQuest(
            name = "Karameikos: Final Stand",
            boss = "Iron Ring Slaver Lord",
            bossMaxHp = 30,
            enemies = listOf("Golthar the Wizard", "Hutaakan Guardian", "Shadow", "Living Statue"),
        ),
        RoomQuest(
            name = "Barovia: Final Stand",
            boss = "Strahd von Zarovich",
            bossMaxHp = 72,
            enemies = listOf("Amber Golem", "Death Knight", "Beucephalus", "Ezmerelda's Revenant"),
        ),
        RoomQuest(
            name = "Chult: Final Stand",
            boss = "Acererak",
            bossMaxHp = 142,
            enemies = listOf("Atropal", "Tomb Guardian", "Soulmonger", "Beholder Zombie", "Withers", "Gray Slaad"),
        ),
    )
}
