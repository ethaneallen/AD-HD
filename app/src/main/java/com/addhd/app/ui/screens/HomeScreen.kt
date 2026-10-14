package com.addhd.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.describedAs
import com.addhd.app.ui.components.spoken
import com.addhd.app.ui.components.decorative
import com.addhd.app.campaign.BossType
import com.addhd.app.campaign.HeroClass
import com.addhd.app.coach.Coach
import com.addhd.app.coach.CoachResult
import com.addhd.app.coach.SuggestedAction
import com.addhd.app.data.PetEntity
import com.addhd.app.data.TaskEntity
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.ui.components.StatChip
import com.addhd.app.ui.theme.AppColors
import com.addhd.app.ui.theme.Radii
import com.addhd.app.vm.AppViewModel

@Composable
fun HomeScreen(
    pet: PetEntity?,
    coach: CoachResult,
    focusMinutesToday: Int,
    tasksDoneToday: Int,
    cleanStreakDays: Int,
    heroClass: HeroClass?,
    questName: String,
    questBoss: String,
    roomInQuest: Int,
    /** Room-10 boss fight. 0 max HP hides the fight UI (pre-v22 state / loading). */
    bossMaxHp: Int = 0,
    bossHpRemaining: Int = 0,
    heroBossDamage: Int = 0,
    totalRooms: Int,
    flavorHeadline: String,
    flavorLine: String,
    activeQuest: AppViewModel.ActiveQuest?,
    campaigns: List<com.addhd.app.data.CampaignEntity>,
    onSelectCampaign: (String) -> Unit,
    onAction: (SuggestedAction) -> Unit,
    onOpenSos: () -> Unit,
    onQuickWin: (String) -> Unit,
    onOpenStreak: () -> Unit,
    onDefeatBoss: () -> Unit,
    quickStrikeTasks: List<TaskEntity> = emptyList(),   // up to 3 lowest-effort open quests for Quick Strike
    onCompleteTask: (TaskEntity) -> Unit = {},
    // "What was I doing?" — the quest from the most recent attributed focus block, if still open.
    resumeTask: TaskEntity? = null,
    /** True once this quest has been explicitly committed to, which changes the card's tense. */
    resumeIsActive: Boolean = false,
    onStillOnIt: (TaskEntity) -> Unit = {},
    /** The earned-and-losable XP multiplier. Null hides the card. */
    momentum: AppViewModel.Momentum? = null,
) {
    val scroll = rememberScrollState()
    val heroEmoji = heroClass?.emoji ?: "🧙"
    // Quick Strike: the coach's "Strike a Minor Blow" opens this in-place instead of navigating away.
    var showQuickStrike by remember { mutableStateOf(false) }

    val bob = com.addhd.app.ui.theme.pulse(from = 0.96f, to = 1.06f, durationMs = 1800, label = "petbob")

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Compact horizontal Hero Header: circular avatar + name/level + tight XP bar. Replaces
        // the old full-height vertical block so the campaign + boss cards sit above the fold.
        val p = pet ?: PetEntity()
        val need = p.level * 100
        val targetProgress = (p.xp.toFloat() / need).coerceIn(0f, 1f)
        val animProgress by animateFloatAsState(
            targetValue = targetProgress, animationSpec = tween(600), label = "xpFill",
        )
        var pump by remember { mutableStateOf(false) }
        val pumpScale by animateFloatAsState(
            targetValue = if (pump) 1.35f else 1f,
            animationSpec = tween(if (pump) 120 else 380),
            label = "xpPump",
            finishedListener = { if (pump) pump = false },
        )
        LaunchedEffect(p.xp) { pump = true }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.card),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Circular avatar frame with the scaled-down, gently-bobbing weapon icon.
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(heroEmoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.scale(bob))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            pet?.name ?: "The Hero",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "${heroClass?.display ?: "Adventurer"} · Lv. ${p.level}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    // Gold balance badge — lets you watch the shop currency grow during play.
                    Card(
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                    ) {
                        Text(
                            "🪙 ${p.gold}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            // Stripping the emoji would leave a bare number; say what it counts.
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .describedAs("${p.gold} gold"),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // Tappable clean-streak badge, moved into the header to save a whole row.
                    Card(
                        onClick = onOpenStreak,
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(containerColor = com.addhd.app.ui.theme.RaisedSurface.color),
                    ) {
                        Text(
                            "🛡️ $cleanStreakDays",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .describedAs("$cleanStreakDays day clean streak"),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Sub-quest flavor line, low-profile.
                Text(
                    flavorLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier.weight(1f).height(8.dp).graphicsLayer { scaleY = pumpScale },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${p.xp}/$need XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // Task creation now lives entirely on the global FAB (Scaffold, AppNav) — the old dashboard
        // "＋ Quick Quest" banner was redundant with it, so it's been removed to cut clutter.

        // Skip DOPAMINE_SOS here — the dedicated crimson "Seek Refuge in the Sanctuary" button below
        // already offers that exact action, so surfacing the coach nudge as a second button would
        // just be a duplicate. Every other nudge still renders.
        coach.suggestedAction?.takeIf { it != SuggestedAction.DOPAMINE_SOS }?.let { action ->
            Button(
                onClick = {
                    // "Strike a Minor Blow" is a micro-action trigger, not navigation: pop the
                    // Quick Strike sheet so a task can be knocked out without leaving Home. Every
                    // other coach nudge still routes to its screen.
                    if (action == SuggestedAction.TINY_TASK) showQuickStrike = true
                    else onAction(action)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Radii.button),
            ) { Text(action.label, style = MaterialTheme.typography.titleMedium) }
        }

        if (showQuickStrike) {
            QuickStrikeDialog(
                tinyTasks = quickStrikeTasks,
                onComplete = onCompleteTask,
                onQuickWin = onQuickWin,
                onDismiss = { showQuickStrike = false },
            )
        }

        // Always-available panic button. Height matches the coach action button above (56.dp)
        // so the stacked primary buttons stay visually consistent whether or not a coach nudge
        // or the empty-state NoBrainer card is present.
        //
        // Contrast: pin BOTH colors explicitly via the shared Sanctuary tokens. Relying on the
        // theme left contentColor at the default onPrimary (near-black teal) over a dark-crimson
        // container — an unreadable dark-on-dark pairing that failed WCAG. The fixed crimson bg +
        // warm-cream label guarantees a high-contrast (~7:1) light-on-dark label in BOTH themes.
        Button(
            onClick = onOpenSos,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(Radii.button),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = AppColors.SanctuaryCrimson,
                contentColor = AppColors.SanctuaryCream,
            ),
        ) {
            Text(
                "🕯️  Seek Refuge in the Sanctuary",
                modifier = Modifier.spoken("Seek Refuge in the Sanctuary"),
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Externalised working memory. Re-entry after a block (or after days away) used to give no
        // clue what you'd been in the middle of, leaving you to reconstruct it — which for an ADHD
        // brain is often enough friction to not bother. The phone remembers instead.
        resumeTask?.let { t ->
            SectionCard {
                // Tense matters: once you've said "still on this", the card is describing the
                // present, and calling it "last time" reads as the app not having registered it.
                Text(
                    if (resumeIsActive) "Still working on" else "Last time you were working on",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Spacer(Modifier.height(10.dp))
                // Both answers are about the QUEST, not about where to go next. An earlier version
                // offered "Open focus" here, which was a category error: being asked what you were
                // working on and answering by navigating to a timer makes no sense, and it left the
                // card permanently unresolved because nothing but finishing the quest dismissed it.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onStillOnIt(t) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.button),
                    ) { Text("Still on it") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onCompleteTask(t) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.button),
                        colors = com.addhd.app.ui.components.cardOutlinedButtonColors(),
                        border = com.addhd.app.ui.components.cardOutlinedBorder(),
                    ) { Text("It's done") }
                }
            }
        }

        Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("quests won", tasksDoneToday, Modifier.weight(1f))
            StatChip("focus min", focusMinutesToday, Modifier.weight(1f))
        }

        // Momentum — the one number here with something at stake. Stated with the consequence
        // attached, because a bonus you can't see yourself losing exerts no pull at all.
        momentum?.let { m ->
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⚡",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(end = 10.dp).decorative(),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Momentum ${m.value}/${m.max}  ·  +${m.bonusPct}% XP",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            when {
                                m.value >= m.max ->
                                    "Maxed. A full idle day costs you ${m.atRisk}."
                                m.value == 0 ->
                                    "Earn anything today to start it climbing."
                                else ->
                                    "+1 for acting today · −${m.atRisk} if the day passes unused."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // One fight holds the floor at a time.
        //
        // The dungeon (rooms + its own boss) and the campaign (chapters + a chapter boss) are
        // separate systems that both narrate in the same voice. Side by side, "Wight blocks the
        // way" and "The boss stands in your way!" read as one battle with a contradictory button.
        // When the campaign chapter is at its gate, the dungeon steps back to a single quiet line:
        // the chapter boss is the rarer, authored moment, so it gets the spotlight.
        val campaignBossHasTheFloor = activeQuest?.bossReady == true && !activeQuest.campaignDone
        if (campaignBossHasTheFloor) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⚔️ $questName",
                        modifier = Modifier.weight(1f).spoken("Dungeon: $questName"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Room $roomInQuest / $totalRooms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.addhd.app.ui.theme.GoldText.onCard,
                    )
                }
                if (roomInQuest >= totalRooms && bossMaxHp > 0) {
                    val hp = if (bossHpRemaining > 0) bossHpRemaining else bossMaxHp
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$questBoss waiting · $hp / $bossMaxHp HP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⚔️ Current Quest", modifier = Modifier.spoken("⚔️ Current Quest"), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Room $roomInQuest / $totalRooms",
                    style = MaterialTheme.typography.titleMedium,
                    color = com.addhd.app.ui.theme.GoldText.onCard,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(questName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (roomInQuest.toFloat() / totalRooms).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = com.addhd.app.ui.theme.RaisedSurface.color,   // light tan
            )
            Spacer(Modifier.height(8.dp))
            val roomsToBoss = (totalRooms - roomInQuest).coerceAtLeast(0)
            if (roomInQuest >= totalRooms && bossMaxHp > 0) {
                // The boss has HP, so show the fight rather than a one-line warning. The hit count
                // is the honest bit: it tells you the climax takes several wins, and that raising
                // STR (medium/big quests) is what shortens it.
                val hp = if (bossHpRemaining > 0) bossHpRemaining else bossMaxHp
                val hitsLeft = if (heroBossDamage > 0) {
                    ((hp + heroBossDamage - 1) / heroBossDamage)
                } else {
                    0
                }
                Text(
                    "⚠️ $questBoss blocks the way",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = com.addhd.app.ui.theme.GoldText.onCard,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (hp.toFloat() / bossMaxHp).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .describedAs("$questBoss at $hp of $bossMaxHp hit points"),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = com.addhd.app.ui.theme.RaisedSurface.color,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    // Quote the floor, not a flat figure: a win also adds 3 per effort point and
                    // doubles on a crit, so "you hit for 11" would understate a 28-damage swing.
                    "$hp / $bossMaxHp HP  ·  you hit for $heroBossDamage+" +
                        if (hitsLeft > 0) {
                            "  ·  up to $hitsLeft more ${if (hitsLeft == 1) "win" else "wins"} to fell it"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "$roomsToBoss ${if (roomsToBoss == 1) "room" else "rooms"} to the warlord: " +
                        "$questBoss. Every task cleared advances you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Campaign picker: shown when no campaign is active yet (fresh start, or the current
        // one was finished). Pick one to pin it as the dashboard quest.
        if (activeQuest == null) {
            val choices = campaigns.filter { !it.completed && !it.active }
            if (choices.isNotEmpty()) {
                SectionCard {
                    Text("⚔️ Choose your campaign", modifier = Modifier.spoken("⚔️ Choose your campaign"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pick a long-term adventure. Completing real tasks advances its chapters; defeat each boss to move on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    choices.forEach { c ->
                        Button(
                            onClick = { onSelectCampaign(c.campaignKey) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(Radii.button),
                        ) {
                            Text(
                                "${c.icon}  ${c.name}" + if (c.level.isNotBlank()) "  (Lv ${c.level})" else "",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }

        // Long-term campaign arc imported from the D&D game engine. Advances as real tasks
        // are completed; the boss gate must be cleared to move to the next chapter.
        activeQuest?.let { q ->
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${q.campaignIcon} ${q.campaignName}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ch. ${(q.milestoneIndex + 1).coerceAtMost(q.milestoneCount)} / ${q.milestoneCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = com.addhd.app.ui.theme.GoldText.onCard,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(q.milestoneTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (q.objective.isNotBlank()) {
                    Text(
                        q.objective,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!q.campaignDone && q.tasksRequired > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (q.tasksDone.toFloat() / q.tasksRequired).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = com.addhd.app.ui.theme.RaisedSurface.color,   // light tan
                    )
                    Spacer(Modifier.height(8.dp))
                    if (q.bossReady) {
                        // Boss gate: chapter charged. Progress is locked here until the boss falls.
                        Text(
                            "⚔️ The boss stands in your way! Chapter progress is locked — extra tasks still earn XP, but you must defeat the boss to advance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        // Only enable the kill when the typed demand is ALSO met (q.bossVulnerable
                        // mirrors defeatBoss()'s guard). A disabled button + the BossDemandPanel
                        // below make the remaining requirement legible instead of a silent no-op.
                        Button(
                            onClick = onDefeatBoss,
                            enabled = q.bossVulnerable,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                if (q.bossVulnerable) "⚔️ Defeat the Boss" else "🔒 Boss is invulnerable",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    } else {
                        // The gate counts dungeon quests now, so say so — "2 more tasks" would be
                        // flatly wrong when each tick costs ten rooms and a boss kill.
                        val left = (q.tasksRequired - q.tasksDone).coerceAtLeast(0)
                        Text(
                            if (left == 1) {
                                "Clear the dungeon quest ahead — its boss falling advances this chapter."
                            } else {
                                "$left more dungeon quests to reach the chapter boss " +
                                    "(ten rooms and a boss apiece)."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Typed boss demand: an extra constraint the hero must satisfy beyond the task
                    // gate. STANDARD adds nothing (classic layout). Others append a tinted panel.
                    BossDemandPanel(q)
                }
            }
        }
    }
}

/**
 * The typed-boss sub-objective panel shown under the task bar. Morphs by [AppViewModel.ActiveQuest.bossType]:
 * STANDARD renders nothing; the others show a tinted panel with an icon, the demand text, and either
 * an x / target counter (Behemoth, Specter) or a human-readable "held since" streak line (Hydra).
 * Progress denominators are PLACEHOLDER constants on [BossType] — the combat layer reuses them.
 */
@Composable
private fun BossDemandPanel(q: AppViewModel.ActiveQuest) {
    when (BossType.fromContent(q.bossType)) {
        BossType.STANDARD -> Unit  // classic layout — nothing appended
        BossType.IRON_BEHEMOTH -> BossDemandCard(
            icon = "🛡️",
            title = "Shielded",
            line = "Absorb deep energy. Complete Focus Sessions to break its armor.",
            progress = q.bossProgress,
            target = BossType.IRON_BEHEMOTH_SESSIONS,
        )
        BossType.SHADOW_SPECTER -> BossDemandCard(
            icon = "👻",
            title = "Ethereal Form",
            line = "Vulnerable only to time-blocked actions.",
            progress = q.bossProgress,
            target = BossType.SHADOW_SPECTER_TIMED,
        )
        BossType.HYDRA -> BossDemandCard(
            icon = "🐍",
            title = "Regenerative Threat",
            line = "Maintain your active streak unbroken since the encounter began.",
            progress = null,
            target = null,
            footnote = hydraAnchorLabel(q.bossAnchorMillis),
        )
    }
}

/** Formats the boss encounter's start anchor as a human-readable line (never raw millis). */
private fun hydraAnchorLabel(anchorMillis: Long): String {
    if (anchorMillis <= 0L) return "Encounter not yet begun."
    val date = java.time.Instant.ofEpochMilli(anchorMillis)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, java.time.LocalDate.now())
    val since = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    return when {
        days <= 0L -> "Streak must hold since today ($since)."
        days == 1L -> "Held 1 day (since $since)."
        else -> "Held $days days (since $since)."
    }
}

/** A tinted sub-objective panel: accent bar + icon + title + demand line, with an optional
 *  x / target counter (with mini progress bar) or a footnote line. */
@Composable
private fun BossDemandCard(
    icon: String,
    title: String,
    line: String,
    progress: Int?,
    target: Int?,
    footnote: String? = null,
) {
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier.width(4.dp).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.tertiary),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$icon  $title",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress != null && target != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (progress.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = com.addhd.app.ui.theme.RaisedSurface.color,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "$progress / $target",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (footnote != null) {
                Spacer(Modifier.height(4.dp))
                Text(footnote, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// Stupidly-simple momentum starters. The bar is intentionally on the floor: the goal is
// to break inertia, not to be productive. A roomy pool keeps the "Draw a different contract"
// cycle (and the Quick Strike empty-state) from repeating too soon.
private val noBrainers = listOf(
    // Hydration
    "Drink a glass of water 💧",
    "Refill your water bottle 🚰",
    // Movement
    "Do 5 jumping jacks 🤸",
    "Stand up and stretch 🙆",
    "Do 10 wall push-ups 💪",
    "Wiggle your toes and roll your shoulders 💆",
    "Roll your neck slowly 🧘",
    // Tidy
    "Put one thing away 🧦",
    "Make your bed 🛏️",
    "Wipe down one surface 🧽",
    "Toss 3 pieces of trash 🗑️",
    "Pick up 5 things off the floor 🧹",
    "Wash a single dish 🍽️",
    // Sensory / reset
    "Take 3 deep breaths 🫁",
    "Open a window 🪟",
    "Step outside for a minute ☀️",
    "Splash cold water on your face 🚿",
    "Unclench your jaw, drop your shoulders 😌",
    // Mental / digital
    "Jot down what's on your mind 📝",
    "Put your phone in another room 📵",
)

/**
 * Quick Strike — a micro-action sheet opened from the coach's "Strike a Minor Blow" nudge. Shows up
 * to three lowest-effort open quests, each with a prominent Complete button, so a task can be knocked
 * out without leaving Home. With no quests logged, it offers a preset 2-minute micro-quest instead.
 */
@Composable
private fun QuickStrikeDialog(
    tinyTasks: List<TaskEntity>,
    onComplete: (TaskEntity) -> Unit,
    onQuickWin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Stable preset for the empty state so it doesn't reshuffle on recomposition.
    val preset = remember { noBrainers.random() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚔️ Quick Strike", modifier = Modifier.spoken("⚔️ Quick Strike")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tinyTasks.isNotEmpty()) {
                    Text(
                        "Land one small blow — right here, no detour.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tinyTasks.forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val dot = when (t.energy) { 1 -> "· tiny"; 3 -> "· big"; else -> "· medium" }
                                Text(
                                    "${t.estimateMin} min $dot",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = { onComplete(t); onDismiss() },
                                shape = RoundedCornerShape(Radii.button),
                            ) { Text("Complete") }
                        }
                    }
                } else {
                    Text(
                        "No quests logged. Land a 2-minute strike instead:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            preset,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { onQuickWin(preset); onDismiss() },
                            shape = RoundedCornerShape(Radii.button),
                        ) { Text("Complete") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
