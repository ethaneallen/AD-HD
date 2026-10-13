package com.addhd.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addhd.app.campaign.ShopCatalog
import com.addhd.app.vm.AppViewModel.PurchaseResult

/** A purchase result paired with whether it's a positive (success) or negative (blocked) tone. */
data class PurchaseToastState(val message: String, val positive: Boolean)

/** Map a raw [PurchaseResult] to display text + tone. Item name resolved via the code catalog. */
fun PurchaseResult.toToast(): PurchaseToastState {
    val name = ShopCatalog.item(itemIdOf())?.name ?: "Item"
    return when (this) {
        is PurchaseResult.Success -> PurchaseToastState("🪙 Bought $name  ·  ${goldLeft} gold left", true)
        is PurchaseResult.NotEnoughGold -> PurchaseToastState("Not enough gold — need ${need - have} more for $name", false)
        is PurchaseResult.MaxStackReached -> PurchaseToastState("You already have the most $name you can carry", false)
        is PurchaseResult.Unknown -> PurchaseToastState("That item isn't available", false)
    }
}

private fun PurchaseResult.itemIdOf(): String = when (this) {
    is PurchaseResult.Success -> itemId
    is PurchaseResult.NotEnoughGold -> itemId
    is PurchaseResult.MaxStackReached -> itemId
    is PurchaseResult.Unknown -> itemId
}

/**
 * Transient purchase feedback banner. Slides up from the bottom, auto-dismissed by the caller
 * (AppNav holds the timed state, mirroring CombatOverlay/WorldTierOverlay). Success = teal,
 * blocked = error/leather so the tone reads at a glance without needing to parse the text.
 */
@Composable
fun PurchaseToast(state: PurchaseToastState?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = state != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val s = state ?: return@AnimatedVisibility
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 90.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (s.positive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    s.message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (s.positive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}
