package com.agon.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.domain.model.GameMode

@Composable
fun MainMenuScreen(
    selectedMode: GameMode,
    isLoading: Boolean,
    error: String?,
    onModeSelected: (GameMode) -> Unit,
    onNewGame: () -> Unit,
    onNetwork: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onVerify: () -> Unit,
    onDemo: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🁣 دومينو", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Text("اختر وضع اللعب:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.displayName()) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
            Text("لعبة جديدة", fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onNetwork, modifier = Modifier.fillMaxWidth()) { Text("شبكة محلية") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStats, modifier = Modifier.fillMaxWidth()) { Text("الإحصائيات") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("الإعدادات") }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onClearError) { Text("✕") }
                }
            }
        }
        if (isLoading) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator() }
    }
}

private fun GameMode.displayName() = when (this) {
    GameMode.HUMAN_VS_AI -> "ضد AI"
    GameMode.HUMAN_VS_HUMAN -> "ضد لاعب"
    GameMode.FOUR_HUMANS -> "4 لاعبين"
}
