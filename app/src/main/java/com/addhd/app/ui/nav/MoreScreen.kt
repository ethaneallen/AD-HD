package com.addhd.app.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun MoreScreen(nav: NavController) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Entry("📊", "Your Patterns", "Insights from your quests, focus & mood") { nav.navigate("insights") }
        Entry("🧙", "Character sheet", "Stats, gear & inventory") { nav.navigate("character") }
        Entry("🛒", "Adventurer's Shop", "Spend gold on potions & artifacts") { nav.navigate("shop") }
        Entry("🏰", "Hero's Tavern", "Your stronghold — build & harvest") { nav.navigate("stronghold") }
        Entry("📜", "Habits", "Build streaks & train your hero") { nav.navigate("habits") }
        Entry("📓", "The Hero's Chronicle", "Purge the fog of war — campaign notes & free writing") { nav.navigate("journal") }
        Entry("🎲", "Tavern Games", "Test your fate at the gaming tables.") { nav.navigate("fidget") }
        Entry("🕯️", "The Sanctuary", "Retreat, wait out an urge, or invoke a blessing") { nav.navigate("sos") }
        Entry("⚙️", "Settings", "Privacy & reset all data") { nav.navigate("settings") }
    }
}

@Composable
private fun Entry(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(18.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
