package com.agon.app.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.domain.model.*
import com.agon.app.presentation.viewmodel.GameViewModel

@Composable
fun GameScreen(
    gameState: GameState,
    isAiThinking: Boolean,
    showResult: Boolean,
    error: String?,
    onTileClick: (DominoTile, BoardSide?) -> Unit,
    onDrawOrPass: () -> Unit,
    legalSides: (DominoTile) -> Set<BoardSide>,
    onNewGame: () -> Unit,
    onBackToMenu: () -> Unit,
    onDismissResult: () -> Unit,
    onClearError: () -> Unit
) {
    var selectedTile by remember { mutableStateOf<DominoTile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Bar ──────────────────────────────
        TopBar(
            message = gameState.message,
            isAiThinking = isAiThinking,
            onBack = onBackToMenu
        )

        // ── Match Score Banner ───────────────────
        MatchScoreBanner(matchScore = gameState.matchScore, players = gameState.players)

        // ── Players Info ─────────────────────────
        PlayersRow(
            players = gameState.players,
            currentPlayerIndex = gameState.currentPlayerIndex,
            matchScore = gameState.matchScore
        )

        Spacer(Modifier.height(8.dp))

        // ── Board ────────────────────────────────
        BoardArea(
            boardState = gameState.board,
            stockCount = gameState.stockCount,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 8.dp)
        )

        // ── Side Selection ───────────────────────
        AnimatedVisibility(visible = selectedTile != null) {
            selectedTile?.let { tile ->
                SideSelector(
                    tile = tile,
                    onSelectLeft = { onTileClick(tile, BoardSide.LEFT); selectedTile = null },
                    onSelectRight = { onTileClick(tile, BoardSide.RIGHT); selectedTile = null },
                    onCancel = { selectedTile = null }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Error ────────────────────────────────
        if (error != null) {
            ErrorBanner(error = error, onDismiss = onClearError)
        }

        // ── Player Hand ──────────────────────────
        val currentPlayer = gameState.currentPlayer
        if (currentPlayer != null && !currentPlayer.isAi && !gameState.isGameOver) {
            PlayerHandSection(
                player = currentPlayer,
                legalSides = legalSides,
                onTileClick = { tile ->
                    val sides = legalSides(tile)
                    when {
                        sides.isEmpty() -> Unit
                        sides.size == 1 -> onTileClick(tile, sides.first())
                        else -> selectedTile = tile
                    }
                },
                onDrawOrPass = onDrawOrPass,
                canDraw = gameState.canDraw
            )
        } else if (isAiThinking) {
            AiThinkingIndicator()
        }

        Spacer(Modifier.height(8.dp))
    }

    // ── Round Result Dialog ──────────────────────
    if (showResult && gameState.isGameOver && !gameState.isMatchOver) {
        RoundResultDialog(
            gameState = gameState,
            onNextRound = onDismissResult, // caller handles newRound
            onQuit = onBackToMenu
        )
    }

    // ── Match Over Dialog ────────────────────────
    if (gameState.isMatchOver) {
        MatchResultDialog(
            gameState = gameState,
            onRematch = onNewGame,
            onQuit = onBackToMenu
        )
    }
}

// ─────────────────────────────────────────────────────
// Sub-Composables
// ─────────────────────────────────────────────────────

@Composable
private fun TopBar(message: String, isAiThinking: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        if (isAiThinking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
        } else {
            Spacer(Modifier.size(36.dp))
        }
    }
}

@Composable
private fun MatchScoreBanner(matchScore: MatchScore, players: List<Player>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            players.forEach { player ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = player.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${matchScore.playerScore(player.id)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    // Progress bar toward 100
                    LinearProgressIndicator(
                        progress = { matchScore.progressPercent(player.id) },
                        modifier = Modifier.width(60.dp).height(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${matchScore.playerRoundsWon(player.id)} جولات",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (player.id < players.size - 1) {
                    Text("VS", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Text(
            text = "هدف: ${matchScore.targetScore} نقطة • الجولة ${matchScore.currentRound}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PlayersRow(
    players: List<Player>,
    currentPlayerIndex: Int,
    matchScore: MatchScore
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        players.forEach { player ->
            val isCurrent = player.id == currentPlayerIndex
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 1f, targetValue = if (isCurrent) 1.05f else 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "scale"
            )
            Card(
                modifier = Modifier.scale(pulse),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        player.displayName(),
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "${player.hand.size} 🁣",
                        fontSize = 11.sp,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardArea(boardState: BoardState, stockCount: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (boardState.isEmpty) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🁣", fontSize = 32.sp)
                    Text("اللوحة فارغة — ابدأ اللعب",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Board ends display
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EndTile(value = boardState.leftEnd, label = "←")
                        // Middle tiles (last few)
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
                        ) {
                            val displayTiles = boardState.tiles.dropLast(1).takeLast(5)
                            items(displayTiles) { placed ->
                                MiniTile(tile = placed.tile)
                            }
                        }
                        EndTile(value = boardState.rightEnd, label = "→")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "المخزون: $stockCount قطعة | ${boardState.tiles.size} على اللوحة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EndTile(value: Int?, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("${value ?: "-"}", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MiniTile(tile: DominoTile) {
    Box(
        Modifier
            .size(width = 22.dp, height = 36.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${tile.top}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(thickness = 0.5.dp)
            Text("${tile.bottom}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SideSelector(
    tile: DominoTile,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("اختر الجانب للقطعة [${tile.top}|${tile.bottom}]",
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSelectLeft) { Text("← يسار") }
                OutlinedButton(onClick = onCancel) { Text("إلغاء") }
                Button(onClick = onSelectRight) { Text("يمين →") }
            }
        }
    }
}

@Composable
private fun PlayerHandSection(
    player: Player,
    legalSides: (DominoTile) -> Set<BoardSide>,
    onTileClick: (DominoTile) -> Unit,
    onDrawOrPass: () -> Unit,
    canDraw: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("يدك (${player.hand.size} قطع):", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("قيمة اليد: ${player.handValue}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(player.hand) { tile ->
                val sides = legalSides(tile)
                HandTile(
                    tile = tile,
                    isLegal = sides.isNotEmpty(),
                    onClick = if (sides.isNotEmpty()) { { onTileClick(tile) } } else null
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDrawOrPass,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canDraw) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outline
            )
        ) {
            Text(if (canDraw) "سحب قطعة من المخزون" else "تخطي الدور")
        }
    }
}

@Composable
private fun HandTile(tile: DominoTile, isLegal: Boolean, onClick: (() -> Unit)?) {
    val scale by animateFloatAsState(
        targetValue = if (isLegal) 1f else 0.9f,
        animationSpec = tween(200), label = "tile_scale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(width = 48.dp, height = 80.dp)
            .background(
                color = if (isLegal) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isLegal) 2.dp else 1.dp,
                color = if (isLegal) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${tile.top}", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = if (isLegal) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(modifier = Modifier.width(32.dp), thickness = 1.dp)
            Text("${tile.bottom}", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = if (isLegal) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiThinkingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text("AI يفكر في أفضل حركة...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
            TextButton(onClick = onDismiss) { Text("✕") }
        }
    }
}

@Composable
private fun RoundResultDialog(
    gameState: GameState,
    onNextRound: () -> Unit,
    onQuit: () -> Unit
) {
    val lastRound = gameState.matchScore.roundHistory.lastOrNull()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("انتهت الجولة ${lastRound?.roundNumber ?: ""}!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(gameState.message, fontWeight = FontWeight.Bold)
                if (lastRound != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("النقاط المكتسبة: +${lastRound.pointsEarned}", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text("النتيجة التراكمية:", fontWeight = FontWeight.Bold)
                gameState.players.forEach { player ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(player.displayName())
                        Text("${gameState.matchScore.playerScore(player.id)} نقطة",
                            fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { gameState.matchScore.progressPercent(player.id) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onNextRound) { Text("جولة تالية ▶") } },
        dismissButton = { TextButton(onClick = onQuit) { Text("الخروج") } }
    )
}

@Composable
private fun MatchResultDialog(
    gameState: GameState,
    onRematch: () -> Unit,
    onQuit: () -> Unit
) {
    val winnerId = gameState.matchScore.matchWinnerId
    val winner = winnerId?.let { gameState.players.getOrNull(it) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🏆 انتهت المباراة!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${winner?.displayName() ?: "تعادل"} فاز بالمباراة!",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text("النتيجة النهائية:", fontWeight = FontWeight.Bold)
                gameState.matchScore.leaderboard.forEachIndexed { i, (playerId, score) ->
                    val player = gameState.players.getOrNull(playerId)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${i + 1}. ${player?.displayName() ?: "لاعب $playerId"}")
                        Text("$score نقطة | ${gameState.matchScore.playerRoundsWon(playerId)} جولات",
                            fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("عدد الجولات: ${gameState.matchScore.currentRound - 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onRematch) { Text("مباراة جديدة 🔄") } },
        dismissButton = { TextButton(onClick = onQuit) { Text("القائمة الرئيسية") } }
    )
}
