package com.agon.app.data.repository

import android.content.Context
import com.agon.app.data.network.*
import com.agon.app.domain.model.*
import com.agon.app.domain.repository.NetworkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _networkState = MutableStateFlow(NetworkState())
    override val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _events = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = 128)
    override val events: Flow<NetworkEvent> = _events.asSharedFlow()

    private val _discoveredRooms = MutableStateFlow<List<NetworkRoom>>(emptyList())

    private var server: NsdGameServer? = null
    private var client: NsdGameClient? = null

    private var localPlayerId: String = ""

    // ── HOST ───────────────────────────────
    override suspend fun createRoom(
        roomName: String,
        maxPlayers: Int
    ): Result<NetworkState> {
        return try {

            val srv = NsdGameServer(context, roomName, maxPlayers)
            server = srv

            scope.launch {
                srv.events.collect { packet ->
                    handleServerPacket(packet)
                }
            }

            val portResult = srv.start()
            if (portResult.isFailure) {
                return Result.failure(portResult.exceptionOrNull()!!)
            }

            localPlayerId = "host_${System.currentTimeMillis()}"

            val hostPlayer = NetworkPlayer(
                id = localPlayerId,
                name = "المضيف",
                isHost = true,
                isReady = true
            )

            val newState = NetworkState(
                isConnected = true,
                isHost = true,
                roomId = roomName,
                roomName = roomName,
                connectedPlayers = listOf(hostPlayer),
                localPlayerId = localPlayerId,
                status = NetworkStatus.CONNECTED
            )

            _networkState.value = newState

            Result.success(newState)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── CLIENT DISCOVERY ─────────────────────
    override suspend fun discoverRooms(): Result<List<NetworkRoom>> {
        return try {

            _networkState.value =
                _networkState.value.copy(status = NetworkStatus.SYNCING)

            val cl = NsdGameClient(context)

            val found = mutableListOf<NetworkRoom>()

            val job = scope.launch {
                cl.discoveredRooms.collect { room ->
                    val networkRoom = NetworkRoom(
                        id = room.name,
                        name = room.name,
                        hostAddress = room.host,
                        hostName = "مضيف",
                        currentPlayers = 1,
                        maxPlayers = 4,
                        port = room.port
                    )

                    if (found.none { it.id == networkRoom.id }) {
                        found.add(networkRoom)
                        _discoveredRooms.value = found.toList()
                    }
                }
            }

            cl.startDiscovery()
            delay(3000)
            cl.stopDiscovery()
            job.cancel()

            _networkState.value =
                _networkState.value.copy(status = NetworkStatus.DISCONNECTED)

            Result.success(_discoveredRooms.value)

        } catch (e: Exception) {
            _networkState.value =
                _networkState.value.copy(
                    status = NetworkStatus.ERROR,
                    error = e.message
                )
            Result.failure(e)
        }
    }

    // ── JOIN ───────────────────────────────
    override suspend fun joinRoom(
        room: NetworkRoom,
        playerName: String
    ): Result<NetworkState> {
        return try {

            _networkState.value =
                _networkState.value.copy(status = NetworkStatus.CONNECTING)

            val cl = NsdGameClient(context)
            client = cl

            scope.launch {
                cl.events.collect { packet ->
                    handleClientPacket(packet)
                }
            }

            val port = room.port ?: NsdGameServer.PORT

            val connectResult = cl.connect(room.hostAddress, port, playerName)
            if (connectResult.isFailure) {
                return Result.failure(connectResult.exceptionOrNull()!!)
            }

            localPlayerId = "client_${System.currentTimeMillis()}"

            val localPlayer = NetworkPlayer(
                id = localPlayerId,
                name = playerName,
                isHost = false
            )

            val newState = NetworkState(
                isConnected = true,
                isHost = false,
                roomId = room.id,
                roomName = room.name,
                connectedPlayers = listOf(localPlayer),
                localPlayerId = localPlayerId,
                status = NetworkStatus.CONNECTED
            )

            _networkState.value = newState

            _events.emit(NetworkEvent.PlayerJoined(localPlayer))

            Result.success(newState)

        } catch (e: Exception) {
            _networkState.value =
                _networkState.value.copy(
                    status = NetworkStatus.ERROR,
                    error = e.message
                )
            Result.failure(e)
        }
    }

    // ── ACTION ─────────────────────────────
    override suspend fun sendGameAction(action: GameAction): Result<Unit> {
        return try {
            if (_networkState.value.isHost) {
                server?.broadcastAction(action, localPlayerId)
            } else {
                client?.sendAction(action)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncGameState(state: GameState): Result<Unit> {
        return try {
            if (_networkState.value.isHost) {
                server?.broadcastState(state)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveRoom(): Result<Unit> {
        return try {
            server?.stop()
            client?.disconnect()
            server = null
            client = null

            _networkState.value = NetworkState()
            _events.emit(NetworkEvent.ConnectionLost)

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startGame(): Result<Unit> {
        if (!_networkState.value.isHost) {
            return Result.failure(
                IllegalStateException("فقط المضيف يمكنه بدء اللعبة")
            )
        }
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> = leaveRoom()

    override suspend fun reconnect(): Result<NetworkState> {
        _networkState.value =
            _networkState.value.copy(status = NetworkStatus.RECONNECTING)
        return Result.failure(
            UnsupportedOperationException("أعد الاتصال يدوياً")
        )
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> {
        val state = _networkState.value

        val updated = state.connectedPlayers.map {
            if (it.id == state.localPlayerId)
                it.copy(isReady = isReady)
            else it
        }

        _networkState.value = state.copy(connectedPlayers = updated)

        return Result.success(Unit)
    }

    // ── SERVER HANDLER ─────────────────────
    private suspend fun handleServerPacket(packet: NetworkPacket) {
        when (packet) {

            is NetworkPacket.PlayerConnected -> {
                val player = NetworkPlayer(
                    id = packet.id,
                    name = packet.name,
                    isHost = false
                )

                val updated = _networkState.value.connectedPlayers + player

                _networkState.value = _networkState.value.copy(
                    connectedPlayers = updated
                )

                _events.emit(NetworkEvent.PlayerJoined(player))
            }

            is NetworkPacket.PlayerDisconnected -> {
                val updated =
                    _networkState.value.connectedPlayers
                        .filter { it.id != packet.id }

                _networkState.value =
                    _networkState.value.copy(connectedPlayers = updated)

                _events.emit(NetworkEvent.PlayerLeft(packet.id))
            }

            is NetworkPacket.ActionReceived -> {
                _events.emit(NetworkEvent.PlayerAction(packet.action))
            }

            is NetworkPacket.Error -> {
                _networkState.value =
                    _networkState.value.copy(error = packet.message)

                _events.emit(NetworkEvent.Error(packet.message))
            }

            else -> Unit
        }
    }

    // ── CLIENT HANDLER ─────────────────────
    private suspend fun handleClientPacket(packet: NetworkPacket) {
        when (packet) {

            is NetworkPacket.StateSync -> {
                _events.emit(NetworkEvent.GameStateSync(packet.state))
            }

            is NetworkPacket.ActionReceived -> {
                _events.emit(NetworkEvent.PlayerAction(packet.action))
            }

            is NetworkPacket.ConnectionLost -> {
                _networkState.value = _networkState.value.copy(
                    isConnected = false,
                    status = NetworkStatus.ERROR,
                    error = "انقطع الاتصال"
                )
                _events.emit(NetworkEvent.ConnectionLost)
            }

            is NetworkPacket.Error -> {
                _networkState.value =
                    _networkState.value.copy(error = packet.message)

                _events.emit(NetworkEvent.Error(packet.message))
            }

            else -> Unit
        }
    }
}