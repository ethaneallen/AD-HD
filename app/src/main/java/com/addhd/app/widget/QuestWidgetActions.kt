package com.addhd.app.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.addhd.app.data.AppDatabase
import com.addhd.app.vm.RewardEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Glance ActionCallback fired when the hero taps a quest's check-circle ON THE HOME-SCREEN WIDGET.
 *
 * Runs with NO ViewModel and NO Activity in scope, so it goes straight to the shared, process-wide
 * [RewardEngine] — the SAME instance the app uses — and drives the identical completion cascade
 * (task-state, combat + loot, XP/gold, combo, campaign/boss credit, streak). Because the engine's
 * mutexes and combo counter are singletons, a widget check-off and an in-app tap can't race or
 * double-count. After the reward loop resolves we call [QuestWidget.updateAll] so the widget repaints
 * with the task gone, the streak/gold bumped, and (if it was the last of the Rule-of-Three) an empty
 * state — no manual diffing.
 */
class ToggleQuestAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[taskIdKey] ?: return   // malformed click — nothing to do

        // Look up the freshest row: the widget's snapshot may be a few seconds stale, and we must
        // never award against an already-done task (double-credit) or a deleted one.
        val db = AppDatabase.get(context)
        val task = withContext(Dispatchers.IO) {
            db.taskDao().all().first().firstOrNull { it.id == taskId }
        } ?: run { QuestWidget().updateAll(context); return }

        if (task.done) { QuestWidget().updateAll(context); return }   // already cleared — just repaint

        // Drive the FULL shared reward pipeline. The widget passes a no-op alarm hook — a home-screen
        // check-off of a time-blocked quest still cancels its reminder via the engine's own alarm
        // sync in the app path, but here we defer that to the next app open to keep the widget's
        // background work minimal and dependency-free. (Completion itself is fully credited.)
        RewardEngine.get(context).completeTask(task)

        // Repaint every placed instance so the checked quest drops off the Rule-of-Three list and the
        // streak/gold header reflects the award.
        QuestWidget().updateAll(context)
    }

    companion object {
        /** ActionParameters key carrying which quest row this check-circle completes. */
        val taskIdKey = ActionParameters.Key<Long>("questTaskId")

        /** Preferences key mirror (Glance state) if a caller needs to stash the last-toggled id. */
        val lastToggledKey = longPreferencesKey("last_toggled_task_id")
    }
}
