package com.addhd.app.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two gold sinks that are meant to be endless.
 *
 * Worth testing because both failure modes are silent and economic rather than crashy: a sink that
 * stops escalating quietly becomes farmable, and a betting table with non-negative expected value
 * is a gold *faucet* wearing a disguise. Neither shows up as a bug report — they just make every
 * coin in the app meaningless, which is the state this save had already reached.
 */
class EconomyTest {

    // ---- The bard's commission ----

    @Test
    fun bardPriceDoublesWithEachCommission() {
        assertEquals(1000, AppViewModel.bardCostFor(0))
        assertEquals(2000, AppViewModel.bardCostFor(1))
        assertEquals(4000, AppViewModel.bardCostFor(2))
        assertEquals(8000, AppViewModel.bardCostFor(3))
        assertEquals(16000, AppViewModel.bardCostFor(4))
    }

    /** Flat beyond the cap, so the price can't overflow into absurdity on a heavy month. */
    @Test
    fun bardPriceFlattensAtTheCap() {
        val capped = AppViewModel.bardCostFor(AppViewModel.BARD_MAX_DOUBLINGS)
        assertEquals(capped, AppViewModel.bardCostFor(AppViewModel.BARD_MAX_DOUBLINGS + 1))
        assertEquals(capped, AppViewModel.bardCostFor(99))
        assertTrue("price must stay positive", capped > 0)
    }

    /**
     * The whole reason the bard replaced the inn: a sink has to drain a hoard in a sane number of
     * purchases. The inn's 600-gold flat rate would have taken ~27 buys to spend 16,000 gold.
     */
    @Test
    fun escalationDrainsAHoardQuickly() {
        var spent = 0
        var buys = 0
        while (spent < 16_000) {
            spent += AppViewModel.bardCostFor(buys)
            buys++
        }
        assertTrue("16k gold should drain in a handful of buys, took $buys", buys <= 5)
    }

    // ---- The gambling table ----

    /**
     * Expected value per gold staked must be NEGATIVE or the table prints money. Computed exactly
     * over the 20 faces rather than sampled, so this can't pass by luck.
     */
    @Test
    fun diceTableHasNegativeExpectedValue() {
        val ev = (1..20).sumOf { roll ->
            when {
                roll == 20 -> AppViewModel.DICE_JACKPOT_MULT
                roll >= 11 -> AppViewModel.DICE_WIN_MULT
                else -> -1.0
            }
        } / 20.0
        assertTrue("EV per gold staked must be negative, was $ev", ev < 0.0)
        assertEquals(-0.08, ev, 1e-9)
    }

    /** Daily exposure has to stay bounded — the table must not be able to become the evening. */
    @Test
    fun dailyExposureIsCapped() {
        val maxTurnover = AppViewModel.DICE_MAX_STAKE * AppViewModel.DICE_BETS_PER_DAY
        assertEquals(5_000, maxTurnover)
        assertTrue(AppViewModel.DICE_BETS_PER_DAY <= 10)
    }

    // ---- Momentum ----

    /** Today is never charged as idle: you still have the whole day to act. */
    @Test
    fun momentumDoesNotDecayForTodayOrYesterday() {
        assertEquals(10, RewardEngine.effectiveMomentum(10, 100L, 100L))
        assertEquals(10, RewardEngine.effectiveMomentum(10, 100L, 101L))
    }

    @Test
    fun momentumDecaysTwoPerWholeIdleDay() {
        assertEquals(8, RewardEngine.effectiveMomentum(10, 100L, 102L))   // 1 idle day
        assertEquals(6, RewardEngine.effectiveMomentum(10, 100L, 103L))   // 2 idle days
        assertEquals(0, RewardEngine.effectiveMomentum(10, 100L, 110L))   // floors, never negative
    }

    @Test
    fun momentumBonusEqualsItsPoints() {
        // The displayed percentage is taken from the points precisely because deriving it from the
        // float multiplier truncated 15 into "+14%".
        assertEquals(1.15f, RewardEngine.momentumMultiplier(15), 1e-6f)
        assertEquals(1.0f, RewardEngine.momentumMultiplier(0), 1e-6f)
        assertEquals(1.15f, RewardEngine.momentumMultiplier(99), 1e-6f)  // clamped at the cap
    }
}
