package com.addhd.app.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import com.addhd.app.util.Haptics
import com.addhd.app.util.Sfx
import com.addhd.app.util.Sound
import com.addhd.app.vm.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.sqrt

/** The three tactile mini-games hosted on the Tavern Games screen. */
private enum class FidgetTab(val label: String) {
    DICE("🎲 Dice"),
    LOCKPICK("🗝️ Lockpick"),
    POPPER("🔮 Runes"),
}

// Boss-fight HP pools. Small so a fight is a few dramatic rolls, not a grind.
private const val BOSS_HP = 3
private const val HERO_HP = 3

/**
 * Draws a stylized icosahedron (d20): a pointy-top hexagon outline with internal facet lines
 * that read as a real tabletop die. The center facet is the brightest (the "up" face holding
 * the number); surrounding facets are shaded darker at varying levels to fake 3D lighting, and
 * every edge is stroked in gold. [base] is the die's body color; [edge] the facet lines.
 */
private fun DrawScope.drawD20(base: Color, edge: Color, litFacet: Int = -1) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = size.minDimension / 2f

    // Outer hexagon (pointy top): 6 corners at -90°, -30°, 30°, 90°, 150°, 210°.
    val hex = (0 until 6).map { i ->
        val a = Math.toRadians((60.0 * i) - 90.0)
        Offset(cx + r * Math.cos(a).toFloat(), cy + r * Math.sin(a).toFloat())
    }
    // Inner triangle corners — the central "top face" of the die. Points up.
    val innerR = r * 0.5f
    val top = Offset(cx, cy - innerR)
    val botL = Offset(cx - innerR * 0.87f, cy + innerR * 0.5f)
    val botR = Offset(cx + innerR * 0.87f, cy + innerR * 0.5f)

    fun tri(a: Offset, b: Offset, c: Offset, fill: Color) {
        val p = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }
        drawPath(p, fill)
        // Round joins/caps so facet corners stay flush instead of shooting miter spikes past the
        // silhouette (the default StrokeJoin.Miter extends sharp acute corners well past the vertex).
        drawPath(p, edge, style = Stroke(width = r * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // Shade levels relative to the base body colour.
    fun shade(f: Float) = Color(
        (base.red * f).coerceIn(0f, 1f),
        (base.green * f).coerceIn(0f, 1f),
        (base.blue * f).coerceIn(0f, 1f),
        1f,
    )

    // Outer ring facets: each hex edge + the nearest inner-triangle vertex, alternating shades
    // so the die looks angular and lit from the upper-left.
    val ringShades = floatArrayOf(1.15f, 0.7f, 0.85f, 0.6f, 1.0f, 0.75f)
    val innerPts = listOf(top, botR, botR, botL, botL, top)
    for (i in 0 until 6) {
        // [litFacet] brightens one ring facet so successive poses look lit from different angles,
        // selling a real tumble rather than a flat spin. -1 = the neutral "settled" look.
        val boost = if (i == litFacet) 1.6f else 1f
        tri(hex[i], hex[(i + 1) % 6], innerPts[i], shade(ringShades[i] * boost))
    }
    // The bright central face holding the number.
    tri(top, botR, botL, shade(if (litFacet == -1) 1.25f else 1.05f))

    // Crisp gold outline around the whole die.
    val outline = Path().apply {
        moveTo(hex[0].x, hex[0].y)
        for (i in 1 until 6) lineTo(hex[i].x, hex[i].y)
        close()
    }
    drawPath(outline, edge, style = Stroke(width = r * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Fidget dice tray. Tap the die (or shake the phone) to roll a d20. Haptics fire as it
 * tumbles and lands. Rolling is free/unlimited; the VM decides any capped crit XP and emits
 * the result on [rolls], which we surface as a splash. Screen never awards XP itself.
 *
 * Boss Fight Mode: when [bossName] is non-null the tray becomes a combat encounter. The header
 * shows the boss + its [bossAc]; a roll >= AC fells the boss and invokes [onBossDefeated] (which
 * clears the gate and advances the campaign in the VM). Misses prompt a retry.
 */
@Composable
fun FidgetScreen(
    rolls: SharedFlow<AppViewModel.FidgetRoll>,
    onRoll: () -> Unit,
    rewards: SharedFlow<AppViewModel.FidgetReward>,
    onAwardGameXp: () -> Unit,
    bossName: String? = null,
    bossAc: Int = 10,
    onBossDefeated: () -> Unit = {},
    attackMod: Int = 0,
    attackStatLabel: String = "STR",
    onHeroDowned: () -> Unit = {},
) {
    val context = LocalContext.current
    val cream = Color(0xFFF5E6CC)
    val bossMode = bossName != null

    // Sub-game tab selection (only shown outside boss mode). Boss fights force the dice.
    var tab by remember { mutableStateOf(FidgetTab.DICE) }
    if (!bossMode) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tavern Games", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FidgetTab.entries.forEach { t ->
                    val selected = tab == t
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else com.addhd.app.ui.theme.RaisedSurface.color)
                            .clickable { tab = t }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            when (tab) {
                FidgetTab.DICE -> DiceGame(rolls, onRoll, cream)
                FidgetTab.LOCKPICK -> LockpickGame(rewards, onAwardGameXp)
                FidgetTab.POPPER -> RunePopperGame(rewards, onAwardGameXp)
            }
        }
        return
    }
    // ----- Boss fight mode: a full turn-based combat loop (self-contained) -----
    BossFightScreen(
        rolls = rolls,
        onRoll = onRoll,
        bossName = bossName!!,
        bossAc = bossAc,
        attackMod = attackMod,
        attackStatLabel = attackStatLabel,
        cream = cream,
        onBossDefeated = onBossDefeated,
        onHeroDowned = onHeroDowned,
    )
}


/** Outcome of resolving one boss-fight attack roll. */
private enum class FightPhase { FIGHTING, WON, LOST }

/**
 * Turn-based boss combat. Boss has [BOSS_HP] HP, hero has [HERO_HP] hearts. Each roll adds the
 * class [attackMod] to the d20: total >= [bossAc] deals 1 to the boss; a miss deals 1 to the
 * hero. First to 0 ends it — boss down = chapter cleared ([onBossDefeated]); hero down = retreat
 * ([onHeroDowned], which re-locks the gate so a real task must recharge the fight).
 */
@Composable
private fun BossFightScreen(
    rolls: SharedFlow<AppViewModel.FidgetRoll>,
    onRoll: () -> Unit,
    bossName: String,
    bossAc: Int,
    attackMod: Int,
    attackStatLabel: String,
    cream: Color,
    onBossDefeated: () -> Unit,
    onHeroDowned: () -> Unit,
) {
    val context = LocalContext.current
    val onBg = MaterialTheme.colorScheme.onBackground

    var bossHp by remember { mutableIntStateOf(BOSS_HP) }
    var heroHp by remember { mutableIntStateOf(HERO_HP) }
    var phase by remember { mutableStateOf(FightPhase.FIGHTING) }
    var shownValue by remember { mutableIntStateOf(20) }
    var rolling by remember { mutableStateOf(false) }
    // Last resolved attack, for the math line ("Rolled 8 + 3 STR = 11 — HIT!").
    var lastLine by remember { mutableStateOf<String?>(null) }
    var lastHit by remember { mutableStateOf<Boolean?>(null) }

    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val rotZ = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun triggerRoll() {
        if (rolling || phase != FightPhase.FIGHTING) return
        rolling = true
        Haptics.diceRoll(context)
        Sound.play(context, Sfx.SFX_DICE_ROLL)
        scope.launch {
            val dur = 1000
            val spins = listOf(rotX, rotY, rotZ)
            spins.forEach { it.snapTo(it.value % 360f) }
            val targets = listOf(
                rotX.value + (720..1440).random() * listOf(1, -1).random(),
                rotY.value + (720..1440).random() * listOf(1, -1).random(),
                rotZ.value + (360..720).random() * listOf(1, -1).random(),
            )
            listOf(
                launch { rotX.animateTo(targets[0].toFloat(), tween(dur, easing = LinearEasing)) },
                launch { rotY.animateTo(targets[1].toFloat(), tween(dur, easing = LinearEasing)) },
                launch { rotZ.animateTo(targets[2].toFloat(), tween(dur, easing = LinearEasing)) },
            ).forEach { it.join() }
            listOf(
                launch { rotX.animateTo(0f, tween(200)) },
                launch { rotY.animateTo(0f, tween(200)) },
                launch { rotZ.animateTo(0f, tween(200)) },
            ).forEach { it.join() }
        }
        onRoll()
    }

    // Resolve each roll: apply the modifier, deal damage, and check for a win/loss.
    LaunchedEffect(Unit) {
        rolls.collect { roll ->
            val steps = 10
            repeat(steps) { i -> shownValue = (1..20).random(); delay(40L + i * 12L) }
            val total = roll.value + attackMod
            shownValue = roll.value
            rolling = false
            val hit = total >= bossAc
            lastHit = hit
            val modStr = if (attackMod > 0) " + $attackMod $attackStatLabel = $total" else ""
            if (hit) {
                lastLine = "Rolled ${roll.value}$modStr — HIT!"
                bossHp = (bossHp - 1).coerceAtLeast(0)
                Haptics.diceCrit(context); Sound.play(context, Sfx.SFX_DICE_CRIT)
                if (bossHp == 0) {
                    phase = FightPhase.WON
                    Haptics.diceCrit(context); Sound.play(context, Sfx.SFX_BOSS_DEFEAT)
                    onBossDefeated()
                }
            } else {
                lastLine = "Rolled ${roll.value}$modStr — MISS! The $bossName strikes back."
                heroHp = (heroHp - 1).coerceAtLeast(0)
                Haptics.tick(context)
                if (heroHp == 0) {
                    phase = FightPhase.LOST
                    Haptics.finishTriple(context)
                    onHeroDowned()
                }
            }
        }
    }

    val currentTrigger by rememberUpdatedState(::triggerRoll)
    ShakeDetector(enabled = !rolling && phase == FightPhase.FIGHTING) { currentTrigger() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("⚔️ Boss Fight", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            bossName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        // HP pools: hearts (teal) vs boss skulls (gold), each dimming as HP drops.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HpRow(label = "You", full = HERO_HP, current = heroHp, fullIcon = "❤️", emptyIcon = "🖤")
            HpRow(label = "Boss", full = BOSS_HP, current = bossHp, fullIcon = "💀", emptyIcon = "☠️", alignEnd = true)
        }
        Text(
            "AC $bossAc  ·  d20 + $attackMod $attackStatLabel to hit",
            style = MaterialTheme.typography.bodyMedium, color = onBg, textAlign = TextAlign.Center,
        )

        // Dice tray.
        val dieBody = MaterialTheme.colorScheme.primary
        val dieEdge = MaterialTheme.colorScheme.secondary
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = phase == FightPhase.FIGHTING) { triggerRoll() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier.fillMaxWidth(0.62f).aspectRatio(1f).graphicsLayer {
                    rotationX = rotX.value; rotationY = rotY.value; rotationZ = rotZ.value
                    cameraDistance = 14f * density
                },
            ) { drawD20(base = dieBody, edge = dieEdge) }
            if (!rolling) {
                // Matches the practice-die pip: dark glyph + cream halo for legibility on any facet.
                Text(
                    "$shownValue",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF3A2A16),
                        shadow = Shadow(color = cream, offset = Offset(0f, 0f), blurRadius = 14f),
                    ),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        // Status / result line.
        when (phase) {
            FightPhase.WON -> {
                Text("⚔️ BOSS DEFEATED!", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = com.addhd.app.ui.theme.GoldText.onBackdrop,
                    textAlign = TextAlign.Center)
                Text("The chapter is cleared. Onward!", style = MaterialTheme.typography.bodyMedium,
                    color = onBg, textAlign = TextAlign.Center)
            }
            FightPhase.LOST -> {
                Text("💀 DEFEAT", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center)
                Text(
                    "You retreat, wounded — but your XP and quests are safe. Complete one real-world task to recharge your hearts, then face the boss again.",
                    style = MaterialTheme.typography.bodyMedium, color = onBg, textAlign = TextAlign.Center,
                )
            }
            FightPhase.FIGHTING -> {
                Text(
                    lastLine ?: "Tap the die to attack!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (lastHit == true) FontWeight.Bold else FontWeight.SemiBold,
                    color = when (lastHit) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> onBg
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A labelled HP pool rendered as a row of full/empty icons. */
@Composable
private fun HpRow(
    label: String,
    full: Int,
    current: Int,
    fullIcon: String,
    emptyIcon: String,
    alignEnd: Boolean = false,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
        Row {
            repeat(full) { i ->
                Text(
                    if (i < current) fullIcon else emptyIcon,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

/**
 * One discrete isometric orientation of the d20. [spin] rotates the silhouette in-plane (true edge
 * rotation), [tiltX]/[tiltY] foreshorten a single axis to fake the die leaning toward/away from the
 * viewer (a solid tilt, NOT a flat skew), and [litFacet] picks which facet catches the light so the
 * shading changes per pose. Stepping through [TUMBLE] in sequence reads as a real die tumbling.
 */
private data class DicePose(val spin: Float, val tiltX: Float, val tiltY: Float, val litFacet: Int) {
    companion object {
        // Index 0 is the flat, number-up settle template (no tilt, neutral lighting).
        val TUMBLE = listOf(
            DicePose(0f,    1.00f, 1.00f, -1),  // 0: FLAT — settled face-up
            DicePose(-22f,  1.00f, 0.72f, 0),   // top-left tilt (leaning back)
            DicePose(24f,   0.74f, 1.00f, 2),   // right edge toward viewer
            DicePose(70f,   1.00f, 0.66f, 3),   // edge-on, tipped forward
            DicePose(-64f,  0.68f, 1.00f, 4),   // left edge, tipped
            DicePose(128f,  1.00f, 0.80f, 5),   // top-right tilt
            DicePose(-110f, 0.80f, 1.00f, 1),   // edge-down, spun
        )
        const val FLAT_IDX = 0
    }
}

/**
 * Game 1 (free-play): the d20 dice tray with a multi-axis physical tumble, tap + shake to roll,
 * and the shared daily-capped crit XP. Self-contained so it can live under a tab.
 */
@Composable
private fun DiceGame(
    rolls: SharedFlow<AppViewModel.FidgetRoll>,
    onRoll: () -> Unit,
    cream: Color,
) {
    val context = LocalContext.current
    var shownValue by remember { mutableIntStateOf(20) }
    var rolling by remember { mutableStateOf(false) }
    var lastRoll by remember { mutableStateOf<AppViewModel.FidgetRoll?>(null) }

    // --- Option A: discrete isometric pose frames. No rotationX/Y skew (that just squashes a flat
    // sprite). Each pose is a distinct hand-authored 3D angle: a silhouette spin (rotZ), a
    // foreshortening squash on ONE axis (tiltX/tiltY < 1), and a lit facet so the light rakes a
    // different face each frame. Stepping through them fast reads as a real solid tumbling. ---
    val poses = remember { DicePose.TUMBLE }
    var poseIdx by remember { mutableIntStateOf(DicePose.FLAT_IDX) }
    val pose = poses[poseIdx]

    val transY = remember { Animatable(0f) }      // px; die lifts during tumble, slams to 0 on land
    val landScale = remember { Animatable(1f) }   // squash-and-pop on impact
    val burst = remember { Animatable(0f) }        // 0..1 Nat-20 gold burst progress
    var shakePx by remember { mutableIntStateOf(0) } // container camera-shake offset on a crit
    var settled by remember { mutableStateOf(true) } // true = show the flat, number-up template

    fun triggerRoll() {
        if (rolling) return
        rolling = true
        Haptics.diceRoll(context)
        Sound.play(context, Sfx.SFX_DICE_ROLL)
        onRoll()
    }

    LaunchedEffect(Unit) {
        rolls.collect { roll ->
            // --- Phase 1: tumble ~700ms. Step through the isometric pose frames, accelerating then
            // slowing, lifting the die as it "bounces up". Each frame shows a new face + new angle. ---
            settled = false
            launch { transY.animateTo(-64f, tween(300, easing = FastOutSlowInEasing)) }
            val frames = 16
            for (f in 0 until frames) {
                // Never repeat the flat settle pose mid-tumble — always an angled frame.
                poseIdx = (1 until poses.size).random()
                shownValue = (1..20).random()
                // Ease-out cadence: frames start fast (~38ms) and stretch toward the end (~90ms).
                delay(38L + (52L * f / frames))
            }

            // --- Phase 2: slam down onto the perfectly flat, number-up template with a heavy,
            // low-damping bounce. This is the single "settled" 3D template, number centered. ---
            poseIdx = DicePose.FLAT_IDX
            shownValue = roll.value
            settled = true
            lastRoll = roll
            rolling = false
            if (roll.crit) { Haptics.diceCrit(context); Sound.play(context, Sfx.SFX_DICE_CRIT) }
            else Haptics.diceLand(context)
            landScale.snapTo(if (roll.crit) 1.4f else 1.22f)
            listOf(
                launch { transY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)) },
                launch { landScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) },
            ).forEach { it.join() }

            // --- Phase 3: Nat-20 celebration — a hard 3px container jitter + a gold burst overlay. ---
            if (roll.value == 20) {
                launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                    burst.snapTo(0f)
                }
                repeat(8) { i ->
                    shakePx = if (i % 2 == 0) 3 else -3
                    delay(45L)
                }
                shakePx = 0
            }
        }
    }

    val currentTrigger by rememberUpdatedState(::triggerRoll)
    ShakeDetector(enabled = !rolling) { currentTrigger() }

    val onBg = MaterialTheme.colorScheme.onBackground
    Text(
        "Roll the d20 to test your fate or pass the hours between adventures. Tap the die or shake the device.",
        style = MaterialTheme.typography.bodyMedium, color = onBg, textAlign = TextAlign.Center,
    )
    val dieBody = MaterialTheme.colorScheme.primary
    val dieEdge = MaterialTheme.colorScheme.secondary
    // Card glows gold on a landed crit, easing back to the plain surface otherwise.
    val isCrit = lastRoll?.crit == true && !rolling
    val trayBg by animateColorAsState(
        if (isCrit) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        tween(320), label = "diceTrayBg",
    )
    // Lift factor 0..1 (die at rest .. peak of the tumble) — drives the drop-shadow size/softness.
    val lift = (-transY.value / 64f).coerceIn(0f, 1f)
    val shadowColor = MaterialTheme.colorScheme.scrim
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            // Nat-20 camera shake: hard ±3px jitter on the whole tray.
            .offset { androidx.compose.ui.unit.IntOffset(shakePx, -shakePx) }
            .clip(RoundedCornerShape(24.dp))
            .background(trayBg).clickable { triggerRoll() },
        contentAlignment = Alignment.Center,
    ) {
        // Ground shadow: a soft ellipse pinned to the felt. Grows + fades as the die lifts, snaps
        // tight + dark when it slams down — the depth cue a flat sprite can't fake on its own.
        Canvas(Modifier.fillMaxWidth(0.62f).aspectRatio(1f)) {
            val cx = size.width / 2f
            val baseR = size.minDimension * 0.34f
            val r = baseR * (1f - 0.28f * lift)
            drawOval(
                color = shadowColor.copy(alpha = 0.38f * (1f - 0.55f * lift)),
                topLeft = Offset(cx - r, size.height * 0.86f - r * 0.32f),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 0.64f),
            )
        }
        Canvas(
            Modifier.fillMaxWidth(0.62f).aspectRatio(1f).graphicsLayer {
                // Silhouette spin only (real edge rotation, not a skew). Foreshorten ONE axis per
                // pose to fake the tilt toward/away from the viewer, and lift with translationY.
                rotationZ = pose.spin
                scaleX = landScale.value * pose.tiltX
                scaleY = landScale.value * pose.tiltY
                translationY = transY.value
            },
        ) { drawD20(base = dieBody, edge = dieEdge, litFacet = pose.litFacet) }
        // Number rides the die only when it's the flat, settled template; hidden mid-tumble so we
        // don't imply a readable face on an angled pose.
        if (settled && !rolling) {
            // High-contrast pip: a bold dark-brown numeral with a bright cream halo. The die's
            // center facet varies in brightness (litFacet shading), so a single flat color washes
            // out on the bright teal — dark glyph + light glow stays legible on any facet.
            Text(
                "$shownValue",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF3A2A16),
                    shadow = Shadow(color = cream, offset = Offset(0f, 0f), blurRadius = 14f),
                ),
                modifier = Modifier.graphicsLayer { scaleX = landScale.value; scaleY = landScale.value },
            )
        }
        // Gold particle burst radiating from the die on a Nat 20.
        if (burst.value > 0f) {
            Canvas(Modifier.fillMaxWidth(0.62f).aspectRatio(1f)) {
                val p = burst.value
                val c = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension * 0.62f
                val gold = Color(0xFFFFD54A)
                repeat(12) { i ->
                    val ang = Math.toRadians((30.0 * i)).toFloat()
                    val d = maxR * p
                    val pos = Offset(c.x + d * cos(ang), c.y + d * sin(ang))
                    drawCircle(gold.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                        radius = size.minDimension * 0.04f * (1f - p * 0.5f), center = pos)
                }
            }
        }
    }
    val roll = lastRoll
    when {
        rolling -> Text("Rolling…", style = MaterialTheme.typography.titleMedium, color = onBg)
        roll == null -> Text("Tap to roll", style = MaterialTheme.typography.titleMedium, color = onBg)
        roll.xpAwarded > 0 -> Text("⚔️ CRITICAL ${roll.value}!  +${roll.xpAwarded} XP",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = com.addhd.app.ui.theme.GoldText.onBackdrop)
        roll.crit -> Text("⚔️ Critical ${roll.value}! (daily XP maxed)",
            style = MaterialTheme.typography.titleMedium, color = onBg)
        else -> Text("Rolled ${roll.value}", style = MaterialTheme.typography.titleMedium, color = onBg)
    }
    if (roll != null && !rolling) {
        Text(
            if (roll.critXpLeftToday > 0)
                "${roll.critXpLeftToday} bonus-XP crit${if (roll.critXpLeftToday == 1) "" else "s"} left today"
            else "Bonus XP resets tomorrow — keep rolling for fun!",
            style = MaterialTheme.typography.bodyMedium, color = onBg,
        )
    }
}

/**
 * Game 2: The Rogue's Lockpick. Drag around the metal dial to rotate it; each degree of travel
 * fires a haptic tick whose strength rises as you near a hidden sweet-spot angle. Hold within a
 * few degrees of the sweet spot for ~1s and the lock pops (heavy double buzz + shared XP), then
 * a fresh sweet spot is randomised.
 */
@Composable
private fun LockpickGame(
    rewards: SharedFlow<AppViewModel.FidgetReward>,
    onAwardGameXp: () -> Unit,
) {
    val context = LocalContext.current
    val onBg = MaterialTheme.colorScheme.onBackground
    // Sweet spot in degrees [0,360). Random per attempt; varies by recomposition seed via a counter.
    var attempt by remember { mutableIntStateOf(0) }
    val sweetSpot = remember(attempt) { (0..359).random().toFloat() }
    var angle by remember { mutableStateOf(0f) }       // current dial angle
    var lastTickDeg by remember { mutableIntStateOf(0) }
    var popped by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<AppViewModel.FidgetReward?>(null) }
    val holdProgress = remember { Animatable(0f) }   // 0..1 fill while parked in the sweet spot
    val scope = rememberCoroutineScope()

    // Angular distance to the sweet spot, 0..180.
    fun distTo(a: Float): Float {
        val d = abs(((a - sweetSpot) % 360f + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }
    val dist = distTo(angle)
    val closeness = (1f - (dist / 180f)).coerceIn(0f, 1f)   // 0 far .. 1 exact
    val inSweet = dist <= 6f

    // Hold-to-open: while parked in the sweet spot, drive a visible 1s fill ring; on full, pop.
    // Leaving the sweet spot rewinds the ring so the player sees their progress bleed away.
    LaunchedEffect(inSweet, popped, attempt) {
        if (inSweet && !popped) {
            holdProgress.animateTo(1f, tween(1000, easing = LinearEasing))
            if (!popped) {
                popped = true
                Haptics.lockPop(context)
                Sound.play(context, Sfx.SFX_LOCK_POP)
                onAwardGameXp()
            }
        } else if (!popped) {
            holdProgress.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) { rewards.collect { reward = it } }

    Text(
        "Feel for the tumbler. Drag the dial — the clicks grow heavier as you near the sweet spot. Hold it there to pick the lock.",
        style = MaterialTheme.typography.bodyMedium, color = onBg, textAlign = TextAlign.Center,
    )
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        val dialColor = MaterialTheme.colorScheme.secondary
        val markColor = if (popped) MaterialTheme.colorScheme.secondary
            else if (inSweet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        Canvas(
            Modifier.fillMaxWidth(0.7f).aspectRatio(1f).pointerInput(attempt) {
                detectDragGestures { change, _ ->
                    val c = size.width / 2f
                    val a = Math.toDegrees(
                        atan2((change.position.y - c).toDouble(), (change.position.x - c).toDouble())
                    ).toFloat()
                    val norm = (a + 360f) % 360f
                    if (!popped) {
                        angle = norm
                        // Fire a tick for each whole degree crossed, strength scaled by closeness.
                        val deg = norm.toInt()
                        if (deg != lastTickDeg) {
                            lastTickDeg = deg
                            Haptics.lockTick(context, closeness)
                        }
                    }
                }
            },
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            // Dial body — warms from dim toward the primary hue as you close on the sweet spot.
            drawCircle(dialColor.copy(alpha = 0.12f + 0.22f * closeness), radius = r, center = c)
            drawCircle(dialColor, radius = r, center = c, style = Stroke(width = r * 0.08f))
            // Hold-progress ring: a bright arc sweeping clockwise from the top as the 1s hold fills.
            if (holdProgress.value > 0f) {
                val inset = r * 0.06f
                drawArc(
                    color = markColor,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress.value,
                    useCenter = false,
                    topLeft = Offset(c.x - r + inset, c.y - r + inset),
                    size = androidx.compose.ui.geometry.Size((r - inset) * 2f, (r - inset) * 2f),
                    style = Stroke(width = r * 0.12f),
                )
            }
            // Notch marker showing the current dial angle.
            val rad = Math.toRadians(angle.toDouble())
            val tip = Offset(c.x + r * 0.82f * Math.cos(rad).toFloat(), c.y + r * 0.82f * Math.sin(rad).toFloat())
            drawLine(markColor, start = c, end = tip, strokeWidth = r * 0.09f)
            drawCircle(markColor, radius = r * 0.12f, center = c)
        }
    }
    when {
        popped -> Text("🗝️ CLICK! The lock springs open!",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = com.addhd.app.ui.theme.GoldText.onBackdrop, textAlign = TextAlign.Center)
        inSweet -> Text("Hold it right there…", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        closeness > 0.7f -> Text("So close — the tumbler's almost set.",
            style = MaterialTheme.typography.titleMedium, color = onBg)
        else -> Text("Searching for the sweet spot…", style = MaterialTheme.typography.titleMedium, color = onBg)
    }
    if (popped) {
        val rw = reward
        Text(
            if (rw != null && rw.xpAwarded > 0) "+${rw.xpAwarded} XP  ·  ${rw.critXpLeftToday} bonus left today"
            else "Nice pick! (daily bonus XP maxed)",
            style = MaterialTheme.typography.bodyMedium, color = onBg,
        )
        Text(
            "Pick another lock",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            // Bright teal on the pale raised surface measured 1.88:1 — effectively invisible.
            // onPrimaryContainer is the deep teal, 5.33:1 on the same backdrop.
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .background(com.addhd.app.ui.theme.RaisedSurface.color)
                .clickable { popped = false; angle = 0f; lastTickDeg = 0; scope.launch { holdProgress.snapTo(0f) }; attempt++ }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/**
 * Game 3: Rune Popper — a 4x4 pop-it board of glowing runes. Tapping a rune pops it (glow off,
 * snappy click + short audio pop). Clearing all 16 flashes the screen, resets the board, and
 * grants the shared daily-capped XP.
 */
@Composable
private fun RunePopperGame(
    rewards: SharedFlow<AppViewModel.FidgetReward>,
    onAwardGameXp: () -> Unit,
) {
    val context = LocalContext.current
    val onBg = MaterialTheme.colorScheme.onBackground
    val runes = remember { listOf("ᚠ", "ᚢ", "ᚦ", "ᚨ", "ᚱ", "ᚲ", "ᚷ", "ᚹ") }
    // popped[i] = true once tapped. mutableStateList so taps recompose the grid.
    val popped = remember { mutableStateListOf(*Array(16) { false }) }
    var boardSeed by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<AppViewModel.FidgetReward?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { rewards.collect { reward = it } }

    // Rune glyph + colour per cell, stable within a board.
    val cellRune = remember(boardSeed) { List(16) { runes[(it + boardSeed) % runes.size] } }

    fun onCleared() {
        Haptics.boardClear(context)
        Sound.play(context, Sfx.SFX_BOSS_DEFEAT)
        onAwardGameXp()
        scope.launch {
            flash = true
            delay(320)
            flash = false
            for (i in 0 until 16) popped[i] = false
            boardSeed++
        }
    }

    Text(
        "Dispel the glowing sigils. Tap to snap them down — purge the board to claim a fragment of experience.",
        style = MaterialTheme.typography.bodyMedium, color = onBg, textAlign = TextAlign.Center,
    )
    val remaining = popped.count { !it }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
            .background(if (flash) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 4) {
                        val i = row * 4 + col
                        val isPopped = popped[i]
                        // Active runes glow teal with a lighter halo ring; popped runes sink to a
                        // dark, semi-transparent inset so they clearly read as "depressed".
                        val glow = MaterialTheme.colorScheme.primary
                        val depressed = Color(0xFF3A2717).copy(alpha = 0.55f) // darker than the card
                        // Snap-shrink as the rune sinks; bouncy grow-in when the board resets.
                        val cellScale by animateFloatAsState(
                            targetValue = if (isPopped) 0.82f else 1f,
                            animationSpec = if (isPopped) tween(110, easing = FastOutSlowInEasing)
                            else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "runeScale",
                        )
                        Box(
                            Modifier
                                .size(58.dp)
                                .graphicsLayer { scaleX = cellScale; scaleY = cellScale }
                                .clip(RoundedCornerShape(50))
                                .background(if (isPopped) depressed else glow)
                                .then(
                                    if (isPopped) Modifier
                                    else Modifier.border(
                                        width = 2.dp,
                                        color = glow.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(50),
                                    )
                                )
                                .clickable(enabled = !isPopped) {
                                    if (!popped[i]) {
                                        popped[i] = true
                                        Haptics.runePop(context)
                                        playPop(context)
                                        if (popped.all { it }) onCleared()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!isPopped) {
                                Text(cellRune[i], style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
    if (remaining == 0) {
        val rw = reward
        Text("✨ Board cleared!", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = com.addhd.app.ui.theme.GoldText.onBackdrop)
        Text(
            if (rw != null && rw.xpAwarded > 0) "+${rw.xpAwarded} XP  ·  ${rw.critXpLeftToday} bonus left today"
            else "Satisfying. (daily bonus XP maxed)",
            style = MaterialTheme.typography.bodyMedium, color = onBg,
        )
    } else {
        Text("$remaining rune${if (remaining == 1) "" else "s"} left",
            style = MaterialTheme.typography.titleMedium, color = onBg)
    }
}

/**
 * Registers an accelerometer listener while [enabled] and invokes [onShake] when a sharp
 * movement crosses the threshold (debounced). Cleaned up automatically when the screen leaves
 * composition or when disabled.
 */
@Composable
private fun ShakeDetector(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val shake by rememberUpdatedState(onShake)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShake = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                // ~2.5g threshold; SensorManager.GRAVITY_EARTH is ~9.81.
                if (g > SensorManager.GRAVITY_EARTH * 2.5f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShake > 900L) { // debounce so one shake = one roll
                        lastShake = now
                        shake()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
}

/** A short, snappy "pop" tone for the Rune Popper — synthesized, no audio asset needed. */
private fun playPop(context: Context) {
    try {
        // Fire-and-forget: a brief blip. Released after it plays so we don't leak the generator.
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
        android.os.Handler(context.mainLooper).postDelayed({ tg.release() }, 200)
    } catch (_: Exception) { /* audio not available — haptics still fire */ }
}
