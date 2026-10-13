package com.addhd.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addhd.app.ui.components.SectionCard
import com.addhd.app.vm.AppViewModel

/**
 * "Your Patterns" — a reflective, judgment-free dashboard built entirely from data the app already
 * collects (focus sessions, completed quests, mood logs, streak). No goals, no red numbers, no
 * "you failed to…" copy: every line frames what the hero is ALREADY doing well and where their
 * natural rhythm lies. Metrics that lack a real sample are hidden rather than shown as a hollow 0.
 */
@Composable
fun InsightsScreen(
    data: AppViewModel.InsightsData,
    calibration: List<AppViewModel.Calibration> = emptyList(),
    allTime: AppViewModel.AllTime? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Extra bottom room so the last card can be scrolled clear of the global FAB, which
            // otherwise sits on top of the chart's final day — hiding the most recent bar, the one
            // you're most likely to be looking for.
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Your Patterns", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "A quiet look at your rhythm — no scores, no pressure. Just what your own weeks reveal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (!data.hasEnoughData) {
            SectionCard {
                Text("Your story starts here", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Complete a few quests, run a focus block, or log your mood — and your patterns " +
                        "will begin to appear here. There's nothing to get right; just live a few days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // --- This week at a glance: three warm stat tiles ---
        SectionCard {
            Text("This week", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("⚔️", "${data.weeklyQuests}", "quests cleared", Modifier.weight(1f))
                StatTile("⏳", "${data.weeklyFocusMin}", "focus minutes", Modifier.weight(1f))
                StatTile("🔥", "${data.streakDays}", "day streak", Modifier.weight(1f))
            }
        }

        // --- All time ---
        // Every other panel here is a 7-day window, so the app could never show you more than one
        // week of yourself no matter how long you'd used it. These totals only grow, and they're
        // what make a lapse survivable: proof the time already spent still counts.
        if (allTime != null && allTime.hasHistory) {
            SectionCard {
                Text("All time", style = MaterialTheme.typography.titleMedium)
                if (allTime.daysKnown > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${allTime.daysKnown} days of history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "⏳",
                        com.addhd.app.ui.components.focusLabel(allTime.focusMinutes),
                        "focused",
                        Modifier.weight(1f),
                    )
                    StatTile("⚔️", "${allTime.questsCompleted}", "quests cleared", Modifier.weight(1f))
                    StatTile("🧙", "Lv ${allTime.level}", "reached", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "These never reset. Nothing here can be lost by taking a break.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Peak focus window ---
        if (data.peakWindow != null) {
            SectionCard {
                Text("🕰️  Your peak focus window", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    data.peakWindow,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This is when your focus tends to land. Save your hardest quest for this window and " +
                        "let the easy hours carry the rest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Strongest day ---
        if (data.strongestDay != null) {
            SectionCard {
                Text("📅  Your power day", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    data.strongestDay,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You clear more quests on ${data.strongestDay}s than any other day. Worth protecting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Time estimates vs. reality ---
        // Shown only once there's attributed focus time to compare. Framed as calibration, not
        // failure: "how long things actually take you" is useful information, and under-estimating
        // is the norm, not a personal defect.
        if (calibration.isNotEmpty()) {
            SectionCard {
                Text("⏱️  Guess vs. reality", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val avg = calibration.map { it.ratio }.average()
                Text(
                    when {
                        avg >= 1.25 -> "Things take you about %.1fx longer than you plan for.".format(avg)
                        avg <= 0.8 -> "You finish in about %.0f%% of the time you budget.".format(avg * 100)
                        else -> "Your estimates are landing close to reality."
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        avg >= 1.25 -> "Not a scolding — just calibration. Padding your next " +
                            "estimate by that much is usually all it takes."
                        avg <= 0.8 -> "You're budgeting more than you need. Worth trusting " +
                            "yourself with the spare time."
                        else -> "Worth knowing — a reliable estimate is a rare thing."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                calibration.take(4).forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            c.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "est ${c.estimateMin}m · actual ${c.actualMin}m",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // --- Focus vs. Mood, last 7 days ---
        SectionCard {
            Text("Focus & mood, last 7 days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Teal bars are focus minutes. Gold dots trace your logged mood.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Scale readout. The bars are normalised to the week's OWN maximum, which means a
            // 40-minute week and a 400-minute week draw an identical picture — the chart looked
            // quantitative while carrying no magnitude at all. Stating the peak and the total is
            // what turns it back into information.
            val peak = data.series.maxOfOrNull { it.focusMin } ?: 0
            if (peak > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Best day: ${com.addhd.app.ui.components.focusLabel(peak)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Week: ${com.addhd.app.ui.components.focusLabel(data.series.sumOf { it.focusMin })}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            FocusMoodSparkline(
                data.series,
                barColor = MaterialTheme.colorScheme.primary,
                // The mood trace sits on a leather card; the container gold measured 2.41:1
                // against it, which is too faint for a data series you're meant to read.
                moodColor = com.addhd.app.ui.theme.GoldText.onCard,
                baselineColor = MaterialTheme.colorScheme.outline,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A dependency-free 7-day chart: teal focus-minute bars with a gold mood trend line overlaid.
 * Focus bars scale to the week's own max (relative, not absolute — a light week still reads); the
 * mood line maps the fixed 1..5 scale onto the plot height. Days with no logged mood break the line
 * (a gap rather than a fake zero). Rounded caps + a single baseline match the tavern aesthetic.
 */
@Composable
private fun FocusMoodSparkline(
    days: List<AppViewModel.DayPoint>,
    barColor: Color,
    moodColor: Color,
    baselineColor: Color,
    labelColor: Color,
) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = days.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barW = slot * 0.42f
            val plotBottom = size.height
            val plotTop = 8f
            val plotH = plotBottom - plotTop

            val maxFocus = (days.maxOf { it.focusMin }).coerceAtLeast(1)

            // Baseline.
            drawLine(
                color = baselineColor.copy(alpha = 0.5f),
                start = Offset(0f, plotBottom),
                end = Offset(size.width, plotBottom),
                strokeWidth = 2f,
            )

            // Focus bars.
            days.forEachIndexed { i, d ->
                val cx = slot * i + slot / 2f
                val h = plotH * (d.focusMin.toFloat() / maxFocus)
                if (d.focusMin > 0) {
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.85f),
                        topLeft = Offset(cx - barW / 2f, plotBottom - h),
                        size = Size(barW, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2.4f, barW / 2.4f),
                    )
                }
            }

            // Mood trend line (1..5 → plot height), broken across null days.
            fun moodY(m: Int) = plotBottom - plotH * ((m - 1).toFloat() / 4f)
            var prev: Offset? = null
            days.forEachIndexed { i, d ->
                val cx = slot * i + slot / 2f
                val m = d.mood
                if (m != null) {
                    val pt = Offset(cx, moodY(m))
                    prev?.let {
                        drawLine(moodColor, it, pt, strokeWidth = 4f, cap = StrokeCap.Round)
                    }
                    drawCircle(moodColor, radius = 6f, center = pt)
                    // Ring for contrast on the teal bars behind it.
                    drawCircle(moodColor, radius = 6f, center = pt, style = Stroke(width = 2f))
                    prev = pt
                } else {
                    prev = null  // gap — don't connect across a day with no mood
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Day-of-week labels, each with its own minute count underneath. Per-bar values mean the
        // reader never has to infer magnitude from a relatively-scaled bar height.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            days.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.label, style = MaterialTheme.typography.bodySmall, color = labelColor)
                    Text(
                        if (d.focusMin > 0) "${d.focusMin}" else "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.focusMin > 0) barColor else labelColor.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
