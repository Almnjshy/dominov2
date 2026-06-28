package com.agon.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.domain.model.*
import com.agon.app.domain.usecase.ai.AIPlayUseCase
import com.agon.app.domain.usecase.game.*
import com.agon.app.domain.usecase.settings.LoadSettingsUseCase
import com.agon.app.domain.usecase.stats.RecordGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameViewModel @Inject constructor(
    private val newGameUseCase: NewGameUseCase,
    private val playTileUseCase: PlayTileUseCase,
    private val drawOrPassUseCase: DrawOrPassUseCase,
    private val getLegalMovesUseCase: GetLegalMovesUseCase,
    private val checkGameOverUseCase: CheckGameOverUseCase,
    private val aiPlayUseCase: AIPlayUseCase,
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val recordGameUseCase: RecordGameUseCase
) : ViewModel() {

    data class GameUiState(
        val gameState: GameState = GameState(),
        val isLoading: Boolean = false,
        val isAiThinking: Boolean = false,
        val error: String? = null,
        val showRoundResult: Boolean = false,   // show between rounds
        val showMatchResult: Boolean = false,   // show at end of match
        val aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM,
        val selectedTile: DominoTile? = null
    )

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val aiTurnQueue = Channel<Unit>(Channel.CONFLATED)
    private var currentGameMode: GameMode = GameMode.HUMAN_VS_AI

    init {
        viewModelScope.launch {
            aiTurnQueue.receiveAsFlow().collect { processAiTurn() }
        }
        viewModelScope.launch {
            try {
                val settings = loadSettingsUseCase()
                _uiState.value = _uiState.value.copy(aiDifficulty = settings.aiDifficulty)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun newGame(mode: GameMode) {
        currentGameMode = mode
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, showRoundResult = false,
                showMatchResult = false, error = null, selectedTile = null
            )
            newGameUseCase(mode)
                .onSuccess { state ->
                    _uiState.value = _uiState.value.copy(gameState = state, isLoading = false)
                    queueAiTurnIfNeeded()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    /** Start a new round (keeping match score) */
    fun newRound() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, showRoundResult = false,
                error = null, selectedTile = null
            )
            // Use newGame but preserve match score via the engine
            newGameUseCase.newRound(currentGameMode)
                .onSuccess { state ->
                    _uiState.value = _uiState.value.copy(gameState = state, isLoading = false)
                    queueAiTurnIfNeeded()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun selectTile(tile: DominoTile) {
        val sides = getLegalMovesUseCase(tile)
        if (sides.isEmpty()) return
        if (sides.size == 1) {
            playTile(tile, sides.first())
        } else {
            _uiState.value = _uiState.value.copy(selectedTile = tile)
        }
    }

    fun playTile(tile: DominoTile, side: BoardSide?) {
        val state = _uiState.value.gameState
        if (state.isGameOver) return
        val currentPlayer = state.currentPlayer ?: return
        if (currentPlayer.isAi) return

        viewModelScope.launch {
            val selectedSide = side ?: getLegalMovesUseCase(tile).firstOrNull() ?: return@launch
            playTileUseCase(tile, selectedSide)
                .onSuccess { newState ->
                    _uiState.value = _uiState.value.copy(
                        gameState = newState, error = null, selectedTile = null
                    )
                    handleRoundEnd(newState)
                    if (!newState.isGameOver) queueAiTurnIfNeeded()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun drawOrPass() {
        val state = _uiState.value.gameState
        if (state.isGameOver) return
        val currentPlayer = state.currentPlayer ?: return
        if (currentPlayer.isAi) return

        viewModelScope.launch {
            drawOrPassUseCase()
                .onSuccess { newState ->
                    _uiState.value = _uiState.value.copy(gameState = newState, error = null)
                    handleRoundEnd(newState)
                    if (!newState.isGameOver) queueAiTurnIfNeeded()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun dismissRoundResult() {
        _uiState.value = _uiState.value.copy(showRoundResult = false)
    }

    fun dismissMatchResult() {
        _uiState.value = _uiState.value.copy(showMatchResult = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSelectedTile() {
        _uiState.value = _uiState.value.copy(selectedTile = null)
    }

    fun getLegalSides(tile: DominoTile): Set<BoardSide> = getLegalMovesUseCase(tile)

    private fun queueAiTurnIfNeeded() {
        val state = _uiState.value.gameState
        val player = state.currentPlayer ?: return
        if (player.isAi && !state.isGameOver) aiTurnQueue.trySend(Unit)
    }

    private suspend fun processAiTurn() {
        val state = _uiState.value.gameState
        val player = state.currentPlayer ?: return
        if (!player.isAi || state.isGameOver) return

        _uiState.value = _uiState.value.copy(isAiThinking = true)
        val difficulty = _uiState.value.aiDifficulty

        if (aiPlayUseCase.shouldDrawOrPass(state, player)) {
            drawOrPassUseCase().onSuccess { newState ->
                _uiState.value = _uiState.value.copy(gameState = newState, isAiThinking = false)
                handleRoundEnd(newState)
                if (!newState.isGameOver) queueAiTurnIfNeeded()
            }.onFailure {
                _uiState.value = _uiState.value.copy(isAiThinking = false)
            }
            return
        }

        // AI picks best move using improved engine
        val aiMove = aiPlayUseCase(state, player, difficulty)
        if (aiMove != null) {
            playTileUseCase(aiMove.tile, aiMove.side)
                .onSuccess { newState ->
                    _uiState.value = _uiState.value.copy(gameState = newState, isAiThinking = false)
                    handleRoundEnd(newState)
                    if (!newState.isGameOver) queueAiTurnIfNeeded()
                }
                .onFailure {
                    drawOrPassUseCase().onSuccess { newState ->
                        _uiState.value = _uiState.value.copy(gameState = newState, isAiThinking = false)
                        handleRoundEnd(newState)
                        if (!newState.isGameOver) queueAiTurnIfNeeded()
                    }
                }
        } else {
            drawOrPassUseCase().onSuccess { newState ->
                _uiState.value = _uiState.value.copy(gameState = newState, isAiThinking = false)
                handleRoundEnd(newState)
                if (!newState.isGameOver) queueAiTurnIfNeeded()
            }.onFailure {
                _uiState.value = _uiState.value.copy(isAiThinking = false)
            }
        }
    }

    private fun handleRoundEnd(state: GameState) {
        if (!state.isGameOver) return

        if (state.isMatchOver) {
            _uiState.value = _uiState.value.copy(showMatchResult = true)
            saveMatchResult(state)
        } else {
            _uiState.value = _uiState.value.copy(showRoundResult = true)
        }
    }

    private fun saveMatchResult(state: GameState) {
        viewModelScope.launch {
            val matchWinnerId = state.matchScore.matchWinnerId ?: return@launch
            val result = GameResult(
                winnerId = matchWinnerId,
                winnerName = state.players.getOrNull(matchWinnerId)?.displayName() ?: "فائز",
                scores = state.matchScore.scores,
                durationSeconds = (System.currentTimeMillis() - (state.roundStartTime)) / 1000,
                gameMode = state.gameMode
            )
            recordGameUseCase(result, 0)
        }
    }
}
