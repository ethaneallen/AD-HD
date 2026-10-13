package com.addhd.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reduce-motion support.
 *
 * The app leans on perpetual motion for character — a bobbing hero, a breathing focus familiar, a
 * pulsing shield, a slowly rotating sigil in the Sanctuary. That charm is a cost for anyone with
 * vestibular sensitivity or motion-triggered distractibility, which commonly rides along with
 * ADHD: a looping animation is a permanent moving target in the corner of your eye, and it's at
 * its worst on the Sanctuary screen, which exists precisely for moments of overwhelm.
 *
 * Every perpetual animation reads [LocalReduceMotion] through [pulse] and holds still when it's
 * set. One-shot, user-initiated animations (XP bar fill, the combat banner, screen transitions)
 * are deliberately left alone — they communicate that something just happened, they end on their
 * own, and removing them would cost feedback without reducing ambient motion.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * True when the OS itself has animations switched off (Accessibility → Remove animations, or
 * Developer options). Used as the DEFAULT for the in-app toggle, so someone who has already told
 * Android they don't want motion doesn't have to tell this app a second time.
 */
fun systemAnimationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/**
 * A value that oscillates between [from] and [to] forever — or sits frozen at [restAt] when
 * reduce-motion is on. Drop-in replacement for a `rememberInfiniteTransition` + `animateFloat`
 * pair, so a caller can't forget to honour the preference.
 */
@Composable
fun pulse(
    from: Float,
    to: Float,
    durationMs: Int,
    label: String,
    restAt: Float = 1f,
    reverse: Boolean = true,
): Float {
    if (LocalReduceMotion.current) return restAt
    val transition = rememberInfiniteTransition(label = label)
    val value by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs),
            repeatMode = if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = "$label-value",
    )
    return value
}
