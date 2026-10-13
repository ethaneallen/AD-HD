package com.addhd.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.ui.theme.Radii
import com.addhd.app.vm.AppViewModel
import kotlinx.coroutines.delay

/** Diameter of the countdown dial. */
private val DIAL_SIZE = 220.dp

/**
 * The Focus (Pomodoro) screen — a pure VIEW over [AppViewModel.FocusTimer].
 *
 * The countdown itself deliberately lives in the ViewModel, not here. As screen-local `remember`
 * state it was destroyed the moment you left the Focus tab (or Android reclaimed the process), so
 * a block run the way this screen tells you to run it — phone face-down, app backgrounded — was
 * never written to focus_sessions and never showed up under Home → "focus min" or Your Patterns.
 * Only the ambient companion and the retreat animation are screen-local now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    timer: AppViewModel.FocusTimer,
    onPickMinutes: (Int) -> Unit,
    onStart: (minutes: Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onDismissFinish: () -> Unit,
    onTacticalRetreat: () -> Unit,
    onStartHype: () -> Unit,
    // Post-block attribution: offered only AFTER a block finishes, and entirely optional.
    attributionCandidates: List<com.addhd.app.data.TaskEntity> = emptyList(),
    canAttribute: Boolean = false,
    attributedTo: String? = null,
    onAttribute: (com.addhd.app.data.TaskEntity) -> Unit = {},
    // Mid-block rescue: split a quest that turned out to be too big, without killing the timer.
    onAddStep: (parentId: Long, title: String) -> Unit = { _, _ -> },
    /** The quest explicitly committed to, if any — this block's minutes go to it automatically. */
    focusTarget: com.addhd.app.data.TaskEntity? = null,
) {
    var breakdownFor by remember { mutableStateOf<com.addhd.app.data.TaskEntity?>(null) }
    var hypeEnabled by remember { mutableStateOf(true) }
    var retreated by remember { mutableStateOf(false) }
    // Ambient Focus Companion — null = silent. Drives the soundscape + animated familiar.
    var companion by remember { mutableStateOf<FocusCompanion?>(null) }
    val ambienceContext = LocalContext.current

    val minutesChoice = timer.minutes
    val remaining = timer.remainingSec
    val running = timer.running
    val finished = timer.finished

    // Start/switch/stop the soundscape as the selection changes, and always silence it when the
    // Focus screen leaves composition so no loop leaks into the rest of the app.
    LaunchedEffect(companion) {
        companion?.let { com.addhd.app.util.Ambience.play(ambienceContext, it.key) } ?: com.addhd.app.util.Ambience.stop()
    }
    DisposableEffect(Unit) {
        onDispose { com.addhd.app.util.Ambience.stop() }
    }

    // Tactical Retreat: stop the timer, show the shield beat, then pivot to a
    // 2-minute physical reset. No lost XP, no lost streak — a clean exit, not a failure.
    LaunchedEffect(retreated) {
        if (retreated) {
            onReset()
            delay(1300)
            onTacticalRetreat()
            retreated = false
        }
    }

    if (retreated) {
        TacticalRetreatOverlay()
        return
    }

    val progress = 1f - remaining.toFloat() / (minutesChoice * 60).coerceAtLeast(1)

    // Scrollable, because this screen does NOT fit on a tall-but-short-viewport phone (e.g. a
    // Pixel 10). Without a scroll the Column handed the timer Box a max-height smaller than its
    // requested 240.dp, so `Modifier.size(240)` got squeezed to 240 x xxx and the progress ring
    // rendered as a flattened ELLIPSE with the bottom of the dial — and the retreat hatch below
    // it — clipped off entirely. Scrolling gives the content unbounded height, so every child
    // keeps its true size. The bottom padding keeps the transport row clear of the global FAB.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Focus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Eyes on the path, hero. The Tavern awaits your return.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            "Lay your scrying glass face-down and let no distraction reach you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )

        // Ambient companion: optional low-fi soundscape + a subtle animated familiar while it plays.
        AmbientCompanionBar(selected = companion, onSelect = { companion = it })
        if (companion != null) {
            AnimatedCompanion(emoji = companion!!.emoji)
        }

        // There is deliberately ONE primary action on this screen. A separate "⚡ Just Start (5 min)"
        // button used to sit here, and with the 5-chip selected it made the exact same call as
        // Start below it — two full-width accent buttons doing one job. (It originally labelled
        // itself with your easiest quest, but a focus session has no task reference, so the link it
        // implied never existed.) Its one good idea — offering another round while the momentum is
        // there — now applies to every block, see the finish prompt below.
        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 15, 25, 45).forEach { m ->
                    val sel = minutesChoice == m
                    FilterChip(
                        selected = sel,
                        onClick = { onPickMinutes(m) },
                        label = { Text("$m") },
                        colors = com.addhd.app.ui.components.craftChipColors(onLightBackground = true),
                        border = com.addhd.app.ui.components.craftChipBorder(sel, onLightBackground = true),
                    )
                }
            }
            FilterChip(
                selected = hypeEnabled,
                onClick = { hypeEnabled = !hypeEnabled },
                label = { Text(if (hypeEnabled) "🎵 Summon Bard's song: on" else "🎵 Summon Bard's song: off") },
                colors = com.addhd.app.ui.components.craftChipColors(onLightBackground = true),
                border = com.addhd.app.ui.components.craftChipBorder(hypeEnabled, onLightBackground = true),
            )
        }

        // Named target, shown only when the hero chose it themselves (via Home's "Pick it back up"
        // or a previous block's attribution). This is NOT the old auto-guessed label that used to
        // sit on the Just Start button: that one named a quest it had no ability to link, whereas
        // this block's minutes really are credited to the quest shown.
        if (focusTarget != null && !finished) {
            Text(
                "Working on: ${focusTarget.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(DIAL_SIZE)) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(DIAL_SIZE),
                strokeWidth = 12.dp,
            )
            val mm = remaining / 60
            val ss = remaining % 60
            Text("%02d:%02d".format(mm, ss), style = MaterialTheme.typography.headlineLarge)
        }

        if (finished) {
            // Post-block fork, now offered after EVERY block rather than only after a 5-minute
            // sprint: ride the momentum into another round, or stop here. The minutes are ALREADY
            // banked at this point, so this choice only decides what happens next — never whether
            // the work counted.
            Text("🔥 Watch complete! The hearth roars.", style = MaterialTheme.typography.titleLarge)
            Text(
                "$minutesChoice focus minutes banked.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
            // Optional: tie the minutes to a quest so its estimate can be checked against reality.
            // Asked here rather than before the block on purpose — nothing may stand between the
            // hero and the Start button. Skipping costs nothing; the minutes are banked either way.
            if (attributedTo != null) {
                Text(
                    "Logged against: $attributedTo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            } else if (canAttribute && attributionCandidates.isNotEmpty()) {
                Text(
                    "What did you work on? (optional)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attributionCandidates.forEach { t ->
                        FilterChip(
                            selected = false,
                            onClick = { onAttribute(t) },
                            label = { Text(t.title, maxLines = 1, fontSize = 12.sp) },
                            colors = com.addhd.app.ui.components.craftChipColors(onLightBackground = true),
                            border = com.addhd.app.ui.components.craftChipBorder(false, onLightBackground = true),
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        // Order matters — see the note on the Start button below.
                        onStart(minutesChoice)
                        if (hypeEnabled) onStartHype()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(Radii.button),
                ) { Text("Another $minutesChoice") }
                OutlinedButton(
                    onClick = onDismissFinish,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(Radii.button),
                ) { Text("Done for now") }
            }
        }

        // The finish prompt owns the screen while it's up — Start/Reset would just duplicate it.
        if (!finished) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!running) {
                Button(
                    onClick = {
                        // Fire the Hype Hook only on a fresh start — drown out the
                        // transition-to-work friction instantly.
                        if (timer.isFresh) {
                            // ARM THE BLOCK FIRST, then hand the screen to the music app.
                            // "Summon Bard's Song" deliberately foregrounds YouTube Music, which
                            // means this very tap pushes us into the background. Starting the
                            // countdown's foreground service after that point is a background FGS
                            // start, which Android 12+ refuses — the block would still run and
                            // still be credited, but the live countdown would silently never
                            // appear. Arming while we're definitively foreground avoids the race.
                            onStart(minutesChoice)
                            if (hypeEnabled) onStartHype()
                        } else {
                            onResume()
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(Radii.button),
                ) { Text(if (timer.isFresh) "Start" else "Resume") }
            } else {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(Radii.button),
                ) { Text("Pause") }
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(Radii.button),
            ) { Text("Reset") }
        }

        // Stalling mid-block used to have exactly one exit: quit the timer. But "I'm stuck" and
        // "this task is too big" are different problems, and the second one has a real fix the app
        // already supports — micro-steps — which simply couldn't be reached from here. Offered
        // while a block is live so the clock keeps running while you carve the task up.
        if (running && (focusTarget != null || attributionCandidates.isNotEmpty())) {
            TextButton(
                // Default to the quest actually being worked on, not just the easiest open one.
                onClick = { breakdownFor = focusTarget ?: attributionCandidates.first() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("🪓  Too big? Break it into steps") }
        }

        // Available once a block is underway — a shame-free exit hatch.
        if (running || !timer.isFresh) {
            TextButton(onClick = { retreated = true }, modifier = Modifier.fillMaxWidth()) {
                Text("🛡️  Tactical Retreat — regroup, no penalty")
            }
        }
        }
    }

    BreakdownDialog(
        target = breakdownFor,
        // The committed quest leads the list, and is included even when the mana filter would
        // otherwise have left it out — you must always be able to break up what you're on.
        candidates = (
            listOfNotNull(focusTarget) +
                attributionCandidates.filter { it.id != focusTarget?.id }
            ).take(4),
        onPickTarget = { breakdownFor = it },
        onAddStep = onAddStep,
        onDismiss = { breakdownFor = null },
    )
}

/**
 * Carve a too-big quest into micro-steps mid-block. Deliberately stays open after each step is
 * added — the point is to get two or three out while they're in your head — and the focus timer is
 * untouched throughout, so using this never costs you the block you're in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownDialog(
    target: com.addhd.app.data.TaskEntity?,
    candidates: List<com.addhd.app.data.TaskEntity>,
    onPickTarget: (com.addhd.app.data.TaskEntity) -> Unit,
    onAddStep: (parentId: Long, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (target == null) return
    var step by remember(target.id) { mutableStateOf("") }
    var added by remember(target.id) { mutableIntStateOf(0) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🪓", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.decorative()) },
        title = { Text("Break it down", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Smallest next action — one physical thing you could do in five minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidates.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Which quest?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        candidates.forEach { c ->
                            FilterChip(
                                selected = c.id == target.id,
                                onClick = { onPickTarget(c) },
                                label = { Text(c.title, maxLines = 1, fontSize = 12.sp) },
                                colors = com.addhd.app.ui.components.craftChipColors(onLightBackground = false),
                                border = com.addhd.app.ui.components.craftChipBorder(c.id == target.id, onLightBackground = false),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    label = { Text("e.g. open the file and read one page") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.button),
                )
                if (added > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$added step${if (added == 1) "" else "s"} added to \"${target.title}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step.isNotBlank()) {
                        onAddStep(target.id, step.trim())
                        added++
                        step = ""        // stay open: capture the next one while it's in mind
                    }
                },
                enabled = step.isNotBlank(),
                shape = RoundedCornerShape(Radii.button),
            ) { Text("Add step") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (added > 0) "Back to work" else "Cancel") }
        },
    )
}

@Composable
private fun TacticalRetreatOverlay() {
    val scale = com.addhd.app.ui.theme.pulse(from = 0.9f, to = 1.15f, durationMs = 700, label = "shield")
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "🛡️",
            fontSize = MaterialTheme.typography.headlineLarge.fontSize,
            modifier = Modifier.scale(scale * 2f).decorative(),
        )
        Spacer(Modifier.height(28.dp))
        Text("Tactical Retreat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "The party holds the line. No streak lost, no XP lost. Regroup with a 2-minute reset…",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The three ambient companions offered on the Focus screen (label, familiar emoji, [Ambience] key). */
private enum class FocusCompanion(val label: String, val emoji: String, val key: String) {
    HEARTH("Hearth", "🔥", "hearth"),
    WILDWOOD("Wildwood", "🌲", "nature"),
    RAINFALL("Rain", "🌧️", "rain"),
}

/**
 * Ambient-companion selector: an "Off" chip plus one per [FocusCompanion]. Tapping the active
 * companion again toggles it back off, so silence is always one tap away. A [FlowRow] lets the
 * chips wrap onto a second line rather than squeezing the last one into a two-line label.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmbientCompanionBar(selected: FocusCompanion?, onSelect: (FocusCompanion?) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Ambient companion",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Off", maxLines = 1, fontSize = 12.sp) },
                colors = com.addhd.app.ui.components.craftChipColors(onLightBackground = true),
                border = com.addhd.app.ui.components.craftChipBorder(selected == null, onLightBackground = true),
            )
            FocusCompanion.entries.forEach { c ->
                FilterChip(
                    selected = selected == c,
                    onClick = { onSelect(if (selected == c) null else c) },
                    label = { Text("${c.emoji} ${c.label}", maxLines = 1, fontSize = 12.sp) },
                    colors = com.addhd.app.ui.components.craftChipColors(onLightBackground = true),
                    border = com.addhd.app.ui.components.craftChipBorder(selected == c, onLightBackground = true),
                )
            }
        }
    }
}

/** A softly breathing familiar shown while an ambience plays — gentle scale + glow pulse. */
@Composable
private fun AnimatedCompanion(emoji: String) {
    val scale = com.addhd.app.ui.theme.pulse(from = 0.92f, to = 1.10f, durationMs = 1600, label = "companionScale")
    val glow = com.addhd.app.ui.theme.pulse(from = 0.6f, to = 1f, durationMs = 1600, label = "companionGlow")
    Text(
        emoji,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.scale(scale).alpha(glow),
    )
}
