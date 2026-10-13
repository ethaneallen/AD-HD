package com.addhd.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.data.TaskEntity
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.ui.theme.Radii
import com.addhd.app.util.Scheduler

/** Backlog Blur kicks in only once the open list exceeds this many quests. */
private const val BACKLOG_BLUR_THRESHOLD = 5

/** Native time picker → returns the chosen start time as minutes-from-midnight. */
private fun showTimePicker(context: android.content.Context, initialMin: Int, onPicked: (Int) -> Unit) {
    android.app.TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initialMin / 60, initialMin % 60, false,
    ).show()
}

@Composable
fun TasksScreen(
    tasks: List<TaskEntity>,
    onAdd: (String, Int, Int, Int?) -> Unit,   // last arg: pinned start-minute, or null
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onAutoSchedule: () -> Unit,
    onClearSchedule: () -> Unit,
    scrollCount: Int = 0,             // Scrolls of Teleportation owned (gates the per-task skip button)
    onSkip: (TaskEntity) -> Unit = {},
    onAddSubtask: (Long, String) -> Unit = { _, _ -> },   // (parentId, title) -> add a micro-step
    onScheduleForToday: (TaskEntity) -> Unit = {},         // triage an inbox item onto today's list
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf("") }
    var energy by remember { mutableIntStateOf(2) }
    var estimate by remember { mutableIntStateOf(25) }
    // Optional manual quest time (minutes-from-midnight). null = let the scheduler place it.
    var pinnedStartMin by remember { mutableStateOf<Int?>(null) }
    // Task Jar: id of the single drawn "Active Quest". When set, the list enters focus mode —
    // that task is spotlighted and the rest are dimmed to kill choice paralysis.
    var activeQuestId by remember { mutableStateOf<Long?>(null) }
    // Done list collapses by default — it grows huge and is just history.
    var doneExpanded by remember { mutableStateOf(false) }

    val today = java.time.LocalDate.now().toEpochDay()
    // Inbox section collapses on its own; unscheduled captures live here until triaged.
    var inboxExpanded by remember { mutableStateOf(true) }
    // Backlog Blur / "Rule of Three": once the open list gets long it defaults to just the top 3
    // easiest quests so a big backlog never becomes a wall of overwhelm. Purely a view toggle.
    var backlogExpanded by remember { mutableStateOf(false) }
    // Micro-steps render nested under their parent, so the main lists carry TOP-LEVEL quests only.
    val subtasksByParent = tasks.filter { it.parentId != null }.groupBy { it.parentId!! }
    // Inbox: one-tap captures with no due date yet. Surfaced at the top so nothing gets lost, but
    // held OUT of the main Open list so the active quest log stays "things I committed to today".
    val inbox = tasks.filter { !it.done && it.parentId == null && it.dueEpochDay == null }
        .sortedByDescending { it.createdAt }
    val open = tasks.filter { !it.done && it.parentId == null && it.dueEpochDay != null }
    // Midnight clean slate: the Done list shows only tasks finished TODAY. Older completions
    // stay in the Quest Archive (DB) for 30 days but don't clutter the active list.
    val done = tasks.filter { it.done && it.completedAtEpochDay == today && it.parentId == null }
    // Daily habits/rituals are intentionally NOT surfaced here — they live exclusively on the
    // Habits (Rituals) screen. The Quest Log is one-off active quests only.
    // The currently-drawn Active Quest, resolved live. Clears itself if that task got
    // completed or deleted so focus mode never points at a stale row.
    val activeQuest = activeQuestId?.let { id -> open.firstOrNull { it.id == id } }
    if (activeQuestId != null && activeQuest == null) activeQuestId = null
    val focusMode = activeQuest != null
    // Roll for Priority: let fate pick one open quest, with a dice clatter + haptic for the roll feel.
    fun drawFromJar() {
        open.randomOrNull()?.let {
            activeQuestId = it.id
            com.addhd.app.util.Haptics.diceRoll(context)
            com.addhd.app.util.Sound.play(context, com.addhd.app.util.Sfx.SFX_DICE_ROLL)
        }
    }
    val scheduled = tasks.filter { it.blockStartMin != null && !it.done }
        .sortedBy { it.blockStartMin }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                // Decision-paralysis solver: one tap and fate spotlights a single open quest.
                if (open.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { drawFromJar() },
                        shape = RoundedCornerShape(Radii.button),
                    ) { Text("🎲 Roll for Priority") }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Log your active quests here. Accept them to your log, then let your campaign map help you conquer them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Inbox / Unscheduled Quests — everything caught via one-tap capture lands here with no due
        // date. Pinned to the top so nothing is forgotten, with a per-card "Schedule for Today" tap
        // to promote it into the active Open list. Collapsible; hidden entirely when empty.
        if (inbox.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { inboxExpanded = !inboxExpanded }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${if (inboxExpanded) "▾" else "▸"}  📥 Inbox — Unscheduled (${inbox.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (inboxExpanded) {
                items(inbox, key = { "inbox" + it.id }) { t ->
                    Card(
                        Modifier.fillMaxWidth().animateItem(),
                        shape = RoundedCornerShape(Radii.card),
                        colors = CardDefaults.cardColors(containerColor = com.addhd.app.ui.theme.RaisedSurface.color),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                            FilledTonalButton(
                                onClick = { onScheduleForToday(t) },
                                shape = RoundedCornerShape(Radii.button),
                            ) { Text("Schedule for Today") }
                            IconButton(onClick = { onDelete(t) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Roll for Priority — once fate picks a quest, spotlight it in the "Fate Directs You" callout
        // so there's exactly one thing to face. The roll trigger lives in the header above.
        if (focusMode) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "🎲 Fate Directs You",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            activeQuest!!.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Just do this one thing. Everything else can wait.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onToggle(activeQuest); activeQuestId = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onPrimary,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) { Text("Done ✓", fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = { drawFromJar() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.onPrimary),
                            ) { Text("🎲 Reroll") }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { activeQuestId = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Dismiss — show all tasks", color = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        }

        item {
            SectionCard {
                // Subtle selected fill (pale teal) so the effort/estimate chips never overpower the
                // filled "Post to Quest Log" primary button below; unselected chips are outlined only.
                val questChipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name your quest…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),   // match the app's rounded inputs/cards
                )
                Spacer(Modifier.height(10.dp))
                Text("Effort", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Tiny", 2 to "Medium", 3 to "Big").forEach { (v, lbl) ->
                        FilterChip(
                            selected = energy == v, onClick = { energy = v }, label = { Text(lbl) },
                            colors = questChipColors,
                            border = com.addhd.app.ui.components.craftChipBorder(energy == v, onLightBackground = false),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Estimate: $estimate min", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 25, 45, 60).forEach { m ->
                        FilterChip(
                            selected = estimate == m, onClick = { estimate = m }, label = { Text("$m") },
                            colors = questChipColors,
                            border = com.addhd.app.ui.components.craftChipBorder(estimate == m, onLightBackground = false),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Optional manual time lock. Off = auto-scheduler places it; on = pinned anchor.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⏰ Set quest time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (pinnedStartMin != null) {
                        Text(
                            Scheduler.fmt(pinnedStartMin!!),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showTimePicker(context, pinnedStartMin!!) { pinnedStartMin = it } }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Switch(
                        checked = pinnedStartMin != null,
                        onCheckedChange = { on ->
                            if (on) showTimePicker(context, 9 * 60) { pinnedStartMin = it }
                            else pinnedStartMin = null
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Primary action: a filled bright-teal button with bold high-contrast text — the
                // most prominent element in the creation card.
                Button(
                    onClick = { onAdd(title, energy, estimate, pinnedStartMin); title = ""; pinnedStartMin = null },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        "Post to Quest Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),   // +4 over the list's 12.dp gap = 16.dp breathing room
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onAutoSchedule, modifier = Modifier.weight(1f)) {
                    Text("⏱ Plan Today's Adventure")
                }
                TextButton(onClick = onClearSchedule) { Text("Clear") }
            }
        }

        if (scheduled.isNotEmpty()) {
            item {
                Text("Today's time blocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(scheduled, key = { "b" + it.id }) { t ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Scheduler.fmt(t.blockStartMin ?: 0),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(t.title, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }

        item {
            Text(
                "Open (${open.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Backlog Blur: when there are more than 5 open quests, rank them by lowest effort (the same
        // energy*100+estimate ordering quickStrikeTasks uses) and show only the top 3 until expanded.
        // Never blurs while focus mode owns the screen. Purely presentational — no DB change.
        val ruleOfThree = !focusMode && open.size > BACKLOG_BLUR_THRESHOLD && !backlogExpanded
        val ranked = if (ruleOfThree) open.sortedBy { it.energy * 100 + it.estimateMin } else open
        val visibleOpen = if (ruleOfThree) ranked.take(3) else ranked
        items(visibleOpen, key = { it.id }) { t ->
            // Focus mode: the Active Quest stays full-strength; all others dim back.
            val dim = if (focusMode && t.id != activeQuestId) 0.35f else 1f
            TaskRow(
                t = t,
                subtasks = subtasksByParent[t.id].orEmpty(),
                modifier = Modifier.animateItem().alpha(dim),
                onToggle = onToggle,
                onDelete = onDelete,
                onAddSubtask = onAddSubtask,
                scrollCount = scrollCount,
                onSkip = onSkip,
            )
        }
        // Rule-of-Three toggle: only shown once the list is long enough to blur. Expands to the full
        // backlog or collapses back to the top 3. Hidden entirely in focus mode.
        if (!focusMode && open.size > BACKLOG_BLUR_THRESHOLD) {
            item {
                val hidden = open.size - 3
                TextButton(
                    onClick = { backlogExpanded = !backlogExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (backlogExpanded) "Collapse to top 3" else "Show all ($hidden hidden)")
                }
            }
        }

        if (done.isNotEmpty()) {
            item {
                // Tappable header toggles the (long) history list. Collapsed by default.
                Row(
                    Modifier.fillMaxWidth().clickable { doneExpanded = !doneExpanded }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${if (doneExpanded) "▾" else "▸"}  Done (${done.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (doneExpanded) {
                items(done, key = { it.id }) { t -> TaskRow(t = t, modifier = Modifier.animateItem(), onToggle = onToggle, onDelete = onDelete) }
            }
        }
    }

}

@Composable
private fun TaskRow(
    t: TaskEntity,
    subtasks: List<TaskEntity> = emptyList(),
    modifier: Modifier = Modifier,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onAddSubtask: (Long, String) -> Unit = { _, _ -> },
    scrollCount: Int = 0,
    onSkip: (TaskEntity) -> Unit = {},
) {
    // Juice: a teal flash sweeps across the card the moment a task is checked done, then fades.
    var justCompleted by remember { mutableStateOf(false) }
    val flash by animateColorAsState(
        targetValue = if (justCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(if (justCompleted) 120 else 600),
        label = "taskFlash",
        finishedListener = { if (justCompleted) justCompleted = false },
    )
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.card),
        colors = CardDefaults.cardColors(containerColor = flash),
    ) {
      Column {
        Row(Modifier.padding(start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = t.done,
                onCheckedChange = { checked -> if (checked) justCompleted = true; onToggle(t) },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val dot = when (t.energy) { 1 -> "· tiny"; 3 -> "· big"; else -> "· medium" }
                // Step progress hint when this quest has been broken down.
                val stepHint = if (subtasks.isNotEmpty()) "  ·  ⚔️ ${subtasks.count { it.done }}/${subtasks.size} steps" else ""
                Text("${t.estimateMin} min $dot$stepHint", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Scroll of Teleportation: only shown on OPEN tasks when at least one Scroll is owned.
            // Teleports past this task (marks done, XP only, no campaign credit).
            if (!t.done && scrollCount > 0) {
                IconButton(onClick = { onSkip(t) }) {
                    Text("📜", style = MaterialTheme.typography.titleMedium, modifier = Modifier.decorative())
                }
            }
            IconButton(onClick = { onDelete(t) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Breakdown / micro-steps live inside the parent card, only while the quest is open.
        if (!t.done) {
            SubtaskSection(
                parentId = t.id,
                subtasks = subtasks,
                onToggle = onToggle,
                onDelete = onDelete,
                onAddSubtask = onAddSubtask,
            )
        }
      }
    }
}

/**
 * Nested breakdown control shown under an open parent quest. Collapsed by default; the header
 * toggles the step list + an inline "add a micro-step" field. When there are no steps yet it reads
 * "Break into smaller strikes" to invite task-initiation chunking; once steps exist it shows the
 * done/total tally. Steps are ordinary child tasks, so each checkbox completion awards XP.
 */
@Composable
private fun SubtaskSection(
    parentId: Long,
    subtasks: List<TaskEntity>,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onAddSubtask: (Long, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var newStep by remember { mutableStateOf("") }
    val doneCount = subtasks.count { it.done }

    Column(Modifier.padding(start = 46.dp, end = 10.dp, bottom = 8.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(Radii.chip))
                .clickable { expanded = !expanded }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (subtasks.isEmpty()) "⚔️ Break into smaller strikes"
                else "${if (expanded) "▾" else "▸"}  Steps $doneCount/${subtasks.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            subtasks.forEach { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = st.done, onCheckedChange = { onToggle(st) })
                    Text(
                        st.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (st.done) TextDecoration.LineThrough else null,
                        color = if (st.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { onDelete(st) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete step", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newStep,
                    onValueChange = { newStep = it },
                    placeholder = { Text("Add a micro-step…") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radii.chip),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (newStep.isNotBlank()) { onAddSubtask(parentId, newStep); newStep = "" } },
                    enabled = newStep.isNotBlank(),
                    // Without this the control announces as an unnamed button: the emoji is its
                    // only content, so there is nothing for a screen reader to read.
                    modifier = Modifier.describedAs("Add step"),
                ) {
                    Text("➕", style = MaterialTheme.typography.titleMedium, modifier = Modifier.decorative())
                }
            }
        }
    }
}
