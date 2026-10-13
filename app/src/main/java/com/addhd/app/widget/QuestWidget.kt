package com.addhd.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.addhd.app.MainActivity
import com.addhd.app.data.AppDatabase
import com.addhd.app.data.TaskEntity
import kotlinx.coroutines.flow.first

/**
 * The home-screen "Quest Board" — a Glance widget mirroring the app's Rule-of-Three: the three
 * easiest open quests, each with a one-tap check-off that drives the SHARED [com.addhd.app.vm.RewardEngine]
 * (see [ToggleQuestAction]). Header shows the live streak (🔥) and gold (💰); a Quick-Capture button
 * deep-links into the app's voice capture. Dark-tavern palette to match the in-app theme.
 *
 * Data is read straight from Room at compose time (Glance recomposes on updateAll), so no ViewModel
 * or lifecycle is involved — the widget is a thin projection over the same tables the app owns.
 */
class QuestWidget : GlanceAppWidget() {

    // Resize freely: a single flexible layout that fills whatever cell the launcher gives us.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Snapshot the data the widget needs BEFORE provideContent so the composition is synchronous.
        val db = AppDatabase.get(context)
        val quests = db.taskDao().all().first()
            .filter { !it.done && it.parentId == null }
            .sortedBy { it.energy * 100 + it.estimateMin }   // same Rule-of-Three order as the app
            .take(3)
        val pet = db.petDao().get()
        val streak = pet?.streakDays ?: 0
        val gold = pet?.gold ?: 0

        provideContent { QuestBoard(context, streak, gold, quests) }
    }

    @Composable
    private fun QuestBoard(context: Context, streak: Int, gold: Int, quests: List<TaskEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Bg)
                .cornerRadius(20.dp)
                .padding(14.dp),
        ) {
            Header(context, streak, gold)
            Spacer(GlanceModifier.height(10.dp))
            if (quests.isEmpty()) {
                EmptyState()
            } else {
                quests.forEach { q ->
                    QuestRow(q)
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun Header(context: Context, streak: Int, gold: Int) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "🔥 $streak",
                style = TextStyle(color = ColorProvider(Gold), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(12.dp))
            Text(
                "💰 $gold",
                style = TextStyle(color = ColorProvider(Gold), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            // Quick-Capture: launch the app straight into the voice quick-capture overlay.
            Box(
                modifier = GlanceModifier
                    .background(TealDeep)
                    .cornerRadius(14.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(actionStartActivity(quickCaptureIntent(context))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "🎙️ Capture",
                    style = TextStyle(color = ColorProvider(Teal), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }

    @Composable
    private fun QuestRow(task: TaskEntity) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Surface)
                .cornerRadius(14.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(
                    actionRunCallback<ToggleQuestAction>(
                        actionParametersOf(ToggleQuestAction.taskIdKey to task.id),
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Check-off target: a hollow ring, the whole row is tappable so the hit area is generous.
            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .background(BgLift)
                    .cornerRadius(11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("○", style = TextStyle(color = ColorProvider(Teal), fontSize = 16.sp))
            }
            Spacer(GlanceModifier.width(10.dp))
            Text(
                task.title,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Cream), fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
    }

    @Composable
    private fun EmptyState() {
        Box(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "All quests cleared. ⚔️",
                style = TextStyle(color = ColorProvider(Cream), fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
    }

    companion object {
        // Dark-tavern palette (mirrors AddhdTheme's DarkColors — Glance can't read the M3 scheme).
        private val Bg = Color(0xFF241A10)
        private val BgLift = Color(0xFF2E2114)
        private val Surface = Color(0xFF3A2717)
        private val Teal = Color(0xFF26D9BE)
        private val TealDeep = Color(0xFF0E3B34)
        private val Gold = Color(0xFFD9AF4A)
        private val Cream = Color(0xFFF3E7CC)

        /** Intent that opens the app and flags AppNav to raise the voice quick-capture dialog. */
        fun quickCaptureIntent(context: Context?): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_QUICK_CAPTURE, true)
            }
    }
}
