package com.addhd.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addhd.app.ui.components.spoken
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.data.JournalEntity
import com.addhd.app.data.MoodEntity
import com.addhd.app.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// One fixed footprint for EVERY icon on the Status card — condition emoji and spell crystals
// alike — so the two rows share identical geometry and read as a matching set.
private val STATUS_ICON_SIZE = 52.dp

// Status conditions (the old "mood" 1..5 scale), reframed as RPG character states. Index 0 = Lv1.
// Emoji chosen as high-contrast fantasy status symbols (cracked shield … flaming sword).
private val conditionFaces = listOf("🛡️", "🪨", "📜", "✨", "⚔️")
private val conditionNames = listOf("Exhausted", "Encumbered", "Prepared", "Inspired", "Hyped")
// Mana Reserves levels (the old "energy" 1..5 scale). Kept 5-deep for the DB, but the on-screen
// read-out collapses to three clear states via [manaReadout].
private val energyLabels = listOf("Depleted", "Low", "Steady", "Surge", "Overcharged")

// Collapse the 1..5 mana scale to a single clear read-out: Depleted · Steady · Surge.
private fun manaReadout(energy: Int): String = when (energy) {
    in 1..2 -> "Depleted"
    3 -> "Steady"
    else -> "Surge"
}

@Composable
fun StatusScreen(
    moods: List<MoodEntity>,
    onLog: (Int, Int, String) -> Unit,
    // Mana matching: the reading logged here now decides which quests the app offers back. Without
    // these the screen was write-only — a five-point scale consumed as a single bit.
    effortCeiling: Int? = null,
    matchedTasks: List<com.addhd.app.data.TaskEntity> = emptyList(),
    onCompleteTask: (com.addhd.app.data.TaskEntity) -> Unit = {},
    onStartFocus: () -> Unit = {},
    onOpenSanctuary: () -> Unit = {},
) {
    var mood by remember { mutableIntStateOf(3) }
    var energy by remember { mutableIntStateOf(3) }
    var note by remember { mutableStateOf("") }
    // True once the user manually picks an emoji, so energy taps stop overriding their choice.
    var moodManuallySet by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Bottom room so the last row clears the global FAB.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 12.dp, bottom = 88.dp,
        ),
    ) {
        item {
            Text("STATUS & MANA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Assess your active status conditions and magical reserves before taking on today's quests.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            SectionCard {
                // ----- Active Condition (the old mood scale) -----
                Text("Active Condition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    conditionFaces.forEachIndexed { i, face ->
                        val v = i + 1
                        val selected = mood == v
                        // Each condition is a self-contained, tappable status BADGE: a bordered,
                        // rounded card holding a large icon + a permanent label. Selected pops with
                        // a teal fill/border; unselected keeps a light outline so it still reads as
                        // an interactive card on the brown surface.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                    else Modifier
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { mood = v; moodManuallySet = true }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Box(
                                Modifier.size(STATUS_ICON_SIZE).alpha(if (selected) 1f else 0.9f),
                                contentAlignment = Alignment.Center,
                            ) {
                                // The condition name is written directly beneath, so announcing
                                // the glyph as well ("shield, Exhausted") is pure noise.
                                Text(face, fontSize = 32.sp, modifier = Modifier.decorative())
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                conditionNames[i],
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Bold thematic label for the selected condition, e.g. "Inspired (Level 4)".
                Text(
                    "${conditionNames[mood - 1]} (Level $mood)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = com.addhd.app.ui.theme.GoldText.onCard,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))
                // ----- Mana Reserves (the old energy scale) -----
                Text("Mana Reserves", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                // Flask is a PURE visual indicator of the mana level — all interaction lives in the
                // slider beneath it. Kept small (64.dp tall) so it accents rather than dominates.
                ManaFlask(level = energy, modifier = Modifier.fillMaxWidth().height(64.dp))
                Spacer(Modifier.height(4.dp))
                // The tactile control: a real draggable slider with 5 discrete stops (steps=3 -> 4
                // intervals). Drag or tap the thumb; the flask reflects the result. Writes to the
                // SAME `energy` state the old system used, so the DB pipeline is unchanged.
                Slider(
                    value = (energy - 1).toFloat(),
                    onValueChange = { f ->
                        val v = f.toInt().coerceIn(0, 4) + 1
                        if (v != energy) {
                            energy = v
                            if (!moodManuallySet) mood = v
                        }
                    },
                    valueRange = 0f..4f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth(),
                    // Earth-tone theme mapping so the control matches the rest of the card: teal
                    // thumb/active track (same teal as the selected condition text), tan inactive
                    // track (the unselected-asset frame color). Ticks use FULL-opacity contrast
                    // colors — dark ink dots on the teal active track, and onBackground (ink in
                    // light theme, cream in dark) on the pale inactive track — so every stop reads.
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        activeTickColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveTrackColor = com.addhd.app.ui.theme.RaisedSurface.color,
                        inactiveTickColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
                // Scale end-markers, styled to match the "Exhausted / Hyped" condition labels.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Depleted", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Surge", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Mana Reserves: ${manaReadout(energy)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Add campaign notes / journal entries…") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(10.dp))
                // Primary commit action: a filled bright-teal button with bold dark text — the
                // clear, high-visibility way to record the current condition.
                Button(
                    onClick = { onLog(mood, energy, note); note = "" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Lock in Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ----- The app answers back -----
        // Appears once there's a reading for today. Previously logging your status produced no
        // visible response at all, which is the fastest way to teach someone a screen is pointless.
        // A low reading shortens the list instead of just recording the number.
        if (effortCeiling != null && matchedTasks.isNotEmpty()) {
            item {
                SectionCard {
                    Text(
                        com.addhd.app.vm.AppViewModel.manaMatchLabel(effortCeiling),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (effortCeiling <= 1) {
                            "Matched to your reserves — nothing here should cost much."
                        } else {
                            "Matched to your reserves. Tap one to clear it."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    matchedTasks.take(3).forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "○",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onCompleteTask(t) }.padding(end = 10.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    "${t.estimateMin} min",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Depleted gets steered to the Sanctuary; anything else gets offered a block.
                    if (effortCeiling <= 1) {
                        OutlinedButton(
                            onClick = onOpenSanctuary,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Or rest — visit the Sanctuary") }
                    } else {
                        OutlinedButton(
                            onClick = onStartFocus,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Start a focus block") }
                    }
                }
            }
        }

        if (moods.isNotEmpty()) {
            item { Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(moods.take(20), key = { it.id }) { m ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            conditionFaces[m.mood - 1],
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.decorative(),   // the row states the name in words
                        )
                        Spacer(Modifier.height(0.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("${conditionNames[m.mood - 1]} · ${energyLabels[m.energy - 1]} mana", style = MaterialTheme.typography.bodyLarge)
                            if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(fmtDate(m.epochMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Skeuomorphic mana flask: a glass potion (round body tapering to a corked neck) whose teal liquid
 * physically rises/falls with the 1..5 [level]. PURELY a visual indicator — it has no tap handling;
 * the slider beneath it owns all interaction and this just reflects the chosen level.
 *
 * Geometry is normalized to the canvas box so it scales to any size:
 *   - neck/cork occupy the top ~28% of height, body the lower ~72%
 *   - body is a rounded near-circle; liquid is the body path clipped to a bottom-up fill line
 */
@Composable
private fun ManaFlask(level: Int, modifier: Modifier = Modifier) {
    val teal = MaterialTheme.colorScheme.primary
    val glass = MaterialTheme.colorScheme.onSurface       // earth-tone high-contrast border
    val cork = MaterialTheme.colorScheme.secondary
    // 1..5 -> 0.12..1.0 fill fraction; animated so the liquid glides rather than snaps.
    val targetFraction = 0.12f + (level - 1).coerceIn(0, 4) / 4f * 0.88f
    val fillFraction by animateFloatAsState(targetFraction, animationSpec = tween(320), label = "mana")

    Box(modifier, contentAlignment = Alignment.Center) {
        // Pure visual indicator — NO tap handling. The slider below owns all interaction; the
        // flask just reflects [level] as a rising/falling liquid. Height comes from the caller
        // (kept compact) and the geometry below normalizes to whatever box size it's given.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            // Flask body: a rounded bulb centered horizontally, occupying the lower 72% of height.
            val bodyTop = h * 0.28f
            val bodyBottom = h * 0.98f
            val bodyH = bodyBottom - bodyTop
            val bodyW = bodyH * 0.92f                  // slightly narrower than tall
            val bodyLeft = cx - bodyW / 2f
            val bodyRight = cx + bodyW / 2f
            val r = bodyW / 2f                          // corner radius = full round shoulders
            // Neck: a narrow rectangle rising from the body's shoulders to the cork.
            val neckW = bodyW * 0.30f
            val neckTop = h * 0.10f
            val neckLeft = cx - neckW / 2f
            val neckRight = cx + neckW / 2f

            // ---- Glass outline path (neck + round body) ----
            val flask = Path().apply {
                // neck (top) down to shoulders
                moveTo(neckLeft, neckTop)
                lineTo(neckLeft, bodyTop)
                // left shoulder curve into the bulb
                quadraticBezierTo(bodyLeft, bodyTop, bodyLeft, bodyTop + r)
                lineTo(bodyLeft, bodyBottom - r)
                // rounded bottom
                quadraticBezierTo(bodyLeft, bodyBottom, cx, bodyBottom)
                quadraticBezierTo(bodyRight, bodyBottom, bodyRight, bodyBottom - r)
                lineTo(bodyRight, bodyTop + r)
                // right shoulder back up to neck
                quadraticBezierTo(bodyRight, bodyTop, neckRight, bodyTop)
                lineTo(neckRight, neckTop)
                close()
            }

            // ---- Liquid: fill the flask path from the bottom up to fillFraction of the body ----
            val liquidTop = bodyBottom - bodyH * fillFraction
            clipPath(flask) {
                drawRect(
                    color = teal,
                    topLeft = Offset(bodyLeft - r, liquidTop),
                    size = androidx.compose.ui.geometry.Size(bodyW + 2 * r, bodyBottom - liquidTop),
                )
                // a lighter surface line on the liquid for a glassy meniscus
                drawLine(
                    glass.copy(alpha = 0.35f),
                    Offset(bodyLeft, liquidTop), Offset(bodyRight, liquidTop),
                    strokeWidth = h * 0.012f,
                )
            }

            // ---- Glass outline stroke (drawn over the liquid so the rim reads on top) ----
            val strokeW = w * 0.012f
            drawPath(flask, glass.copy(alpha = 0.55f), style = Stroke(width = strokeW))

            // ---- Cork stopper on the neck ----
            val corkH = h * 0.09f
            drawRect(
                color = cork,
                topLeft = Offset(neckLeft - neckW * 0.12f, neckTop - corkH),
                size = androidx.compose.ui.geometry.Size(neckW * 1.24f, corkH),
            )
        }
    }
}

private val journalPrompts = listOf(
    "What curse or mental parasite is draining your focus right now?",
    "Name a small victory or successful skirmish from today's march.",
    "What does a baseline survival victory look like for this leg of the journey?",
    "Which monster are you avoiding, and what is the smallest weapon you can draw against it?",
    "Where did you lose your health points, and where did you find a potion of restoration?",
    "Unrestricted Log — ink the parchment freely.",
)

@Composable
fun JournalScreen(
    entries: List<JournalEntity>,
    onAdd: (String, String) -> Unit,
    onDelete: (JournalEntity) -> Unit,
) {
    var prompt by remember { mutableStateOf(journalPrompts.first()) }
    var body by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Bottom room so the last row clears the global FAB.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 12.dp, bottom = 88.dp,
        ),
    ) {
        item {
            Text("The Hero's Chronicle", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Purge the fog of war from your mind. Inscribe your campaign notes, catalog your burdens, or simply draft a free-form testament.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            SectionCard {
                Text("Prompts", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                journalPrompts.forEach { p ->
                    Text(
                        "• $p",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (prompt == p) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { prompt = p }.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(prompt) },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onAdd(prompt, body); body = "" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                    border = com.addhd.app.ui.components.cardOutlinedBorder(),
                ) { Text("Seal Log") }
            }
        }
        items(entries, key = { it.id }) { e ->
            Card(
                Modifier.fillMaxWidth().clickable { onDelete(e) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(fmtDate(e.epochMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.prompt.isNotBlank()) Text(e.prompt, style = MaterialTheme.typography.labelLarge, color = com.addhd.app.ui.theme.GoldText.onCard)
                    Spacer(Modifier.height(4.dp))
                    Text(e.body, style = MaterialTheme.typography.bodyLarge)
                    Text("Tap to delete", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun fmtDate(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
