package com.addhd.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the emoji stripping used for screen-reader announcements. Worth a test because the regex
 * has to remove ornament without eating the words — and the failure mode (a mangled or unchanged
 * announcement) is invisible to anyone not actually running TalkBack.
 */
class SemanticsTest {

    @Test
    fun stripsLeadingEmojiAndTidiesSpacing() {
        assertEquals("Current Quest", "⚔️ Current Quest".withoutEmoji())
        assertEquals("ROOM CLEARED", "🏆 ROOM CLEARED".withoutEmoji())
        assertEquals("Seek Refuge in the Sanctuary", "🕯️  Seek Refuge in the Sanctuary".withoutEmoji())
    }

    @Test
    fun stripsEmojiFromAnywhereInTheString() {
        assertEquals("Up in 5 min", "🕰️ Up in 5 min".withoutEmoji())
        assertEquals("Momentum 12/15 · +12% XP", "⚡ Momentum 12/15 · +12% XP".withoutEmoji())
        assertEquals("done and dusted", "done 🎉 and dusted".withoutEmoji())
    }

    @Test
    fun leavesPlainTextUntouched() {
        assertEquals("Still working on", "Still working on".withoutEmoji())
        assertEquals("est 25m · actual 45m", "est 25m · actual 45m".withoutEmoji())
    }

    @Test
    fun handlesEmojiOnlyStrings() {
        assertEquals("", "🔥".withoutEmoji())
        assertEquals("", "⚔️🔥⚔️".withoutEmoji())
    }

    /** Currency and punctuation must survive — they carry meaning in this app's labels. */
    @Test
    fun keepsMeaningfulNonAsciiCharacters() {
        assertEquals("Sold Boots for 5 gold", "🪙 Sold Boots for 5 gold".withoutEmoji())
        assertEquals("1.5x longer than you plan for", "1.5x longer than you plan for".withoutEmoji())
    }
}
