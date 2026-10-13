package com.addhd.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.theme.Radii

/**
 * Colors for a FILLED button sitting inside a leather card: solid gold container with
 * ink-dark text — high contrast, never crimson-on-brown.
 */
@Composable
fun cardButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.secondary,
    contentColor = MaterialTheme.colorScheme.onSecondary,
)

/**
 * Colors for an OUTLINED button inside a leather card: cream text so the label pops.
 * Pair with [cardOutlinedBorder] for a matching light border.
 */
@Composable
fun cardOutlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
fun cardOutlinedBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

/**
 * High-contrast FilterChip colors. Selected -> vibrant teal fill with dark ink text. Unselected
 * -> transparent with a high-contrast label; pass [onLightBackground] = true for chips on the
 * cream/parchment background (deep-brown text) or false for chips inside a leather card (cream
 * text). Pair with [craftChipBorder] for the matching unselected outline.
 */
@Composable
fun craftChipColors(onLightBackground: Boolean): SelectableChipColors {
    val unselectedText =
        if (onLightBackground) MaterialTheme.colorScheme.onBackground   // deep brown on cream
        else MaterialTheme.colorScheme.onSurface                        // cream on leather
    return FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,     // vibrant teal fill
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,       // dark ink on teal
        labelColor = unselectedText,
    )
}

@Composable
fun craftChipBorder(selected: Boolean, onLightBackground: Boolean): BorderStroke {
    val c =
        if (onLightBackground) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.onSurface
    // Selected chips are filled teal (no visible border needed); unselected get a solid,
    // high-contrast outline instead of the faint default tan.
    return BorderStroke(1.5.dp, if (selected) Color.Transparent else c)
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column {
        Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun screenPadding() = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radii.chip),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** Numeric stat chip whose value COUNTS UP to the target instead of snapping — a little juice. */
@Composable
fun StatChip(label: String, value: Int, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(targetValue = value, animationSpec = tween(650), label = "statCount")
    StatChip(label, "$animated", modifier)
}
