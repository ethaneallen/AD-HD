package com.addhd.app.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.ui.theme.Radii
import com.addhd.app.util.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Sanctuary (display name) — the deepest, most-cared-about flow. The code identifier stays
 * [SosScreen] and the "sos" route / DopamineEntity persistence are UNCHANGED on purpose: this is
 * a pure cosmetic rebrand, no schema or enum churn.
 * Three modes: urge-surf breathing timer, "Invoke a Blessing" menu, and a log.
 * Framing throughout: the pull passes; you don't have to answer it with a bad hit.
 */
@Composable
fun SosScreen(
    onLog: (level: Int, trigger: String, action: String, rodeItOut: Boolean) -> Unit,
    wisdomPool: List<String>,
    blessingRewards: kotlinx.coroutines.flow.SharedFlow<com.addhd.app.vm.AppViewModel.BlessingReward>,
) {
    val scroll = rememberScrollState()
    val context = LocalContext.current
    var phase by remember { mutableStateOf("intro") } // intro | surf | boost | oracle | done
    var rodeItOut by remember { mutableStateOf(false) }
    // The reward from the most recent blessing, shown explicitly on the DoneCard. Collected here so
    // it survives the boost->done transition (the flow is replay=1, so a fast tap can't lose it).
    var lastBlessing by remember { mutableStateOf<com.addhd.app.vm.AppViewModel.BlessingReward?>(null) }
    // The transient toast: set the instant a reward lands, auto-cleared after a beat. Separate from
    // lastBlessing (which persists for the DoneCard) so re-entering "done" never re-pops the toast.
    var toast by remember { mutableStateOf<com.addhd.app.vm.AppViewModel.BlessingReward?>(null) }
    LaunchedEffect(Unit) {
        blessingRewards.collect { reward ->
            lastBlessing = reward
            toast = reward
            // Physical reward feedback fired exactly as the toast appears (gear = crit buzz).
            if (reward.kind == com.addhd.app.vm.AppViewModel.BlessingReward.Kind.GEAR) Haptics.diceCrit(context)
            else Haptics.finishTriple(context)
        }
    }
    // Auto-dismiss the toast a few seconds after it's shown.
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2800)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("The Sanctuary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "The hero retreats here when focus fractures. The pull to scroll, snack, or numb out is a wave, not an emergency. Wait it out — or invoke a blessing to steady yourself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            when (phase) {
                "intro" -> IntroButtons(
                    onSurf = { phase = "surf" },
                    onBoost = { phase = "boost" },
                    onOracle = { phase = "oracle" },
                )
                "surf" -> UrgeSurf(onDone = { rode -> if (rode) { rodeItOut = true; onLog(2, "urge", "rode it out", true); phase = "done" } else phase = "boost" })
                "boost" -> BoostMenu(onPicked = { action -> rodeItOut = false; lastBlessing = null; onLog(2, "low dopamine", action, false); phase = "done" })
                "oracle" -> OracleCard(wisdomPool = wisdomPool, onBack = { phase = "intro" })
                // Urge-surf shows the resolve message; a blessing shows its explicit reward readout.
                "done" -> DoneCard(rodeItOut = rodeItOut, reward = if (rodeItOut) null else lastBlessing, onAgain = { phase = "intro" })
            }
        }
        // Reward toast floats above the scrolling content so a drop is never silent.
        com.addhd.app.ui.components.BlessingToast(reward = toast)
    }
}

@Composable
private fun IntroButtons(onSurf: () -> Unit, onBoost: () -> Unit, onOracle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onSurf,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(Radii.button),
        ) { Text("🕯️  Center Your Mind (90s)", style = MaterialTheme.typography.titleMedium) }

        FilledTonalButton(
            onClick = onBoost,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(Radii.button),
        ) { Text("✨  Invoke a Blessing", style = MaterialTheme.typography.titleMedium) }

        FilledTonalButton(
            onClick = onOracle,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(Radii.button),
        ) { Text("🔮  Consult the Oracle", style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * Consult the Oracle — draws a line from [wisdomPool] and shows it in an atmospheric blockquote.
 * Tracks the last-shown index so "Seek further guidance" never repeats the same line back-to-back
 * (picks from the remaining indices). Degrades safely: an empty pool shows a fallback line, and a
 * single-line pool simply re-shows that line (nothing else to pick).
 */
@Composable
private fun OracleCard(wisdomPool: List<String>, onBack: () -> Unit) {
    // Index of the currently-shown line; -1 only in the impossible empty-pool case.
    var index by remember { mutableIntStateOf(if (wisdomPool.isEmpty()) -1 else wisdomPool.indices.random()) }

    fun reroll() {
        if (wisdomPool.size <= 1) return               // nothing to vary
        val next = wisdomPool.indices.filter { it != index }.random()  // never the current line
        index = next
    }

    val line = wisdomPool.getOrNull(index) ?: "The Oracle is silent. Return to the path."

    SectionCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("🔮", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.decorative())
            Spacer(Modifier.height(4.dp))
            Text(
                "The Oracle speaks",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            // Atmospheric blockquote: a tinted panel with a leading accent bar and italic text.
            // height(IntrinsicSize.Min) lets the accent bar stretch to the wrapped text's height.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp),
                ) {
                    Text(
                        "“$line”",
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { reroll() },
                enabled = wisdomPool.size > 1,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("🔮  Seek further guidance") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                border = com.addhd.app.ui.components.cardOutlinedBorder(),
            ) { Text("Return to the Sanctuary") }
        }
    }
}

/**
 * The Arcane Focus — a pulsating geometric breath anchor drawn on Canvas. Concentric accent rings
 * and radiating orbit spokes scale with [pulse] (the 16s box-breath curve) and rotate slowly by
 * [spin]. Deliberately low-contrast: it should guide the eye, not shout. All geometry derives from
 * the canvas size, so it scales to whatever Box it's dropped in.
 */
@Composable
private fun ArcaneFocus(pulse: Float, spin: Float, accent: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val base = size.minDimension / 2f * 0.9f

        // Concentric accent rings — outer faintest, scaled by the breath pulse.
        val rings = listOf(1.0f, 0.72f, 0.44f)
        rings.forEachIndexed { i, f ->
            drawCircle(
                color = accent.copy(alpha = 0.14f + i * 0.05f),
                radius = base * f * pulse,
                center = c,
                style = Stroke(width = 2.5f + i),
            )
        }

        // Orbit spokes — six faint lines radiating from the core, slowly rotating.
        val spokes = 6
        val inner = base * 0.30f * pulse
        val outer = base * 1.0f * pulse
        for (k in 0 until spokes) {
            val a = spin + (2 * PI * k / spokes).toFloat()
            val dx = cos(a); val dy = sin(a)
            drawLine(
                color = accent.copy(alpha = 0.10f),
                start = Offset(c.x + dx * inner, c.y + dy * inner),
                end = Offset(c.x + dx * outer, c.y + dy * outer),
                strokeWidth = 1.5f,
            )
            // Small constellation node at each spoke's end.
            drawCircle(accent.copy(alpha = 0.30f), radius = 3f, center = Offset(c.x + dx * outer, c.y + dy * outer))
        }

        // Central filled focus point — the breath core.
        drawCircle(color = accent.copy(alpha = 0.85f), radius = base * 0.12f * pulse, center = c)
        drawCircle(color = accent.copy(alpha = 0.25f), radius = base * 0.20f * pulse, center = c)
    }
}

@Composable
private fun UrgeSurf(onDone: (rode: Boolean) -> Unit) {
    val total = 90
    var remaining by remember { mutableIntStateOf(total) }
    var running by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0) running = false
    }

    // 4-4-4-4 box breathing: a 16s cycle of four 4s phases. Derived from elapsed seconds so the
    // discrete label + haptics stay locked to the same clock as the countdown (no drift).
    //   0 = inhale · 1 = hold (full) · 2 = exhale · 3 = hold (empty)
    val elapsed = total - remaining
    val phaseIndex = (elapsed % 16) / 4
    val phaseLabel = when (phaseIndex) {
        0 -> "Inhale the ether…"
        1 -> "Hold focus…"
        2 -> "Exhale the noise…"
        else -> "Hold still…"
    }

    // Haptic breathing: ramp up entering inhale, fade out entering exhale, silent on the holds.
    // Eyes-closed friendly. Triple pulse when the whole rite completes.
    LaunchedEffect(phaseIndex, running) {
        if (!running) return@LaunchedEffect
        when (phaseIndex) {
            0 -> Haptics.inhale(context)
            2 -> Haptics.exhale(context)
            else -> {} // holds are silent
        }
    }
    LaunchedEffect(running, remaining) {
        if (!running && remaining == 0) Haptics.finishTriple(context)
    }
    DisposableEffect(Unit) {
        onDispose { Haptics.stop(context) }
    }

    // FRAME-LOCK: the pulse target is a pure function of phaseIndex — the SAME elapsed-derived
    // clock that drives the label and haptics — so a phase flip changes the target at the exact
    // instant the word switches and the buzz fires. The 4s tween just fills the 4s phase between
    // flips at 60fps. Inhale/hold-full sit at 1.0 (full); exhale/hold-empty sit at 0.7 (empty),
    // so the two holds are naturally still — the target doesn't move, so neither does the anchor.
    val pulseTarget = if (phaseIndex == 0 || phaseIndex == 1) 1.0f else 0.7f
    val pulse by animateFloatAsState(
        targetValue = pulseTarget,
        animationSpec = tween(4000),   // exactly one 4s breath phase
        label = "pulse",
    )
    // Rotation is decorative only (not part of breath grounding), so it stays on an independent
    // smooth loop — driving it off the integer `elapsed` would make it step jerkily once per second.
    val spin = com.addhd.app.ui.theme.pulse(
        from = 0f, to = (2 * PI).toFloat(), durationMs = 32000,
        label = "arcane", restAt = 0f, reverse = false,
    )

    SectionCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                phaseLabel,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                ArcaneFocus(pulse = pulse, spin = spin, accent = MaterialTheme.colorScheme.primary)
                Text(
                    "$remaining",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Let the chaos settle. A focused mind strikes with absolute precision. Breathe out the noise.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            if (remaining == 0) {
                Button(onClick = { onDone(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("I held the line 🛡️")
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onDone(true) },
                        modifier = Modifier.weight(1f),
                        colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                        border = com.addhd.app.ui.components.cardOutlinedBorder(),
                    ) {
                        Text("I'm good now")
                    }
                    FilledTonalButton(onClick = { onDone(false) }, modifier = Modifier.weight(1f)) {
                        Text("Still restless")
                    }
                }
            }
        }
    }
}

private data class Boost(val emoji: String, val title: String, val desc: String)

// The Sanctuary offers exactly a d6's worth of blessings — one face per option — so "Roll d6 for
// Sanity" always maps cleanly and the hero never faces choice overload. Keep this at 6.
private const val SANCTUARY_D6 = 6

private val boosts = listOf(
    Boost("🏃", "Move for 5 minutes", "Brisk walk, run, or 20 jumping jacks. Clean dopamine + endorphin hit."),
    Boost("💧", "Cold water", "Souse face with freezing water to reset the nervous system fast."),
    Boost("🧹", "2-minute tidy", "Clear one small surface. Visible progress = a real reward signal."),
    Boost("🎵", "One hype song", "Play a song that moves you. Stand up. Let it end, then decide."),
    Boost("☀️", "Step outside", "Absorb solar radiance for 3 minutes to regulate mood and energy."),
    Boost("🫁", "Box breathing", "In 4, hold 4, out 4, hold 4 — five rounds to lower heart rate."),
)

// Hard cap the visible/roll pool at a d6 (6 faces) no matter how `boosts` grows later.
private val sanctuaryPool: List<Boost> = boosts.take(SANCTUARY_D6)

@Composable
private fun BoostMenu(onPicked: (String) -> Unit) {
    val context = LocalContext.current
    // Roll for Sanity: -1 = not rolled, else index of the chosen boost. `rolling` gates the anim.
    var chosen by remember { mutableIntStateOf(-1) }
    var rolling by remember { mutableStateOf(false) }
    var dieFace by remember { mutableIntStateOf(6) }
    val scope = rememberCoroutineScope()

    fun rollForSanity() {
        if (rolling) return
        rolling = true
        chosen = -1
        Haptics.diceRoll(context)
        com.addhd.app.util.Sound.play(context, com.addhd.app.util.Sfx.SFX_DICE_ROLL)
        scope.launch {
            // Quick d6 flicker (~700ms, decelerating), then land on a result.
            repeat(10) { i ->
                dieFace = (1..6).random()
                delay(45L + i * 15L)
            }
            val result = sanctuaryPool.indices.random()
            dieFace = result + 1
            chosen = result
            rolling = false
            Haptics.diceCrit(context)
            com.addhd.app.util.Sound.play(context, com.addhd.app.util.Sfx.SFX_DICE_CRIT)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Pick one. You only have to do this one thing.",
            style = MaterialTheme.typography.titleMedium,
        )
        // Can't decide? Let the die choose.
        Button(
            onClick = { rollForSanity() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                if (rolling) "🎲  Rolling…  [$dieFace]"
                else if (chosen >= 0) "🎲  Roll again"
                else "🎲  Roll d6 for Sanity — choose for me",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        sanctuaryPool.forEachIndexed { i, b ->
            // Once rolled, spotlight the chosen boost in teal and dim the rest.
            val isChosen = chosen == i
            val dim = if (chosen >= 0 && !isChosen) 0.35f else 1f
            Card(
                onClick = { onPicked(b.title) },
                modifier = Modifier.fillMaxWidth().alpha(dim),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isChosen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            b.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isChosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            b.desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isChosen) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneCard(
    rodeItOut: Boolean,
    reward: com.addhd.app.vm.AppViewModel.BlessingReward?,
    onAgain: () -> Unit,
) {
    // A gear blessing gets its own celebratory header/emoji; everything else keeps the resolve framing.
    val gotGear = reward?.kind == com.addhd.app.vm.AppViewModel.BlessingReward.Kind.GEAR
    SectionCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    rodeItOut -> "🔥"
                    gotGear -> reward?.lootEmoji ?: "🎁"
                    else -> "🛡️"
                },
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    rodeItOut -> "You rode out the whole wave."
                    gotGear -> "The blessing bears a gift."
                    else -> "The blessing steadies you."
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (rodeItOut)
                    "That's the hardest rep there is — surfing the urge instead of acting on it. Your resolve hardened a LOT for that. Every stand makes the next one quieter."
                else
                    "You met a low-dopamine moment on purpose instead of on autopilot. Your resolve hardened. Every rep makes the next urge quieter.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // Reward badge — states EXACTLY what was gained. Urge-surf shows its fixed XP; a blessing
            // shows its actual tiered reward (gear / gold / XP boon), never a silent or generic result.
            val badge: String = when {
                rodeItOut -> "+75 XP  ·  urge ridden 🌊"
                reward == null -> "Blessing invoked ✨"   // reward in flight (async) — brief fallback
                gotGear -> "${reward.lootEmoji} ${reward.lootName}  ·  +${reward.xp} XP"
                reward.kind == com.addhd.app.vm.AppViewModel.BlessingReward.Kind.GOLD ->
                    "+${reward.gold} 🪙 Gold  ·  +${reward.xp} XP"
                else -> "+${reward.xp} XP  ·  a jolt of resolve ✨"   // XP_BOON (baseXp already folded in)
            }
            Card(
                shape = RoundedCornerShape(999.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onAgain,
                colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                border = com.addhd.app.ui.components.cardOutlinedBorder(),
            ) { Text("Done") }
        }
    }
}
