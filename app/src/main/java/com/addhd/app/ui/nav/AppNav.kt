package com.addhd.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.addhd.app.campaign.Campaign
import com.addhd.app.campaign.HeroClass
import com.addhd.app.ui.components.CombatOverlay
import com.addhd.app.ui.components.toToast
import com.addhd.app.ui.screens.CharacterScreen
import com.addhd.app.ui.screens.ClassPickerScreen
import com.addhd.app.ui.screens.FidgetScreen
import com.addhd.app.ui.screens.FocusScreen
import com.addhd.app.ui.screens.HabitsScreen
import com.addhd.app.ui.screens.HomeScreen
import com.addhd.app.ui.screens.InsightsScreen
import com.addhd.app.ui.screens.JournalScreen
import com.addhd.app.ui.screens.StatusScreen
import com.addhd.app.ui.screens.SettingsScreen
import com.addhd.app.ui.screens.SosScreen
import com.addhd.app.ui.screens.StreakScreen
import com.addhd.app.ui.screens.TasksScreen
import com.addhd.app.ui.theme.Radii
import com.addhd.app.vm.AppViewModel
import java.time.LocalDate
import java.time.ZoneId

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("tasks", "Tasks", Icons.Default.CheckCircle),
    Tab("focus", "Focus", Icons.Default.Timer),
    Tab("wellbeing", "Status", Icons.Default.Shield),
    Tab("more", "More", Icons.Default.MoreHoriz),
)

@Composable
fun AppNav(openQuickCaptureOnce: androidx.compose.runtime.MutableState<Boolean>? = null) {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current

    // Listen for combat events (task completed -> combat log). Hold the latest for a few
    // seconds so the banner shows regardless of which tab triggered it, then auto-clear.
    var combatEvent by remember { mutableStateOf<AppViewModel.CombatEvent?>(null) }
    LaunchedEffect(Unit) {
        vm.combat.collect { ev ->
            combatEvent = ev
            kotlinx.coroutines.delay(if (ev.bossDefeated) 3800 else 2200)
            combatEvent = null
        }
    }

    // World Tier / NG+ celebration: when the rotation wraps, hold the banner up long enough to
    // land the moment, then auto-clear. Mirrors the combat-overlay pattern above.
    var worldTierUp by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        vm.worldTierUp.collect { ev ->
            worldTierUp = ev.newTier
            kotlinx.coroutines.delay(5000)
            worldTierUp = null
        }
    }

    // Purchase feedback: every buyItem() broadcasts a PurchaseResult; show it as a transient
    // bottom banner (success or blocked), then auto-clear. Same timed-hold pattern as above.
    var purchaseToast by remember { mutableStateOf<com.addhd.app.ui.components.PurchaseToastState?>(null) }
    // Global 1-tap quick capture: the FAB flips this on, opening a minimal add-quest dialog from
    // ANY tab so a task can be logged the instant it's thought of (executive-function friction = 0).
    var showQuickAdd by remember { mutableStateOf(false) }
    // Widget Quick-Capture deep link: when MainActivity flags an open-to-capture launch, raise the
    // dialog once and clear the flag so it won't re-fire on recomposition or config change.
    if (openQuickCaptureOnce != null) {
        LaunchedEffect(openQuickCaptureOnce.value) {
            if (openQuickCaptureOnce.value) { showQuickAdd = true; openQuickCaptureOnce.value = false }
        }
    }
    LaunchedEffect(Unit) {
        vm.purchases.collect { result ->
            purchaseToast = result.toToast()
            kotlinx.coroutines.delay(2200)
            purchaseToast = null
        }
    }

    // Consumable-use confirmation: Scroll/Potion emit a message when actually consumed, shown as
    // a positive toast so using an item is never silent. Reuses the same banner as purchases.
    LaunchedEffect(Unit) {
        vm.consumedMsg.collect { msg ->
            purchaseToast = com.addhd.app.ui.components.PurchaseToastState(msg, positive = true)
            kotlinx.coroutines.delay(2200)
            purchaseToast = null
        }
    }

    // Hero's Tavern feedback (buy/build/harvest + shortfalls). "Not enough" reads as negative.
    LaunchedEffect(Unit) {
        vm.tavernMsg.collect { msg ->
            val negative = msg.startsWith("Not enough") || msg.startsWith("Nothing")
            purchaseToast = com.addhd.app.ui.components.PurchaseToastState(msg, positive = !negative)
            kotlinx.coroutines.delay(2200)
            purchaseToast = null
        }
    }

    // Loot sell-back feedback: every sellLootItem() broadcasts a SellResult. Success reads as
    // positive (gold earned); a refused equipped-gear sale reads as negative. A NotFound (row
    // already gone from a double-tap) is a silent no-op — no toast.
    LaunchedEffect(Unit) {
        vm.sells.collect { result ->
            val toast = when (result) {
                is com.addhd.app.vm.AppViewModel.SellResult.Success ->
                    com.addhd.app.ui.components.PurchaseToastState(
                        "🪙 Sold ${result.name} for ${result.goldEarned} gold", positive = true
                    )
                com.addhd.app.vm.AppViewModel.SellResult.Equipped ->
                    com.addhd.app.ui.components.PurchaseToastState(
                        "Unequip it first — equipped gear can't be sold", positive = false
                    )
                com.addhd.app.vm.AppViewModel.SellResult.NotFound -> null
            }
            if (toast != null) {
                purchaseToast = toast
                kotlinx.coroutines.delay(2200)
                purchaseToast = null
            }
        }
    }

    // Published once here so every screen and overlay below inherits it — the perpetual
    // animations read it via com.addhd.app.ui.theme.pulse() rather than each taking the pref.
    val reduceMotion by vm.reduceMotion.collectAsState()
    val returnWelcome by vm.returnWelcome.collectAsState()

    androidx.compose.runtime.CompositionLocalProvider(
        com.addhd.app.ui.theme.LocalReduceMotion provides reduceMotion,
    ) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                // Parchment page as the bar background; crimson/leather for item states.
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.tertiary,
            ) {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination
                tabs.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,      // dark ink icon on teal pill
                            selectedTextColor = MaterialTheme.colorScheme.primary,        // teal label
                            indicatorColor = MaterialTheme.colorScheme.primary,           // vibrant-teal active pill
                            unselectedIconColor = MaterialTheme.colorScheme.tertiary,     // deep leather brown
                            unselectedTextColor = MaterialTheme.colorScheme.tertiary,
                        ),
                        selected = selected,
                        onClick = {
                            // Always land on the tab's root screen. Sub-screens (habits,
                            // journal, fidget, sos) are flat routes, so we don't save/restore
                            // per-tab stacks — tapping a tab is deterministic.
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            // Persistent, always-reachable capture. One tap opens the zero-friction sheet from any tab.
            // Small variant + extra end/bottom margin so it stays clear of list-row actions, card
            // buttons, and boss progress bars near the bottom edge instead of hovering over them.
            SmallFloatingActionButton(
                onClick = { showQuickAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(end = 8.dp, bottom = 12.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Quick capture a task") }
        },
    ) { pad ->
        Surface(Modifier.padding(pad), color = MaterialTheme.colorScheme.background) {
            NavHost(
                nav,
                startDestination = "home",
                // Gentle cross-fade between screens so navigation feels smooth, not a hard cut.
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable("home") {
                    val pet by vm.pet.collectAsState()
                    val coach by vm.coach.collectAsState()
                    val activeQuest by vm.activeQuest.collectAsState()
                    val campaigns by vm.campaigns.collectAsState()
                    val focus by vm.focusSessions.collectAsState()
                    val tasks by vm.tasks.collectAsState()
                    val quickStrike by vm.manaMatchedTasks.collectAsState()
                    val resumeTask by vm.resumeContext.collectAsState()
                    val resumeIsActive by vm.resumeIsActive.collectAsState()
                    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val today = LocalDate.now().toEpochDay()
                    val cleanDays = AppViewModel.cleanDays(pet?.cleanStreakStartEpochDay ?: 0L, today)
                    val heroClass = HeroClass.from(pet?.heroClass ?: "")

                    // First-launch gate: pick a class before the campaign begins. Wait until the
                    // pet row has loaded (non-null) so we don't flash the picker or a wrong-class
                    // hero over saved data during the first Flow emission.
                    if (pet == null) {
                        // Loading — render nothing rather than a default-state flash.
                    } else if (heroClass == null) {
                        ClassPickerScreen(onChosen = { hc, name -> vm.chooseClass(hc, name) })
                    } else {
                        val xp = pet?.xp ?: 0
                        val level = pet?.level ?: 1
                        val quest = Campaign.currentQuest(xp, level)
                        // Gritty, class/room-aware flavor. Seed by lifetime XP so it stays
                        // stable within a state but shifts as the hero progresses.
                        val flavor = Campaign.flavorFor(
                            className = heroClass?.display ?: "Your hero",
                            xp = xp, level = level,
                            seed = Campaign.totalLifetimeXp(xp, level),
                        )
                        HomeScreen(
                            pet = pet,
                            coach = coach,
                            focusMinutesToday = focus.filter { it.completed && it.epochMillis >= startOfDay }.sumOf { it.minutes },
                            tasksDoneToday = tasks.count { it.done && it.completedAtEpochDay == today },
                            cleanStreakDays = cleanDays,
                            heroClass = heroClass,
                            questName = quest.name,
                            questBoss = quest.boss,
                            roomInQuest = Campaign.roomInQuest(xp, level),
                            totalRooms = Campaign.TOTAL_ROOMS,
                            flavorHeadline = flavor.headline,
                            flavorLine = flavor.line,
                            activeQuest = activeQuest,
                            campaigns = campaigns,
                            onSelectCampaign = { key -> vm.selectCampaign(key) },
                            onAction = { action -> nav.navigate(routeFor(action)) },
                            onOpenSos = { nav.navigate("sos") },
                            onQuickWin = { title -> vm.completeQuickWin(title) },
                            onOpenStreak = { nav.navigate("streak") },
                            onDefeatBoss = { nav.navigate("fidget?boss=true") },
                            // Up to 3 lowest-effort open quests for the Home "Quick Strike" sheet.
                            // Taken from the ViewModel's ranked flow rather than re-sorting here,
                            // so there's one ranking rule instead of two copies that can drift.
                            quickStrikeTasks = quickStrike.take(3),
                            onCompleteTask = { t -> vm.toggleTask(t) },
                            resumeTask = resumeTask,
                            resumeIsActive = resumeIsActive,
                            // Answering the reminder commits to the quest (so a later block is
                            // attributed to it without asking) and quiets the card until tomorrow.
                            // No navigation — see the comment on the card in HomeScreen.
                            onStillOnIt = { t -> vm.acknowledgeStillWorkingOn(t) },
                        )
                    }
                }
                composable("streak") {
                    val pet by vm.pet.collectAsState()
                    val owned by vm.ownedItems.collectAsState()
                    val stronghold by vm.strongholdUiState.collectAsState()
                    val today = LocalDate.now().toEpochDay()
                    val cleanDays = AppViewModel.cleanDays(pet?.cleanStreakStartEpochDay ?: 0L, today)
                    val elixirCount = owned.firstOrNull { it.itemId == "elixir_willpower" }?.qty ?: 0
                    StreakScreen(
                        cleanDays = cleanDays,
                        multiplier = AppViewModel.streakMultiplier(cleanDays),
                        elixirCount = elixirCount,
                        streakFreezeCharges = stronghold.chronoCharges,
                        hasChronoAnchor = stronghold.chronoAnchorPurchased,
                        onReset = { vm.resetCleanStreak() },
                    )
                }
                composable("tasks") {
                    val tasks by vm.tasks.collectAsState()
                    val owned by vm.ownedItems.collectAsState()
                    val scrollCount = owned.firstOrNull { it.itemId == "scroll_teleport" }?.qty ?: 0
                    TasksScreen(
                        tasks = tasks,
                        onAdd = vm::addTask,
                        onToggle = vm::toggleTask,
                        onDelete = vm::deleteTask,
                        onAutoSchedule = { vm.autoScheduleToday() },
                        onClearSchedule = { vm.clearSchedule() },
                        scrollCount = scrollCount,
                        onSkip = { t -> vm.useScrollOnTask(t) },
                        onAddSubtask = { parentId, title -> vm.addSubtask(parentId, title) },
                        onScheduleForToday = { t -> vm.scheduleForToday(t) },
                    )
                }
                composable("focus") {
                    val focusTimer by vm.focusTimer.collectAsState()
                    val quickStrikeForFocus by vm.quickStrikeTasks.collectAsState()
                    val canAttribute by vm.canAttributeFocus.collectAsState()
                    val attributedTo by vm.focusAttributedTo.collectAsState()
                    val focusTarget by vm.focusTarget.collectAsState()
                    FocusScreen(
                        timer = focusTimer,
                        onPickMinutes = { m -> vm.setFocusMinutes(m) },
                        onStart = { min -> vm.startFocus(min) },
                        onPause = { vm.pauseFocus() },
                        onResume = { vm.resumeFocus() },
                        onReset = { vm.resetFocus() },
                        onDismissFinish = { vm.dismissFocusFinish() },
                        onTacticalRetreat = { nav.navigate("sos") },
                        onStartHype = { launchHypeMusic(context) },
                        attributionCandidates = quickStrikeForFocus.take(4),
                        canAttribute = canAttribute,
                        attributedTo = attributedTo,
                        onAttribute = { t -> vm.attributeFocusTo(t) },
                        onAddStep = { parentId, title -> vm.addSubtask(parentId, title) },
                        focusTarget = focusTarget,
                    )
                }
                composable("wellbeing") {
                    val moods by vm.moods.collectAsState()
                    val ceiling by vm.effortCeiling.collectAsState()
                    val matched by vm.manaMatchedTasks.collectAsState()
                    StatusScreen(
                        moods = moods,
                        onLog = vm::logMood,
                        effortCeiling = ceiling,
                        matchedTasks = matched,
                        onCompleteTask = { t -> vm.toggleTask(t) },
                        onStartFocus = { nav.navigate("focus") },
                        onOpenSanctuary = { nav.navigate("sos") },
                    )
                }
                composable("more") {
                    MoreScreen(nav)
                }
                composable("insights") {
                    val insights by vm.insights.collectAsState()
                    val calibration by vm.calibration.collectAsState()
                    val allTime by vm.allTime.collectAsState()
                    InsightsScreen(data = insights, calibration = calibration, allTime = allTime)
                }
                composable(
                    "fidget?boss={boss}",
                    arguments = listOf(navArgument("boss") { defaultValue = "false" }),
                ) { entry ->
                    val bossMode = entry.arguments?.getString("boss") == "true"
                    val quest by vm.activeQuest.collectAsState()
                    val petState by vm.pet.collectAsState()
                    // combatStats folds the equipped weapon's bonus into the roll (gear matters).
                    val stats by vm.combatStats.collectAsState()
                    val heroClass = HeroClass.from(petState?.heroClass ?: "")
                    val atk = com.addhd.app.campaign.attackModifier(heroClass, stats, petState?.level ?: 1)
                    // LATCH the encounter once entered. defeatBoss()/bossDownedHero() mutate the
                    // gate mid-fight (bossReady flips false), which would otherwise swap the combat
                    // screen out for the free-play dice before the WON/LOST screen can show. We
                    // snapshot the boss identity on entry and hold it for the whole session.
                    var latchedBoss by rememberSaveable { mutableStateOf<String?>(null) }
                    var latchedMod by rememberSaveable { mutableStateOf(0) }
                    var latchedStat by rememberSaveable { mutableStateOf("STR") }
                    if (bossMode && latchedBoss == null && quest?.bossReady == true) {
                        latchedBoss = quest?.milestoneTitle
                        latchedMod = atk.value
                        latchedStat = atk.statLabel
                    }
                    FidgetScreen(
                        rolls = vm.fidgetRolls,
                        onRoll = { vm.rollFidgetDice() },
                        rewards = vm.fidgetRewards,
                        onAwardGameXp = { vm.awardFidgetGameXp() },
                        bossName = latchedBoss,
                        bossAc = AppViewModel.BOSS_AC,
                        onBossDefeated = { vm.defeatBoss() },
                        attackMod = latchedMod,
                        attackStatLabel = latchedStat,
                        onHeroDowned = { vm.bossDownedHero() },
                    )
                }
                composable("sos") {
                    SosScreen(
                        onLog = vm::logDopamine,
                        wisdomPool = com.addhd.app.vm.AppViewModel.ORACLE_WISDOM,
                        blessingRewards = vm.blessing,
                    )
                }
                composable("habits") {
                    val habits by vm.habits.collectAsState()
                    val checks by vm.habitChecks.collectAsState()
                    HabitsScreen(habits = habits, checks = checks, onAdd = vm::addHabit, onToggle = vm::toggleHabit, onDelete = vm::deleteHabit, onReset = vm::resetHabitStreak, onToggleDay = vm::toggleHabitDay)
                }
                composable("journal") {
                    val entries by vm.journal.collectAsState()
                    JournalScreen(entries = entries, onAdd = vm::addJournal, onDelete = vm::deleteJournal)
                }
                composable("settings") {
                    val soundOn by vm.soundEnabled.collectAsState()
                    val nMaster by vm.notifMaster.collectAsState()
                    val nTimeBlock by vm.notifTimeBlock.collectAsState()
                    val nStreak by vm.notifStreak.collectAsState()
                    val nMorning by vm.notifMorning.collectAsState()
                    SettingsScreen(
                        onResetAll = { vm.resetAllData() },
                        soundEnabled = soundOn,
                        onSoundEnabledChange = { vm.setSoundEnabled(it) },
                        reduceMotion = reduceMotion,
                        onReduceMotionChange = { vm.setReduceMotion(it) },
                        notifMaster = nMaster,
                        onNotifMasterChange = { vm.setNotifMaster(it) },
                        notifTimeBlock = nTimeBlock,
                        onNotifTimeBlockChange = { vm.setNotifTimeBlock(it) },
                        notifStreak = nStreak,
                        onNotifStreakChange = { vm.setNotifStreak(it) },
                        notifMorning = nMorning,
                        onNotifMorningChange = { vm.setNotifMorning(it) },
                    )
                }
                composable("character") {
                    val pet by vm.pet.collectAsState()
                    val stats by vm.heroStats.collectAsState()
                    val loot by vm.loot.collectAsState()
                    val lifetimeQuests by vm.lifetimeQuestsCompleted.collectAsState()
                    val worldTier by vm.worldTier.collectAsState()
                    val owned by vm.ownedItems.collectAsState()
                    val strongholdState by vm.strongholdUiState.collectAsState()
                    CharacterScreen(
                        pet = pet,
                        heroClass = HeroClass.from(pet?.heroClass ?: ""),
                        stats = stats,
                        loot = loot,
                        lifetimeQuests = lifetimeQuests,
                        worldTier = worldTier,
                        ascended = strongholdState.ascensionPurchased,
                        ownedItems = owned,
                        onToggleEquip = { id, eq -> vm.setLootEquipped(id, eq) },
                        onToggleArtifact = { id, eq -> vm.setArtifactEquipped(id, eq) },
                        onSellLoot = { id -> vm.sellLootItem(id) },
                    )
                }
                composable("stronghold") {
                    val state by vm.strongholdUiState.collectAsState()
                    val gold by vm.gold.collectAsState()
                    com.addhd.app.ui.screens.StrongholdScreen(
                        state = state,
                        gold = gold,
                        onBuyTavern = { vm.buyTavern() },
                        onBuildGarden = { vm.buildGarden() },
                        onHarvest = { vm.harvestGarden() },
                        onUnlockTrophyRoom = { vm.unlockTrophyRoom() },
                        onPurchaseAscension = { vm.purchaseAscension() },
                        onPurchaseChronoAnchor = { vm.purchaseChronoAnchor() },
                        onUnlockVault = { vm.unlockCoinVault() },
                        onUpgradeVault = { vm.upgradeCoinVault() },
                        onClaimStipend = { vm.claimStipend() },
                    )
                }
                composable("shop") {
                    val gold by vm.gold.collectAsState()
                    val owned by vm.ownedItems.collectAsState()
                    val potionPrimed by vm.xpPotionPrimed.collectAsState()
                    com.addhd.app.ui.screens.ShopScreen(
                        gold = gold,
                        owned = owned,
                        xpPotionPrimed = potionPrimed,
                        onBuy = { id -> vm.buyItem(id) },
                        onEquip = { id, eq -> vm.setArtifactEquipped(id, eq) },
                        onUsePotion = { vm.usePotionOfFocus() },
                    )
                }
            }
        }
    }
        com.addhd.app.ui.components.ReturnWelcomeDialog(
            welcome = returnWelcome,
            onStartSuggestion = { vm.dismissReturnWelcome(); nav.navigate("tasks") },
            onDismiss = { vm.dismissReturnWelcome() },
        )
        CombatOverlay(event = combatEvent)
        com.addhd.app.ui.components.WorldTierOverlay(newTier = worldTierUp)
        com.addhd.app.ui.components.PurchaseToast(state = purchaseToast)
        QuickAddQuestDialog(
            visible = showQuickAdd,
            onDismiss = { showQuickAdd = false },
            onAdd = { title -> vm.quickCapture(title); showQuickAdd = false },
        )
    }
    }
}

private fun routeFor(action: com.addhd.app.coach.SuggestedAction): String = action.route

/**
 * Minimal 1-field quick-capture dialog opened by the global FAB. Defaults every quest to a tiny
 * (energy 1, 5-min, inbox) quest so capture is instant — the hero can refine effort/time later on
 * the Tasks screen. A mic button fires the system voice-recognition intent (which handles the
 * RECORD_AUDIO prompt + listening UI itself); the transcript drops straight into the title field so
 * the hero can eyeball it before posting. Local [title] state resets on each open (the early return
 * discards it while hidden).
 */
@Composable
private fun QuickAddQuestDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    if (!visible) return
    var title by remember { mutableStateOf("") }
    // System speech recognizer: no manual permission plumbing — the intent owns the mic prompt and
    // the listening dialog. On OK we take the top transcript hypothesis into the title field.
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) title = spoken
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚡ Quick capture") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Name it, or tap the mic…") },
                singleLine = true,
                shape = RoundedCornerShape(Radii.button),
                trailingIcon = {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your quest…")
                        }
                        voiceLauncher.launch(intent)
                    }) {
                        Icon(Icons.Filled.Mic, contentDescription = "Speak to capture")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onAdd(title) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(Radii.button),
            ) { Text("Post to Quest Log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Summon the Bard's Song: bring the hero's streaming app to the foreground so they're one tap
 * from their own focus music. Prefers YouTube Music, then Spotify; if neither is installed, falls
 * back to whatever music app the device offers (CATEGORY_APP_MUSIC chooser). Just opens the app —
 * no deep-link or search seed (those break across app versions). No network/accounts of our own.
 */
private fun launchHypeMusic(context: android.content.Context) {
    // Preference order: YouTube Music first, then Spotify. getLaunchIntentForPackage returns null
    // when the package isn't installed, so this doubles as install-detection with no <queries>.
    val preferred = listOf(
        "com.google.android.apps.youtube.music",  // YouTube Music
        "com.spotify.music",                      // Spotify
    )
    for (pkg in preferred) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return   // launched the preferred app — done
        } catch (_: Exception) { /* fall through to the next candidate / generic fallback */ }
    }

    // Neither installed (or both failed to launch): open the device's default music app.
    try {
        val music = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_APP_MUSIC)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(music)
    } catch (_: Exception) { /* no music app / no launcher category — ignore */ }
}
