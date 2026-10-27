package com.swiftshare.groupshare

import com.swiftshare.nft.channel.HighSpeedDataChannel
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.discovery.models.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class GroupRoom(
    val roomId: String,
    val roomName: String,
    val hostDevice: PeerDevice,
    val isHost: Boolean = true,
    val members: List<PeerDevice> = emptyList(),
    val sharedFiles: List<FileManifestItem> = emptyList()
)

class GroupShareEngine(
    private val dataChannel: HighSpeedDataChannel
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentRoom = MutableStateFlow<GroupRoom?>(null)
    val currentRoom: StateFlow<GroupRoom?> = _currentRoom.asStateFlow()

    private val _broadcastStatus = MutableStateFlow<String?>(null)
    val broadcastStatus: StateFlow<String?> = _broadcastStatus.asStateFlow()

    private val connectedPeers = ConcurrentHashMap<String, PeerDevice>()
    private val roomFiles = mutableListOf<Pair<FileManifestItem, File>>()

    fun createRoom(host: PeerDevice, roomName: String = "SwiftShare Portal Room") {
        val room = GroupRoom(
            roomId = "room_" + System.currentTimeMillis(),
            roomName = roomName,
            hostDevice = host,
            isHost = true,
            members = listOf(host)
        )
        _currentRoom.value = room
    }

    fun joinRoom(host: PeerDevice, myDevice: PeerDevice) {
        val room = GroupRoom(
            roomId = "room_joined_" + host.id,
            roomName = "${host.deviceName}'s Portal",
            hostDevice = host,
            isHost = false,
            members = listOf(host, myDevice)
        )
        connectedPeers[host.id] = host
        _currentRoom.value = room
    }

    fun addMember(peer: PeerDevice) {
        connectedPeers[peer.id] = peer
        val current = _currentRoom.value ?: return
        val updatedMembers = (current.members + peer).distinctBy { it.id }
        _currentRoom.value = current.copy(members = updatedMembers)
    }

    fun removeMember(peerId: String) {
        connectedPeers.remove(peerId)
        val current = _currentRoom.value ?: return
        val updatedMembers = current.members.filterNot { it.id == peerId }
        _currentRoom.value = current.copy(members = updatedMembers)
    }

    fun broadcastFileToAllMembers(file: File, manifestItem: FileManifestItem) {
        roomFiles.add(Pair(manifestItem, file))
        val current = _currentRoom.value ?: return
        _currentRoom.value = current.copy(sharedFiles = current.sharedFiles + manifestItem)

        // Distribute to all other members concurrently in star topology
        val targets = if (current.isHost) {
            connectedPeers.values.filter { it.id != current.hostDevice.id }
        } else {
            listOf(current.hostDevice)
        }

        if (targets.isEmpty()) {
            _broadcastStatus.value = "File added to room. Waiting for members to connect."
            return
        }

        _broadcastStatus.value = "Broadcasting ${file.name} to ${targets.size} member(s)..."

        targets.forEach { targetPeer ->
            scope.launch {
                dataChannel.sendFile(
                    targetIp = targetPeer.ip,
                    manifestItem = manifestItem,
                    sourceFile = file,
                    batchTotalBytes = file.length(),
                    cumulativeBytesStart = 0L,
                    sessionToken = targetPeer.sessionToken,
                    onSuccess = {
                        _broadcastStatus.value = "Broadcast of ${file.name} complete!"
                    },
                    onError = { err ->
                        com.swiftshare.common.AppLogger.e("GroupShareEngine", "Broadcast error: ${err.message}", err, notifyUser = true)
                        _broadcastStatus.value = "Broadcast error: ${err.message}"
                    }
                )
            }
        }
    }

    fun leaveRoom() {
        connectedPeers.clear()
        roomFiles.clear()
        _currentRoom.value = null
        _broadcastStatus.value = null
    }
}
