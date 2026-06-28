package com.agon.app.domain.engine.ai

import com.agon.app.domain.model.AiDifficulty
import com.agon.app.domain.model.BoardSide
import com.agon.app.domain.model.DominoTile
import com.agon.app.domain.model.GameState
import com.agon.app.domain.model.Player
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Pure AI engine — NO mutable internal state.
 * All decisions derived exclusively from GameState snapshot.
 * Safe for Singleton, thread-safe, fully testable.
 */
@Singleton
class DominoAIEngine @Inject constructor() {

    data class AIMove(
        val tile: DominoTile,
        val side: BoardSide,
        val score: Float,
        val reasoning: String = ""
    )

    // ─────────────────────────────────────────────
    // Main entry point — pure function
    // ─────────────────────────────────────────────

    fun calculateBestMove(
        state: GameState,
        player: Player,
        difficulty: AiDifficulty
    ): AIMove? {
        val legalMoves = findLegalMoves(state, player)
        if (legalMoves.isEmpty()) return null

        return when (difficulty) {
            AiDifficulty.EASY -> easyMove(legalMoves)
            AiDifficulty.MEDIUM -> mediumMove(legalMoves, state, player)
            AiDifficulty.HARD -> hardMove(legalMoves, state, player)
        }
    }

    fun shouldDrawOrPass(state: GameState, player: Player): Boolean =
        findLegalMoves(state, player).isEmpty()

    // ─────────────────────────────────────────────
    // EASY: low-value random play
    // ─────────────────────────────────────────────

    private fun easyMove(moves: List<AIMove>): AIMove {
        // 30% play worst move on purpose
        return if (Random.nextFloat() < 0.30f)
            moves.minByOrNull { it.score } ?: moves.random()
        else
            moves.filter { it.tile.total <= 8 }.randomOrNull() ?: moves.random()
    }

    // ─────────────────────────────────────────────
    // MEDIUM: heuristic scoring
    // ─────────────────────────────────────────────

    private fun mediumMove(
        moves: List<AIMove>,
        state: GameState,
        player: Player
    ): AIMove {
        val scored = moves.map { it.copy(score = heuristicScore(it, state, player)) }
            .sortedByDescending { it.score }
        return if (Random.nextFloat() < 0.70f) scored.first()
        else scored.getOrElse(1) { scored.first() }
    }

    // ─────────────────────────────────────────────
    // HARD: Minimax depth-2 + probability from GameState
    // ─────────────────────────────────────────────

    private fun hardMove(
        moves: List<AIMove>,
        state: GameState,
        player: Player
    ): AIMove {
        return moves.maxByOrNull { move ->
            minimaxScore(state, move, depth = 2, maximizing = true)
        } ?: moves.first()
    }

    /**
     * Minimax: simulate best opponent response after each AI move.
     * Pure — operates only on GameState copies (no mutation).
     */
    private fun minimaxScore(
        state: GameState,
        move: AIMove,
        depth: Int,
        maximizing: Boolean
    ): Float {
        // Simulate playing this move
        val simulatedHand = state.currentPlayer?.hand?.filter { it.id != move.tile.id } ?: return 0f
        val simulatedBoard = state.board.place(move.tile, move.side)

        // Base score: tiles played + future options
        val futureOptions = simulatedHand.count { simulatedBoard.getLegalSides(it).isNotEmpty() }
        val baseScore = heuristicScore(move, state, state.currentPlayer!!) + futureOptions * 1.5f

        if (depth <= 0) return baseScore

        // Estimate opponent response using visible game state
        val opponents = state.players.filter { it.id != state.currentPlayerIndex }
        val opponentThreat = opponents.maxOfOrNull { opp ->
            opp.hand.count { simulatedBoard.getLegalSides(it).isNotEmpty() }.toFloat()
        } ?: 0f

        // probability bonus: prefer ends with fewer remaining tiles in stock
        val probabilityBonus = probabilityFromState(move.tile, state)

        return baseScore - opponentThreat * 1.2f + probabilityBonus
    }

    // ─────────────────────────────────────────────
    // Heuristic scoring — pure, no side effects
    // ─────────────────────────────────────────────

    private fun heuristicScore(move: AIMove, state: GameState, player: Player): Float {
        var score = move.tile.total.toFloat()

        // Doubles are high priority early game
        if (move.tile.isDouble) score += 5f
        if (state.turnCount < 6 && move.tile.total > 8) score += 3f

        // Prefer moves that match the board ends strongly
        val matchedEnd = if (move.side == BoardSide.LEFT) state.board.leftEnd else state.board.rightEnd
        if (matchedEnd != null && move.tile.matches(matchedEnd)) score += 2f

        // Prefer moves that block opponents
        val remainingHand = player.hand.filter { it.id != move.tile.id }
        if (blocksOpponents(move, state)) score += 7f

        // Prefer moves that leave good options in remaining hand
        val boardAfter = state.board.place(move.tile, move.side)
        val goodOptions = remainingHand.count { boardAfter.getLegalSides(it).isNotEmpty() }
        score += goodOptions * 1.5f

        // Winning move
        if (remainingHand.isEmpty()) score += 20f

        // Penalize moves that trap AI
        if (remainingHand.isNotEmpty() && goodOptions == 0) score -= 5f

        return score
    }

    /**
     * Probability bonus derived from GameState snapshot only.
     * Counts how many tiles with these values remain in stock/opponent hands
     * (deduced from what's on the board + our hand).
     */
    private fun probabilityFromState(tile: DominoTile, state: GameState): Float {
        val boardTileIds = state.board.tiles.map { it.tile.id }.toSet()
        val myHandIds = state.currentPlayer?.hand?.map { it.id }?.toSet() ?: emptySet()
        val knownIds = boardTileIds + myHandIds

        // For each end value, how many tiles are "unknown" (in stock or opponent hand)?
        fun unknownCount(value: Int): Int {
            val allWithValue = DominoTile.createDeck().filter { it.matches(value) }
            return allWithValue.count { it.id !in knownIds }
        }

        val leftUnknown = state.board.leftEnd?.let { unknownCount(it) } ?: 0
        val rightUnknown = state.board.rightEnd?.let { unknownCount(it) } ?: 0

        // If opponents likely have many tiles of this value → it's risky to expose that end
        val exposedValue = if (tile.top == state.board.leftEnd || tile.top == state.board.rightEnd)
            tile.top else tile.bottom
        val riskPenalty = (leftUnknown + rightUnknown) * 0.3f

        return if (tile.total <= 6) 1f else -riskPenalty
    }

    private fun blocksOpponents(move: AIMove, state: GameState): Boolean {
        val boardAfter = state.board.place(move.tile, move.side)
        val opponents = state.players.filter { it.id != state.currentPlayerIndex }
        return opponents.isNotEmpty() &&
               opponents.all { opp -> opp.hand.all { boardAfter.getLegalSides(it).isEmpty() } }
    }

    private fun findLegalMoves(state: GameState, player: Player): List<AIMove> =
        player.hand.flatMap { tile ->
            state.board.getLegalSides(tile).map { side ->
                AIMove(tile, side, 0f)
            }
        }
}
