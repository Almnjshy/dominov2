package com.agon.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.domain.model.StatsData

@Composable
fun StatsScreen(
    stats: StatsData,
    achievements: List<String>,
    isLoading: Boolean,
    error: String?,
    showClearConfirmation: Boolean,
    exportedJson: String?,
    showExportDialog: Boolean,
    onClearStats: () -> Unit,
    onExport: () -> Unit,
    onShowClearConfirmation: () -> Unit,
    onDismissClearConfirmation: () -> Unit,
    onDismissExportDialog: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Text("الإحصائيات", style = MaterialTheme.typography.headlineSmall)
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("ملخص الإحصائيات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        StatRow("المباريات", "${stats.matchesPlayed}")
                        StatRow("الانتصارات", "${stats.wins}")
                        StatRow("الخسائر", "${stats.losses}")
                        StatRow("نسبة الفوز", "${"%.1f".format(stats.winRate)}%")
                        StatRow("أطول سلسلة انتصارات", "${stats.longestWinStreak}")
                        StatRow("وقت اللعب", "${stats.totalPlayTimeSeconds / 60} دقيقة")
                    }
                }
            }

            if (achievements.isNotEmpty()) {
                item { Text("الإنجازات", style = MaterialTheme.typography.titleMedium) }
                items(achievements) { achievement ->
                    Card { Text("🏆 $achievement", Modifier.padding(12.dp)) }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("تصدير") }
                    OutlinedButton(onClick = onShowClearConfirmation, modifier = Modifier.weight(1f)) { Text("مسح الكل") }
                }
            }

            if (stats.history.isNotEmpty()) {
                item { Text("آخر المباريات", style = MaterialTheme.typography.titleMedium) }
                items(stats.history.takeLast(10).reversed()) { record ->
                    Card {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (record.isWin) "✓ فوز" else "✗ خسارة",
                                color = if (record.isWin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text("${record.durationSeconds}ث")
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissClearConfirmation,
            title = { Text("مسح الإحصائيات") },
            text = { Text("هل تريد مسح جميع الإحصائيات؟ هذا الإجراء لا يمكن التراجع عنه.") },
            confirmButton = { Button(onClick = onClearStats) { Text("مسح") } },
            dismissButton = { TextButton(onClick = onDismissClearConfirmation) { Text("إلغاء") } }
        )
    }

    if (showExportDialog && exportedJson != null) {
        AlertDialog(
            onDismissRequest = onDismissExportDialog,
            title = { Text("تصدير البيانات") },
            text = { Text("تم تجهيز البيانات للتصدير") },
            confirmButton = { TextButton(onClick = onDismissExportDialog) { Text("حسناً") } }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
