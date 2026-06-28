package com.agon.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.domain.model.*
import com.agon.app.domain.usecase.network.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val createRoomUseCase: CreateRoomUseCase,
    private val discoverRoomsUseCase: DiscoverRoomsUseCase,
    private val joinRoomUseCase: JoinRoomUseCase,
    private val leaveRoomUseCase: LeaveRoomUseCase,
    private val syncGameStateUseCase: SyncGameStateUseCase
) : ViewModel() {

    data class NetworkUiState(
        val networkState: NetworkState = NetworkState(),
        val discoveredRooms: List<NetworkRoom> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val showCreateDialog: Boolean = false,
        val statusMessage: String = "غير متصل"
    )

    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    init { observeNetworkEvents() }

    fun createRoom(roomName: String) {
        if (roomName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "أدخل اسم الغرفة")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showCreateDialog = false)
            createRoomUseCase(roomName)
                .onSuccess { state ->
                    _uiState.value = _uiState.value.copy(
                        networkState = state, isLoading = false,
                        statusMessage = "✅ تم إنشاء الغرفة \"$roomName\" — في انتظار اللاعبين"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, error = e.message ?: "فشل إنشاء الغرفة"
                    )
                }
        }
    }

    fun discoverRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, discoveredRooms = emptyList(),
                statusMessage = "🔍 جاري البحث عن الغرف..."
            )
            discoverRoomsUseCase()
                .onSuccess { rooms ->
                    _uiState.value = _uiState.value.copy(
                        discoveredRooms = rooms, isLoading = false,
                        statusMessage = if (rooms.isEmpty()) "لا توجد غرف" else "وجدنا ${rooms.size} غرفة"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, error = e.message ?: "فشل البحث"
                    )
                }
        }
    }

    fun joinRoom(room: NetworkRoom, playerName: String) {
        if (playerName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "أدخل اسمك أولاً")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                statusMessage = "🔗 جاري الاتصال بـ \"${room.name}\"..."
            )
            joinRoomUseCase(room, playerName)
                .onSuccess { state ->
                    _uiState.value = _uiState.value.copy(
                        networkState = state, isLoading = false,
                        statusMessage = "✅ متصل بـ \"${room.name}\""
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, error = e.message ?: "فشل الانضمام"
                    )
                }
        }
    }

    fun leaveRoom() {
        viewModelScope.launch {
            leaveRoomUseCase()
            _uiState.value = NetworkUiState(statusMessage = "غير متصل")
        }
    }

    fun showCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = true) }
    fun dismissCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun observeNetworkEvents() {
        viewModelScope.launch {
            // NetworkState updates come via Flow from use cases
        }
    }
}
