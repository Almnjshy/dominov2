package com.agon.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.domain.model.NetworkRoom
import com.agon.app.domain.model.NetworkState
import com.agon.app.domain.model.NetworkStatus

@Composable
fun NetworkScreen(
    networkState: NetworkState,
    discoveredRooms: List<NetworkRoom>,
    isLoading: Boolean,
    error: String?,
    showCreateDialog: Boolean,
    onCreateRoom: (String) -> Unit,
    onDiscover: () -> Unit,
    onJoinRoom: (NetworkRoom, String) -> Unit,
    onLeaveRoom: () -> Unit,
    onShowCreateDialog: () -> Unit,
    onDismissCreateDialog: () -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    statusMessage: String = ""
) {
    var playerName by remember { mutableStateOf("") }
    var roomToJoin by remember { mutableStateOf<NetworkRoom?>(null) }
    var createRoomName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {

        // Top bar
        TopAppBar(
            title = { Text("اللعب عبر الشبكة") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "رجوع")
                }
            }
        )

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // How it works
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("كيف يعمل؟", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("📶 تأكد أن جميع الأجهزة على نفس الـ WiFi أو Hotspot")
                    Text("🏠 شخص واحد ينشئ غرفة")
                    Text("👥 الباقون يبحثون عن الغرفة وينضمون")
                    Text("🎮 المضيف يبدأ اللعبة")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Status
            val statusColor = when (networkState.status) {
                NetworkStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                NetworkStatus.ERROR -> MaterialTheme.colorScheme.error
                NetworkStatus.CONNECTING, NetworkStatus.SYNCING, NetworkStatus.RECONNECTING ->
                    MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = statusMessage.ifBlank {
                    when (networkState.status) {
                        NetworkStatus.CONNECTED -> "✅ متصل: ${networkState.roomName}"
                        NetworkStatus.CONNECTING -> "⏳ جاري الاتصال..."
                        NetworkStatus.SYNCING -> "🔍 جاري البحث..."
                        NetworkStatus.ERROR -> "❌ خطأ"
                        else -> "⭕ غير متصل"
                    }
                },
                color = statusColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(12.dp))

            if (!networkState.isConnected) {
                // Player name input
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("اسمك في اللعبة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onShowCreateDialog,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) { Text("🏠 أنشئ غرفة") }

                    OutlinedButton(
                        onClick = onDiscover,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("🔍 ابحث")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("جاري البحث... (3 ثواني)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Rooms list
                if (discoveredRooms.isNotEmpty()) {
                    Text("الغرف المتاحة:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(discoveredRooms) { room ->
                            RoomCard(
                                room = room,
                                onJoin = {
                                    if (playerName.isBlank()) roomToJoin = room
                                    else onJoinRoom(room, playerName)
                                }
                            )
                        }
                    }
                } else if (!isLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", style = MaterialTheme.typography.displaySmall)
                            Text("لا توجد غرف — اضغط بحث",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

            } else {
                // Connected view
                ConnectedView(
                    networkState = networkState,
                    onLeave = onLeaveRoom
                )
            }

            // Error
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onClearError) { Text("✕") }
                    }
                }
            }

            // Missing name warning
            if (roomToJoin != null) {
                AlertDialog(
                    onDismissRequest = { roomToJoin = null },
                    title = { Text("أدخل اسمك") },
                    text = {
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = { playerName = it },
                            label = { Text("اسمك") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            onJoinRoom(roomToJoin!!, playerName)
                            roomToJoin = null
                        }, enabled = playerName.isNotBlank()) { Text("انضمام") }
                    },
                    dismissButton = { TextButton(onClick = { roomToJoin = null }) { Text("إلغاء") } }
                )
            }
        }
    }

    // Create room dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateDialog,
            title = { Text("🏠 إنشاء غرفة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتمكن الأصدقاء على نفس الـ WiFi من رؤية غرفتك والانضمام إليها.")
                    OutlinedTextField(
                        value = createRoomName,
                        onValueChange = { createRoomName = it },
                        label = { Text("اسم الغرفة") },
                        placeholder = { Text("مثال: غرفة فيصل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onCreateRoom(createRoomName); createRoomName = "" },
                    enabled = createRoomName.isNotBlank()
                ) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = onDismissCreateDialog) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun RoomCard(room: NetworkRoom, onJoin: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(room.name, fontWeight = FontWeight.Bold)
                Text("المضيف: ${room.hostName}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${room.currentPlayers}/${room.maxPlayers} لاعب",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (room.currentPlayers < room.maxPlayers)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onJoin,
                enabled = room.currentPlayers < room.maxPlayers
            ) { Text("انضمام") }
        }
    }
}

@Composable
private fun ConnectedView(networkState: NetworkState, onLeave: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("الغرفة: ${networkState.roomName}", fontWeight = FontWeight.Bold)
            Text(if (networkState.isHost) "أنت المضيف" else "لاعب",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("اللاعبون المتصلون:", fontWeight = FontWeight.Medium)
            networkState.connectedPlayers.forEach { player ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (player.isHost) "👑" else "👤")
                    Spacer(Modifier.width(4.dp))
                    Text(player.name)
                    if (player.isReady) {
                        Spacer(Modifier.width(4.dp))
                        Text("✅", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (networkState.isHost) {
                Text(
                    "في انتظار اللاعبين... (${networkState.playerCount}/${4})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("مغادرة الغرفة") }
}
