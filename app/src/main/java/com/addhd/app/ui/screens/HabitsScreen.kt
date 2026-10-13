package com.addhd.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.decorative
import com.addhd.app.ui.components.describedAs
import com.addhd.app.data.HabitCheckEntity
import com.addhd.app.data.HabitEntity
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.ui.theme.Radii
import com.addhd.app.util.StreakCalc
import java.time.LocalDate

@Composable
fun HabitsScreen(
    habits: List<HabitEntity>,
    checks: List<HabitCheckEntity>,
    onAdd: (String, String) -> Unit,
    onToggle: (HabitEntity, Boolean) -> Unit,
    onDelete: (HabitEntity) -> Unit,
    onReset: (HabitEntity) -> Unit,
    onToggleDay: (HabitEntity, Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📜") }
    var resetTarget by remember { mutableStateOf<HabitEntity?>(null) }
    var calendarTarget by remember { mutableStateOf<HabitEntity?>(null) }
    val today = LocalDate.now().toEpochDay()
    // Theme tokens for text/borders/icons on the dark leather cards (cream + muted tan). Pulled
    // from the color scheme so BOTH light and dark themes resolve correctly instead of fixed hex
    // values that only looked right in the light theme.
    val cream = MaterialTheme.colorScheme.onSurface
    val tan = MaterialTheme.colorScheme.onSurfaceVariant

    fun streakOf(habitId: Long): StreakCalc.Result {
        val days = checks.asSequence().filter { it.habitId == habitId }.map { it.epochDay }.toSet()
        return StreakCalc.compute(days, today)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            Text("Habits", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "A hero is forged through iron discipline. Record your daily rituals here. Maintain your streak to honor your vows; break the chain, and the counter returns to dust. Faltered today? Strike the mark to reset the vow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            SectionCard {
                // Force cream text/label/border so the fields read on the dark leather card,
                // instead of the theme's crimson defaults that vanish against the brown.
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = cream,
                    unfocusedTextColor = cream,
                    cursorColor = cream,
                    focusedBorderColor = cream,
                    unfocusedBorderColor = tan,
                    focusedLabelColor = cream,
                    unfocusedLabelColor = tan,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 2) emoji = it },
                        label = { Text("Icon") },
                        modifier = Modifier.width(84.dp),
                        singleLine = true,
                        colors = fieldColors,
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name your ritual…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Primary action: a filled bright-teal button so "Inscribe Ritual" is the obvious
                // next tap, consistent with the primary buttons on Tasks/Status.
                Button(
                    onClick = { onAdd(name, emoji); name = ""; emoji = "📜" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(Radii.button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text("Inscribe Ritual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
        }
        items(habits, key = { it.id }) { h ->
            val checkedToday = checks.any { it.habitId == h.id && it.epochDay == today }
            val res = streakOf(h.id)
            val s = res.streak
            Card(
                // Tapping anywhere on the card toggles today's check-in — same action as the box.
                onClick = { onToggle(h, !checkedToday) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(h.name, style = MaterialTheme.typography.titleMedium, color = cream)
                            Spacer(Modifier.height(2.dp))
                            if (s > 0) {
                                // Prominent, high-contrast tally. Stays visible after midnight until
                                // a full day is missed, because StreakCalc counts from yesterday when
                                // today isn't ticked yet.
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("🔥 ", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.decorative())
                                    Text(
                                        "$s",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = cream,
                                    )
                                    Text(
                                        if (s == 1) " day streak" else " days streak",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cream,
                                        modifier = Modifier.padding(bottom = 3.dp),
                                    )
                                }
                                if (!checkedToday) {
                                    Text(
                                        "Check in to keep it going",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tan,
                                    )
                                }
                            } else {
                                Text(
                                    "Start a streak today",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cream,
                                )
                            }
                        }
                        Checkbox(checked = checkedToday, onCheckedChange = { onToggle(h, it) })
                        IconButton(onClick = { calendarTarget = h }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Open calendar", tint = cream)
                        }
                        IconButton(onClick = { onDelete(h) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete habit", tint = cream)
                        }
                    }
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        TextButton(onClick = { calendarTarget = h }) {
                            Text("📅 Log past days", color = tan)
                        }
                        // Manual "I slipped up" reset — clears the streak to 0 immediately.
                        if (s > 0) {
                            TextButton(onClick = { resetTarget = h }) {
                                Text("↺ Reset streak", color = tan)
                            }
                        }
                    }
                }
            }
        }
    }

    resetTarget?.let { h ->
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            icon = { Text("↺", style = MaterialTheme.typography.headlineMedium) },
            title = { Text("Reset “${h.name}” to 0?") },
            text = { Text("This clears the current streak right now. Your habit stays; only the day count is wiped. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onReset(h); resetTarget = null }) { Text("Reset to 0") }
            },
            dismissButton = {
                TextButton(onClick = { resetTarget = null }) { Text("Keep my streak") }
            },
        )
    }

    calendarTarget?.let { h ->
        val checkedDays = remember(checks, h.id) {
            checks.asSequence().filter { it.habitId == h.id }.map { it.epochDay }.toSet()
        }
        HabitCalendarDialog(
            habit = h,
            checkedDays = checkedDays,
            today = today,
            onToggleDay = { day -> onToggleDay(h, day) },
            onDismiss = { calendarTarget = null },
        )
    }
}

/**
 * A month-grid calendar for one habit. Tap any past or present day to toggle its check —
 * used to backfill days the user completed before adding the habit (or fix a missed check).
 * Future days are shown disabled so the streak can't be filled forward.
 */
@Composable
private fun HabitCalendarDialog(
    habit: HabitEntity,
    checkedDays: Set<Long>,
    today: Long,
    onToggleDay: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val cream = MaterialTheme.colorScheme.onSurface
    val tan = MaterialTheme.colorScheme.onSurfaceVariant
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val todayDate = LocalDate.ofEpochDay(today)
    var month by remember { mutableStateOf(java.time.YearMonth.from(todayDate)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("${habit.emoji}  ${habit.name}") },
        text = {
            Column {
                // Month header with prev/next navigation. Can't page past the current month.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", color = cream) }
                    Text(
                        "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                        style = MaterialTheme.typography.titleMedium,
                        color = cream,
                    )
                    val atCurrentMonth = month == java.time.YearMonth.from(todayDate)
                    TextButton(onClick = { month = month.plusMonths(1) }, enabled = !atCurrentMonth) {
                        Text("›", color = if (atCurrentMonth) dim else cream)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Weekday header (Mon-first to match epochDay math where Mon = 0).
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                        Text(
                            d,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tan,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                val daysInMonth = month.lengthOfMonth()
                // DayOfWeek: Mon=1..Sun=7 -> leading blanks so day 1 lands under its weekday.
                val firstOffset = month.atDay(1).dayOfWeek.value - 1
                val cells = firstOffset + daysInMonth
                val rows = (cells + 6) / 7

                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val dayNum = cellIndex - firstOffset + 1
                            if (dayNum < 1 || dayNum > daysInMonth) {
                                Spacer(Modifier.weight(1f).height(40.dp))
                            } else {
                                val epochDay = month.atDay(dayNum).toEpochDay()
                                val checked = checkedDays.contains(epochDay)
                                val isFuture = epochDay > today
                                val isToday = epochDay == today
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                checked -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            if (isFuture) Modifier
                                            else Modifier.clickable { onToggleDay(epochDay) }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isFuture -> dim
                                            checked -> MaterialTheme.colorScheme.onPrimary
                                            else -> cream
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap a day to mark it done. Fill in days you completed before adding this habit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tan,
                )
            }
        },
    )
}
