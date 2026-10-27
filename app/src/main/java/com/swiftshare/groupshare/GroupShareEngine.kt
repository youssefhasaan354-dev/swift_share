package com.swiftshare.groupshare

import com.swiftshare.nft.channel.HighSpeedDataChannel
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.nft.discovery.models.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class MemberTransferProgress(
    val peerId: String,
    val peerName: String,
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: FileTransferStatus = FileTransferStatus.PENDING,
    val errorMessage: String? = null
) {
    val percent: Int
        get() = if (totalBytes > 0) ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

data class GroupRoom(
    val roomId: String,
    val roomName: String,
    val hostDevice: PeerDevice,
    val isHost: Boolean = true,
    val members: List<PeerDevice> = emptyList(),
    val sharedFiles: List<FileManifestItem> = emptyList()
)

class GroupShareEngine(
    private val defaultDataChannel: HighSpeedDataChannel = HighSpeedDataChannel(),
    private val channelFactory: () -> HighSpeedDataChannel = { HighSpeedDataChannel() }
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentRoom = MutableStateFlow<GroupRoom?>(null)
    val currentRoom: StateFlow<GroupRoom?> = _currentRoom.asStateFlow()

    private val _broadcastStatus = MutableStateFlow<String?>(null)
    val broadcastStatus: StateFlow<String?> = _broadcastStatus.asStateFlow()

    private val _memberProgress = MutableStateFlow<Map<String, MemberTransferProgress>>(emptyMap())
    val memberProgress: StateFlow<Map<String, MemberTransferProgress>> = _memberProgress.asStateFlow()

    private val connectedPeers = ConcurrentHashMap<String, PeerDevice>()
    private val roomFiles = mutableListOf<Pair<FileManifestItem, File>>()
    private val activeChannels = ConcurrentHashMap<String, HighSpeedDataChannel>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

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
        activeChannels.remove(peerId)?.cancel()
        activeJobs.remove(peerId)?.cancel()
        _memberProgress.update { it - peerId }
        val current = _currentRoom.value ?: return
        val updatedMembers = current.members.filterNot { it.id == peerId }
        _currentRoom.value = current.copy(members = updatedMembers)
    }

    fun broadcastFileToAllMembers(
        file: File,
        manifestItem: FileManifestItem,
        onAllCompleted: (() -> Unit)? = null
    ) {
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

        val initialProgress = targets.associate { peer ->
            peer.id to MemberTransferProgress(
                peerId = peer.id,
                peerName = peer.deviceName,
                transferredBytes = 0L,
                totalBytes = file.length(),
                status = FileTransferStatus.TRANSFERRING
            )
        }
        _memberProgress.update { it + initialProgress }

        val remainingTargets = java.util.concurrent.atomic.AtomicInteger(targets.size)

        targets.forEach { targetPeer ->
            val channel = channelFactory()
            activeChannels[targetPeer.id] = channel

            val job = scope.launch {
                // Monitor progress from channel
                val progressJob = launch {
                    channel.transferProgress.collect { tp ->
                        _memberProgress.update { map ->
                            val currentProg = map[targetPeer.id] ?: MemberTransferProgress(targetPeer.id, targetPeer.deviceName)
                            map + (targetPeer.id to currentProg.copy(
                                transferredBytes = tp.totalBytesTransferred,
                                totalBytes = if (tp.batchTotalBytes > 0) tp.batchTotalBytes else file.length(),
                                status = tp.status
                            ))
                        }
                    }
                }

                channel.sendFileParallel(
                    targetIp = targetPeer.ip,
                    manifestItem = manifestItem,
                    sourceFile = file,
                    sessionToken = targetPeer.sessionToken,
                    batchId = "group_${System.currentTimeMillis()}_${targetPeer.id}",
                    port = targetPeer.port,
                    streamCount = 4,
                    onSuccess = {
                        progressJob.cancel()
                        _memberProgress.update { map ->
                            val currentProg = map[targetPeer.id] ?: MemberTransferProgress(targetPeer.id, targetPeer.deviceName)
                            map + (targetPeer.id to currentProg.copy(
                                transferredBytes = file.length(),
                                totalBytes = file.length(),
                                status = FileTransferStatus.COMPLETED
                            ))
                        }
                        if (remainingTargets.decrementAndGet() == 0) {
                            _broadcastStatus.value = "Broadcast of ${file.name} complete to all members!"
                            onAllCompleted?.invoke()
                        }
                    },
                    onError = { err ->
                        progressJob.cancel()
                        _memberProgress.update { map ->
                            val currentProg = map[targetPeer.id] ?: MemberTransferProgress(targetPeer.id, targetPeer.deviceName)
                            map + (targetPeer.id to currentProg.copy(
                                status = FileTransferStatus.FAILED,
                                errorMessage = err.message
                            ))
                        }
                        com.swiftshare.common.AppLogger.e("GroupShareEngine", "Broadcast error to ${targetPeer.deviceName}: ${err.message}", err, notifyUser = true)
                        if (remainingTargets.decrementAndGet() == 0) {
                            _broadcastStatus.value = "Broadcast finished with some errors."
                        }
                    }
                )
            }
            activeJobs[targetPeer.id] = job
        }
    }

    fun leaveRoom() {
        activeChannels.values.forEach { it.cancel() }
        activeChannels.clear()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        connectedPeers.clear()
        roomFiles.clear()
        _currentRoom.value = null
        _broadcastStatus.value = null
        _memberProgress.value = emptyMap()
    }
}
