package com.agon.app.domain.engine.game

import com.agon.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-function game engine — NO internal mutable state.
 * Every function takes a GameState and returns a new GameState.
 * This makes it fully testable, thread-safe, and network-ready.
 *
 * Pattern: Redux-style reducer
 *   fun reduce(state: GameState, action: GameAction): Result<GameState>
 */
@Singleton
class DominoGameEngine @Inject constructor(
    private val clock: GameClock
) {

    // ─────────────────────────────────────────────
    // Public API — all pure functions
    // ─────────────────────────────────────────────

    fun newGame(mode: GameMode): GameState {
        val initialMatchScore = MatchScore(
            scores = (0 until mode.playerCount).associate { it to 0 },
            roundsWon = (0 until mode.playerCount).associate { it to 0 },
            targetScore = 100
        )
        return startRound(mode, initialMatchScore, clock.now())
    }

    fun newRound(mode: GameMode, existingMatchScore: MatchScore): GameState =
        startRound(mode, existingMatchScore, clock.now())

    fun reduce(state: GameState, action: GameAction): Result<GameState> = when (action) {
        is GameAction.PlayTile -> handlePlayTile(state, action)
        is GameAction.DrawTile -> handleDraw(state)
        is GameAction.PassTurn -> handlePass(state)
        is GameAction.WinRound -> Result.success(state) // terminal — no further reduce
    }

    fun getLegalSides(state: GameState, tile: DominoTile): Set<BoardSide> =
        state.board.getLegalSides(tile)

    fun hasLegalMoves(state: GameState): Boolean =
        state.currentPlayer?.hand?.any { state.board.getLegalSides(it).isNotEmpty() } == true

    fun shouldDrawOrPass(state: GameState): Boolean = !hasLegalMoves(state)

    fun getGameResult(state: GameState): GameResult? {
        if (!state.isGameOver) return null
        val duration = (clock.now() - state.roundStartTime) / 1000
        val winner = state.winnerId
        return GameResult(
            winnerId = winner ?: -1,
            winnerName = winner?.let { state.players.getOrNull(it)?.displayName() } ?: "تعادل",
            scores = state.players.associate { it.id to it.handValue },
            durationSeconds = duration,
            gameMode = state.gameMode
        )
    }

    // ─────────────────────────────────────────────
    // Private reducers
    // ─────────────────────────────────────────────

    private fun handlePlayTile(state: GameState, action: GameAction.PlayTile): Result<GameState> {
        val validation = validatePlayTile(state, action.tile, action.side)
        if (validation.isFailure) return Result.failure(validation.exceptionOrNull()!!)

        val player = state.currentPlayer!!
        val newBoard = state.board.place(action.tile, action.side)
        val updatedPlayer = player.withoutTile(action.tile)
        val updatedPlayers = state.players.map { if (it.id == player.id) updatedPlayer else it }

        var newState = state.copy(
            players = updatedPlayers,
            board = newBoard,
            lastAction = action
        )

        newState = checkRoundEnd(newState, updatedPlayer)

        if (!newState.roundOver && !newState.isBlocked) {
            val nextIdx = (state.currentPlayerIndex + 1) % state.players.size
            newState = newState.copy(
                currentPlayerIndex = nextIdx,
                turnCount = newState.turnCount + 1,
                message = "دور ${updatedPlayers.getOrNull(nextIdx)?.displayName()}"
            )
        }

        return Result.success(newState)
    }

    private fun handleDraw(state: GameState): Result<GameState> {
        val player = state.currentPlayer
            ?: return Result.failure(IllegalStateException("لا يوجد لاعب حالي"))

        if (state.stock.isEmpty()) return handlePass(state)

        val drawnTile = state.stock.first()
        val newStock = state.stock.drop(1)
        val updatedPlayer = player.withTile(drawnTile)
        val updatedPlayers = state.players.map { if (it.id == player.id) updatedPlayer else it }

        val canPlayDrawn = state.board.getLegalSides(drawnTile).isNotEmpty()
        val nextIdx = if (!canPlayDrawn) (state.currentPlayerIndex + 1) % state.players.size
        else state.currentPlayerIndex

        return Result.success(
            state.copy(
                players = updatedPlayers,
                stock = newStock,
                currentPlayerIndex = nextIdx,
                turnCount = if (!canPlayDrawn) state.turnCount + 1 else state.turnCount,
                lastAction = GameAction.DrawTile(player.id, drawnTile),
                message = if (canPlayDrawn)
                    "${player.displayName()} سحب قطعة ويمكن لعبها"
                else
                    "${player.displayName()} سحب قطعة ومرر"
            )
        )
    }

    private fun handlePass(state: GameState): Result<GameState> {
        val player = state.currentPlayer
            ?: return Result.failure(IllegalStateException("لا يوجد لاعب حالي"))
        val nextIdx = (state.currentPlayerIndex + 1) % state.players.size
        return Result.success(
            state.copy(
                currentPlayerIndex = nextIdx,
                turnCount = state.turnCount + 1,
                lastAction = GameAction.PassTurn(player.id),
                message = "${player.displayName()} تخطى دوره"
            )
        )
    }

    private fun validatePlayTile(state: GameState, tile: DominoTile, side: BoardSide): Result<Unit> =
        when {
            state.roundOver -> Result.failure(IllegalStateException("الجولة انتهت"))
            state.isBlocked -> Result.failure(IllegalStateException("اللعبة موقوفة"))
            state.currentPlayer == null -> Result.failure(IllegalStateException("لا يوجد لاعب حالي"))
            state.currentPlayer.hand.none { it.id == tile.id } ->
                Result.failure(IllegalArgumentException("اللاعب لا يملك هذه القطعة"))
            side !in state.board.getLegalSides(tile) ->
                Result.failure(IllegalArgumentException("حركة غير قانونية: ${tile} في جانب $side"))
            else -> Result.success(Unit)
        }

    private fun checkRoundEnd(state: GameState, lastPlayer: Player): GameState {
        // Case 1: player emptied hand → wins round
        if (lastPlayer.hand.isEmpty()) {
            val loserValues = state.players
                .filter { it.id != lastPlayer.id }
                .associate { it.id to it.handValue }
            val pointsEarned = loserValues.values.sum()
            val roundResult = RoundResult(
                roundNumber = state.matchScore.currentRound,
                winnerId = lastPlayer.id,
                loserHandValues = loserValues,
                pointsEarned = pointsEarned,
                durationSeconds = (clock.now() - state.roundStartTime) / 1000
            )
            val updated = state.applyRoundResult(roundResult)
            return updated.copy(
                roundOver = true,
                winnerId = lastPlayer.id,
                message = buildString {
                    append("${lastPlayer.displayName()} فاز بالجولة! (+$pointsEarned نقطة)")
                    if (updated.isMatchOver) append("\n🏆 ${lastPlayer.displayName()} فاز بالمباراة!")
                },
                lastAction = GameAction.WinRound(lastPlayer.id, state.players.associate { it.id to it.handValue })
            )
        }

        // Case 2: game blocked (no one can play and stock empty)
        if (isBlocked(state)) {
            val minValue = state.players.minOf { it.handValue }
            val winnerId = state.players.filter { it.handValue == minValue }
                .takeIf { it.size == 1 }?.first()?.id
            val loserValues = state.players.associate { it.id to it.handValue }
            val pointsEarned = if (winnerId != null)
                loserValues.filter { it.key != winnerId }.values.sum() else 0
            val roundResult = RoundResult(
                roundNumber = state.matchScore.currentRound,
                winnerId = winnerId,
                loserHandValues = loserValues,
                pointsEarned = pointsEarned,
                wasBlocked = true,
                durationSeconds = (clock.now() - state.roundStartTime) / 1000
            )
            val updated = state.applyRoundResult(roundResult)
            return updated.copy(
                roundOver = true,
                isBlocked = true,
                winnerId = winnerId,
                message = "اللعبة موقوفة! ${winnerId?.let { state.players.getOrNull(it)?.displayName() } ?: "تعادل"} (+$pointsEarned نقطة)",
                lastAction = GameAction.WinRound(winnerId ?: -1, loserValues)
            )
        }

        return state
    }

    private fun isBlocked(state: GameState): Boolean =
        state.stock.isEmpty() &&
        state.players.all { player ->
            player.hand.all { state.board.getLegalSides(it).isEmpty() }
        }

    private fun startRound(mode: GameMode, matchScore: MatchScore, now: Long): GameState {
        val playerCount = mode.playerCount
        val tilesPerPlayer = 28 / playerCount

        // Fix #3: always shuffle
        val deck = DominoTile.createDeck().shuffled()

        val players = List(playerCount) { index ->
            val isAi = index >= (playerCount - mode.aiCount)
            Player(
                id = index,
                name = if (isAi) "Bot ${index + 1}" else "اللاعب ${index + 1}",
                isAi = isAi,
                hand = deck.drop(index * tilesPerPlayer).take(tilesPerPlayer)
            )
        }
        val stock = deck.drop(playerCount * tilesPerPlayer)
        val startingPlayer = findStartingPlayer(players)

        return GameState(
            players = players,
            board = BoardState(),
            stock = stock,
            currentPlayerIndex = startingPlayer,
            gameMode = mode,
            matchScore = matchScore,
            roundStartTime = now,
            message = "${players[startingPlayer].displayName()} يبدأ الجولة ${matchScore.currentRound}"
        )
    }

    private fun findStartingPlayer(players: List<Player>): Int {
        var best = 0; var bestVal = -1
        players.forEachIndexed { i, p ->
            val v = p.hand.filter { it.isDouble }.maxByOrNull { it.total }?.total
                ?: p.hand.maxOfOrNull { it.total } ?: 0
            if (v > bestVal) { bestVal = v; best = i }
        }
        return best
    }
}
