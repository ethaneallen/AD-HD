package com.addhd.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addhd.app.R

// "Parchment Scroll" — a D&D tavern palette. Aged sepia parchment, weathered leather cards,
// VIBRANT TEAL as the primary accent (glows on brown, readable on cream), gold reserved for
// celebratory moments (crits, board-clears, streak fire), ink-black text.
private val Parchment = Color(0xFFEAD9B0)      // aged sepia page

/**
 * Shared corner-radius tokens. Every card/button/input/chip should pull its rounding from here
 * so the whole app stays on one consistent shape scale instead of ad-hoc per-screen values.
 */
object Radii {
    val card = 16.dp
    val button = 16.dp
    val chip = 12.dp
}

/**
 * Named brand colors that live OUTSIDE the M3 ColorScheme — danger/Sanctuary callouts that must
 * stay a fixed dark-crimson + cream pairing in both light and dark themes (colorScheme.error
 * flips to a light salmon in dark mode, which would break the contrast). WCAG-AA (~7:1).
 */
object AppColors {
    /** Deep tavern crimson for danger / "Seek Refuge in the Sanctuary" callout buttons. */
    val SanctuaryCrimson = Color(0xFF8E2B22)
    /** Warm cream/parchment label — high-contrast text on [SanctuaryCrimson]. */
    val SanctuaryCream = Color(0xFFFFF8E7)
}

/**
 * Gold used as TEXT (not as a fill).
 *
 * `colorScheme.secondary` is tuned to be a container — a deep gold carrying dark [onSecondary]
 * text, as on the Home gold badge. Reused as a text colour it failed badly: 2.41:1 on a leather
 * card (chapter/room progress) and 2.33:1 on the parchment background (the Tavern Games scores),
 * both under the 4.5:1 minimum.
 *
 * One value can't fix both, because in the light theme the two backdrops sit on opposite sides of
 * the scale — a gold light enough to read on dark leather disappears on pale parchment. Hence two
 * tokens, each theme-aware. In the dark theme both backdrops are dark, so both resolve to the
 * same bright gold.
 */
/**
 * A slightly RAISED version of the page background — pale parchment in light, warm dark-leather in
 * dark — for inert chips and tiles that should read as lifted off the page while still carrying
 * dark-on-light (light theme) text.
 *
 * This exists because that role was previously taken by `colorScheme.surfaceVariant`, whose M3
 * contract partner is `onSurfaceVariant`. This theme needs onSurfaceVariant to be a muted CREAM
 * (it labels the leather cards, ~60 call sites), so the declared pair measured 1.33:1 — invisible.
 * Giving the raised-surface role its own token lets the Material slots stay internally consistent.
 */
object RaisedSurface {
    val color: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4A2F18) else ParchmentLit

    /** Text/icon colour that belongs on [color]. */
    val onColor: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground
}

object GoldText {
    /** Gold label on a leather card (`surface`). Light: 5.10:1 · Dark: 6.87:1. */
    val onCard: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFD9AF4A) else Color(0xFFE7CE92)

    /** Gold label directly on the parchment `background`. Light: 5.53:1 · Dark: 8.28:1. */
    val onBackdrop: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFD9AF4A) else Color(0xFF6B4E0E)
}
private val ParchmentLit = Color(0xFFF2E6C8)   // lighter parchment for raised surfaces
private val Leather = Color(0xFF6E4A2A)         // weathered leather brown (cards)
private val LeatherDark = Color(0xFF4A2F18)     // deep leather / tooled edge
private val Teal = Color(0xFF00BFA5)            // vibrant teal — primary accent everywhere
private val TealDeep = Color(0xFF00695C)        // darker teal for containers/on-light text
private val Crimson = Color(0xFF8E2B22)         // reserved for genuine errors only
private val Gold = Color(0xFFB8860B)            // deep gold highlight (celebration accents)
private val Ink = Color(0xFF2A1E12)             // ink-black-brown text

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06251F),          // near-black teal — reads on bright teal fills
    primaryContainer = Color(0xFFA7E8DC),   // pale teal container (stat chips, hero card)
    onPrimaryContainer = TealDeep,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFE7CE92),
    onSecondaryContainer = Color(0xFF4A360A),
    tertiary = Leather,
    background = Parchment,
    onBackground = Ink,
    surface = Leather,
    onSurface = Color(0xFFF5E6CC),          // light cream — primary text on leather cards
    // surfaceVariant is now a LEATHER sibling of `surface`, which makes it a genuine partner for
    // the cream onSurfaceVariant below (5.8:1). It previously held pale parchment, so the pair M3
    // guarantees — surfaceVariant + onSurfaceVariant — measured 1.33:1 and any stock component
    // pulling both slots would have rendered near-invisible. The pale "raised background" role it
    // used to serve moved to [RaisedSurface], which is what those few call sites actually wanted.
    surfaceVariant = Color(0xFF5E3F24),
    onSurfaceVariant = Color(0xFFDCC7A0),   // muted cream — subtitles/placeholders on leather
    error = Crimson,
    outline = Color(0xFF9C7A4A),            // mid tan — borders readable on leather AND parchment
    // Dialogs, menus, sheets pull their container from the surfaceContainer* family. M3's
    // light defaults are a pale neutral (gray/lavender) — invisible under our cream text.
    // Pin the whole family to leather browns so every AlertDialog reads with onSurface cream.
    surfaceContainerLowest = LeatherDark,
    surfaceContainerLow = Color(0xFF5E3F24),
    surfaceContainer = Leather,
    surfaceContainerHigh = Color(0xFF7A5230),
    surfaceContainerHighest = Color(0xFF875C36),
    inverseSurface = LeatherDark,
    inverseOnSurface = Color(0xFFF5E6CC),
)

// Dark theme = candle-lit tavern at night: darker parchment, deeper leather.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF26D9BE),            // bright teal, glows on the night-tavern browns
    onPrimary = Color(0xFF00332B),
    primaryContainer = TealDeep,
    onPrimaryContainer = Color(0xFFB6F0E5),
    secondary = Color(0xFFD9AF4A),
    onSecondary = Color(0xFF3A2A06),
    tertiary = Color(0xFFC9A278),
    background = Color(0xFF241A10),
    onBackground = Color(0xFFEAD9B0),
    surface = Color(0xFF3A2717),
    onSurface = Color(0xFFEAD9B0),
    surfaceVariant = Color(0xFF4A2F18),
    onSurfaceVariant = Color(0xFFD8C7A0),
    error = Color(0xFFE49189),
    outline = Color(0xFF8A6A44),
    // Keep the dialog/menu container family in the dark-tavern browns (defaults are already
    // dark here, but pin them so dialogs match surface exactly and text stays cream).
    surfaceContainerLowest = Color(0xFF1E140B),
    surfaceContainerLow = Color(0xFF2E2013),
    surfaceContainer = Color(0xFF3A2717),
    surfaceContainerHigh = Color(0xFF463019),
    surfaceContainerHighest = Color(0xFF523822),
    inverseSurface = Color(0xFFEAD9B0),
    inverseOnSurface = Color(0xFF2A1E12),
)

// Bundled OFL fonts (res/font). No network/Play-Services dependency — works offline.
private val Cinzel = FontFamily(Font(R.font.cinzel_bold, FontWeight.Bold))
private val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

private val AppType = Typography(
    // Cinzel (Roman-inscription serif) for big, sparse headers — instant tavern gravitas.
    headlineLarge = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = 0.5.sp),
    // Titles: Atkinson bold — legible at a glance, weight contrast carries the hierarchy.
    titleLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.25.sp),
    titleMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    // Body/labels: Atkinson with roomier line-height so ADHD eyes don't skip lines mid-scan.
    bodyLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
)

@Composable
fun AddhdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppType,
        content = content,
    )
}
