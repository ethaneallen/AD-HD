package com.addhd.app.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the room/quest progression and the room-10 boss fight.
 *
 * These are worth pinning because the failures are silent: an off-by-one in [Campaign.roomInQuest]
 * either skips the boss entirely or traps the hero on room 10 forever, and a loot index that
 * clamps makes 21 of the 25 quests drop identical gear. Neither throws.
 */
class CampaignProgressionTest {

    // ---- Rooms are a real counter now ----

    @Test
    fun roomRunsOneToTenThenWrapsToTheNextQuest() {
        assertEquals(1, Campaign.roomInQuest(0))
        assertEquals(10, Campaign.roomInQuest(9))
        assertEquals(1, Campaign.roomInQuest(10))
        assertEquals(0, Campaign.questIndex(9))
        assertEquals(1, Campaign.questIndex(10))
    }

    @Test
    fun questsCycleOnlyAfterTheWholeBestiary() {
        val n = Campaign.questCount
        assertEquals(25, n)
        assertEquals(0, Campaign.questIndex(0))
        assertEquals(n - 1, Campaign.questIndex((n - 1) * Campaign.TOTAL_ROOMS))
        // 250 rooms before the first repeat, where the old 4-quest list looped every 40.
        assertEquals(0, Campaign.questIndex(n * Campaign.TOTAL_ROOMS))
    }

    @Test
    fun negativeOrZeroCounterIsSafe() {
        assertEquals(1, Campaign.roomInQuest(-5))
        assertEquals(0, Campaign.questIndex(-5))
    }

    // ---- The bestiary ----

    @Test
    fun everyQuestHasABossWithHpAndItsOwnEnemies() {
        Bestiary.quests.forEachIndexed { i, q ->
            assertTrue("quest $i has no name", q.name.isNotBlank())
            assertTrue("quest $i boss has no name", q.boss.isNotBlank())
            assertTrue("quest $i boss has no HP", q.bossMaxHp >= 20)
            assertTrue("quest $i has no rank-and-file", q.enemies.isNotEmpty())
            assertTrue("quest $i lists its boss as cannon fodder", q.boss !in q.enemies)
        }
    }

    @Test
    fun bossesGetToughersAcrossTiers() {
        // Tier 1 (quests 0-4) must not be harder than the final tier (quests 20-24).
        val firstTier = (0..4).maxOf { Bestiary.quests[it].bossMaxHp }
        val lastTier = (20..24).maxOf { Bestiary.quests[it].bossMaxHp }
        assertTrue("final-tier bosses ($lastTier) should out-HP tier 1 ($firstTier)", lastTier > firstTier)
    }

    @Test
    fun enemiesComeFromTheCurrentQuestsPool() {
        // Room enemies must belong to the setting you're actually in.
        for (questIdx in listOf(0, 7, 13, 24)) {
            val rooms = questIdx * Campaign.TOTAL_ROOMS
            val pool = Campaign.currentQuest(rooms).enemies
            for (seed in 0..30) {
                assertTrue(
                    "quest $questIdx produced an out-of-setting enemy",
                    Campaign.enemyFor(seed, rooms) in pool,
                )
            }
        }
    }

    @Test
    fun differentQuestsDrawDifferentBestiaries() {
        val early = Campaign.currentQuest(0).enemies.toSet()
        val late = Campaign.currentQuest(24 * Campaign.TOTAL_ROOMS).enemies.toSet()
        assertNotEquals("the bestiary should change across the campaign", early, late)
    }

    // ---- Loot pools ----

    @Test
    fun lootTierTracksTheBestiaryRatherThanClamping() {
        // 25 quests over 5 tiers; passing the raw index would pin quests 4..24 to one pool.
        assertEquals(0, Campaign.lootTierFor(0))
        assertEquals(0, Campaign.lootTierFor(4))
        assertEquals(1, Campaign.lootTierFor(5))
        assertEquals(4, Campaign.lootTierFor(24))
        assertNotEquals(Campaign.lootTierFor(4), Campaign.lootTierFor(10))
    }

    // ---- The boss fight ----

    /**
     * The point of the whole change: a boss must take several wins, so that boss damage — and
     * therefore STR, and therefore taking on bigger quests — actually matters.
     */
    @Test
    fun bossesTakeMoreThanOneHitForATypicalHero() {
        val typical = HeroStats(str = 3, dex = 15, con = 88, wis = 4).bossDamage
        assertEquals(11, typical)
        val oneHitKills = Bestiary.quests.count { it.bossMaxHp <= typical }
        assertTrue("$oneHitKills bosses die to a single win; the climax must last", oneHitKills == 0)
    }

    // ---- The dungeon feeds the campaign ----

    /**
     * A chapter must cost ONE dungeon quest, not five individual tasks.
     *
     * Every chapter in campaigns.json authors `tasksRequired = 5`. Used literally as a
     * dungeon-quest gate that would be 50 rooms and 5 boss kills per chapter — roughly 1,800
     * rooms for the 36 authored chapters.
     */
    @Test
    fun chapterGateIsOneDungeonQuestForAuthoredChapters() {
        assertEquals(1, WorldTier.chapterGateInQuests(5))
        assertEquals(1, WorldTier.chapterGateInQuests(1))
        assertEquals(1, WorldTier.chapterGateInQuests(10))
        assertEquals(2, WorldTier.chapterGateInQuests(11))
        assertEquals(2, WorldTier.chapterGateInQuests(20))
    }

    @Test
    fun chapterGateNeverFallsBelowOne() {
        assertEquals(1, WorldTier.chapterGateInQuests(0))
        assertEquals(1, WorldTier.chapterGateInQuests(-5))
    }

    /**
     * World Tier still scales the chapter, on top of the quest conversion — the gate has to stay
     * reachable rather than exploding once it is counted in dungeon quests.
     */
    @Test
    fun worldTierScalesTheChapterGateSanely() {
        val base = WorldTier.chapterGateInQuests(5)
        for (tier in 1..5) {
            val gate = WorldTier.effectiveGate(base, tier, 0)
            assertTrue("tier $tier gate was $gate", gate in 1..4)
        }
    }

    @Test
    fun strongerHeroesFellBossesFaster() {
        val weak = HeroStats(str = 0, dex = 0, con = 0, wis = 0).bossDamage
        val strong = HeroStats(str = 40, dex = 0, con = 0, wis = 0).bossDamage
        val hp = Bestiary.quests.last().bossMaxHp
        assertTrue("boss damage must scale with STR", strong > weak)
        assertTrue("a strong hero should need fewer wins", (hp / strong) < (hp / weak))
    }
}
