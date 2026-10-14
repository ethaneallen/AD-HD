package com.addhd.app.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the loot drops against the two defects behind "I keep getting the same crappy items".
 *
 * Both were silent — no crash, no error, just a reward that quietly stopped being a reward:
 *  1. The seed is the hero's lifetime XP, which climbs ~30 per win. Taken modulo ticket space
 *     directly, consecutive drops landed inside the same 60-ticket common block and returned the
 *     identical item several times running.
 *  2. With a 16-piece catalogue, a long-running save owns everything, and the old
 *     `unowned.ifEmpty { pool }` fallback then returned the FULL pool — where commons hold 60 of
 *     every 100 tickets — so 60% of every later drop was one of the same four cave commons.
 */
class LootTableTest {

    /** Reproduces the real call pattern: seed = lifetime XP, advancing one win at a time. */
    private fun run(drops: Int, tier: Int, startSeed: Int = 11_486, step: Int = 30): List<LootDef> {
        val owned = mutableSetOf<String>()
        var seed = startSeed
        return (0 until drops).map {
            val d = LootTable.rollDrop(tier, seed, owned)
            owned += d.id
            seed += step
            d
        }
    }

    @Test
    fun catalogueIsBigEnoughToStayInteresting() {
        // 16 hand-written pieces + 51 named campaign items + the 55-piece base 5e arsenal.
        assertTrue("catalogue is ${LootTable.allItems.size}", LootTable.allItems.size >= 120)
        assertEquals(51, CampaignGear.items.size)
        assertEquals(5, CampaignGear.pools.size)
        assertEquals(55, GenericGear.items.size)
        assertEquals(5, GenericGear.pools.size)
    }

    @Test
    fun everyGenericGearIdResolves() {
        GenericGear.pools.flatten().forEach {
            assertTrue("dangling id $it", LootTable.allItems.containsKey(it))
        }
        // Every item must be reachable from some pool, or it can never drop.
        val pooled = (CampaignGear.pools + GenericGear.pools).flatten().toSet()
        GenericGear.items.forEach {
            assertTrue("${it.name} is in no pool", it.id in pooled)
        }
    }

    /**
     * The magic weapons are the only genuinely top-tier arms in the source data, so they must not
     * land in the common bucket — a `+3 Greatsword` rolling as often as a Dagger would make the
     * whole rarity curve meaningless.
     */
    @Test
    fun magicWeaponsSitAtTheTopOfTheCurve() {
        val plus = GenericGear.items.filter { it.name.startsWith("+") }
        // 8 at +1, 5 at +2, 3 at +3.
        assertEquals("expected the +1/+2/+3 variants", 16, plus.size)
        plus.forEach {
            val r = LootTable.rarityOf(it)
            assertTrue(
                "${it.name} rolled as $r",
                r in listOf(Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY),
            )
        }
        // And the enchantment level must order them.
        fun bonusOf(n: String) = GenericGear.items.first { it.name == n }.bonus
        assertTrue(bonusOf("+1 Longsword") < bonusOf("+2 Longsword"))
        assertTrue(bonusOf("+2 Longsword") < bonusOf("+3 Longsword"))
    }

    /** A fist is not loot. */
    @Test
    fun unarmedIsNotAnItem() {
        assertTrue(GenericGear.items.none { it.name.equals("Unarmed", ignoreCase = true) })
    }

    @Test
    fun genericGearIsDescribedAndSanelyTiered() {
        GenericGear.items.forEach {
            assertTrue("blank name", it.name.isNotBlank())
            assertTrue("no flavour on ${it.name}", it.flavor.isNotBlank())
            assertTrue("bad bonus on ${it.name}", it.bonus in 1..6)
            assertTrue("bad slot on ${it.name}", it.slot in listOf("Weapon", "Chest", "Cloak", "Shield"))
        }
    }

    @Test
    fun everyCampaignGearIdResolves() {
        CampaignGear.pools.flatten().forEach {
            assertTrue("dangling id $it", LootTable.allItems.containsKey(it))
        }
    }

    @Test
    fun campaignGearHasRealNamesAndFlavour() {
        CampaignGear.items.forEach {
            assertTrue("blank name", it.name.isNotBlank())
            assertTrue("blank flavour on ${it.name}", it.flavor.isNotBlank())
            assertTrue("bad bonus on ${it.name}", it.bonus in 1..6)
        }
    }

    /** The headline symptom: the same piece handed over twice in a row. */
    @Test
    fun consecutiveDropsAreNotTheSameItem() {
        val got = run(drops = 20, tier = 3)
        got.zipWithNext().forEachIndexed { i, (a, b) ->
            assertNotEquals("drops ${i + 1} and ${i + 2} are both ${a.name}", a.id, b.id)
        }
    }

    /** A walking seed must not produce a near-constant result. */
    @Test
    fun aRunOfDropsIsGenuinelyVaried() {
        val got = run(drops = 20, tier = 3)
        val distinct = got.map { it.id }.distinct().size
        assertTrue("only $distinct distinct items in 20 drops", distinct >= 15)
    }

    @Test
    fun dropsAreNotAllCommons() {
        val got = run(drops = 24, tier = 4)
        val commons = got.count { LootTable.rarityOf(it) == Rarity.COMMON }
        assertTrue("$commons of 24 drops were commons", commons < 16)
    }

    /**
     * Duplicates are unavoidable once everything is owned, but they must at least be worth
     * selling rather than the cheapest four items in the game.
     */
    @Test
    fun duplicatesFallBackToTheCurrentTierNotTheCheapestCommons() {
        val everything = LootTable.allItems.keys.toSet()
        val dupes = (0 until 40).map { LootTable.rollDrop(4, 11_486 + it * 30, everything) }
        val lowest = dupes.count { it.bonus <= 2 }
        assertTrue("$lowest of 40 forced duplicates were bottom-tier commons", lowest == 0)
    }

    /**
     * [Campaign.lootTierFor] yields 0..4, so tier 4 must reach Chult gear rather than collapsing
     * back onto Barovia's pool.
     *
     * Asserted by POOL MEMBERSHIP, not by sampling. The first version of this test drew 30 times
     * and hoped a Chult piece appeared — but all 12 are Legendary at one ticket each against a
     * ~700-ticket pool, so it only had a ~40% chance of passing and had been succeeding on the
     * luck of its seeds. Marking everything else as owned forces the draw into the Chult subset,
     * which tests the thing the name claims.
     */
    @Test
    fun theFifthTierHasItsOwnPool() {
        val allButChult = LootTable.allItems.keys.filterNot { it.startsWith("chult_") }.toSet()
        val atTier4 = LootTable.rollDrop(4, 12_345, allButChult)
        assertTrue("tier 4 cannot reach Chult gear, got ${atTier4.id}", atTier4.id.startsWith("chult_"))

        // Tier 3 must NOT contain it — Chult is the fifth setting.
        val atTier3 = LootTable.rollDrop(3, 12_345, allButChult)
        assertTrue("Chult gear leaked into tier 3", !atTier3.id.startsWith("chult_"))
    }

    /**
     * The mundane 5e kit is scoped to its own tier rather than being cumulative. Merging its 27
     * low-tier commons into every tier pushed commons from 46% to 68% of all draws.
     */
    @Test
    fun mundaneKitDoesNotFollowYouUpTheTiers() {
        val lowKit = GenericGear.pools[0].toSet()
        val drops = (0 until 60).map { LootTable.rollDrop(4, 7_919 + it * 31, emptySet()).id }
        assertTrue(
            "starter kit is still dropping at tier 4: ${drops.filter { it in lowKit }.distinct()}",
            drops.none { it in lowKit },
        )
    }

    /** Still deterministic — the roll has to survive process death mid-award. */
    @Test
    fun rollsRemainDeterministic() {
        assertEquals(
            LootTable.rollDrop(2, 12_345, emptySet()).id,
            LootTable.rollDrop(2, 12_345, emptySet()).id,
        )
    }

    // ---- Combat flavour ----

    @Test
    fun bossArchetypesMapSensibly() {
        assertEquals("lich", CombatFlavor.archetypeFor("Acererak"))
        assertEquals("vampire", CombatFlavor.archetypeFor("Strahd von Zarovich"))
        assertEquals("dragon", CombatFlavor.archetypeFor("Young Green Dragon"))
        assertEquals("giant", CombatFlavor.archetypeFor("Troll"))
        assertEquals("beast", CombatFlavor.archetypeFor("Dire Wolf"))
        assertEquals("humanoid", CombatFlavor.archetypeFor("Hobgoblin Captain"))
    }

    @Test
    fun everyBestiaryBossGetsANonEmptyRiposte() {
        Bestiary.quests.forEach { q ->
            val line = CombatFlavor.legendaryAction(q.boss, 3)
            assertTrue("${q.boss} has no legendary action", line.isNotBlank())
        }
    }

    @Test
    fun ripostesVaryAcrossExchanges() {
        val lines = (0 until 8).map { CombatFlavor.legendaryAction("Acererak", it) }.distinct()
        assertTrue("boss repeats itself every exchange", lines.size > 1)
    }
}
