package com.addhd.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Accessibility helpers for the app's heavy use of emoji as iconography.
 *
 * Emoji in a `Text` are, to TalkBack, just characters — so it reads their Unicode names aloud. A
 * decorative "⚔️" beside the words "Current Quest" is announced as "crossed swords Current Quest",
 * and a lone emoji inside an [androidx.compose.material3.IconButton] gives a button with no name
 * at all. With emoji standing in for icons across most of this app, that's the difference between
 * a usable screen reader experience and an unusable one.
 *
 * Two intents, deliberately separate so the choice has to be made explicitly:
 *  - [decorative] — the emoji repeats something already written next to it. Hide it outright.
 *  - [describedAs] — the emoji IS the information (or labels a control). Give it real words.
 */

/**
 * Remove this element from the accessibility tree. For emoji that are pure ornament next to a
 * visible text label, where announcing them adds noise and no meaning.
 */
fun Modifier.decorative(): Modifier = this.clearAndSetSemantics { }

/** Replace this element's announcement with [label]. For emoji that carry meaning on their own. */
fun Modifier.describedAs(label: String): Modifier =
    this.semantics { contentDescription = label }
