package com.swiftshare.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swiftshare.SwiftShareApp
import com.swiftshare.cleaner.JunkCleanerEngine
import com.swiftshare.common.Constants
import com.swiftshare.common.NetworkUtils
import com.swiftshare.converter.VideoToAudioConverter
import com.swiftshare.data.entities.TransferRecordEntity
import com.swiftshare.explorer.MediaItem
import com.swiftshare.explorer.MediaStoreRepository
import com.swiftshare.explorer.StorageAnalyzer
import com.swiftshare.explorer.StorageBreakdown
import com.swiftshare.groupshare.GroupShareEngine
import com.swiftshare.nft.channel.HighSpeedDataChannel
import com.swiftshare.nft.channel.TransferControlChannel
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.nft.channel.models.TransferManifest
import com.swiftshare.nft.channel.models.TransferProgress
import com.swiftshare.nft.discovery.DeviceDiscoveryManager
import com.swiftshare.nft.discovery.QrPayloadManager
import com.swiftshare.nft.discovery.models.ConnectionPayload
import com.swiftshare.nft.discovery.models.PeerDevice
import com.swiftshare.pc.PcRemoteServer
import com.swiftshare.safebox.SafeBoxVault
import com.swiftshare.service.TransferForegroundService
import com.swiftshare.statussaver.StatusSaverManager
import com.swiftshare.webshare.EmbeddedWebShareServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class SwiftShareViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = SwiftShareApp.database

    // Engines
    val mediaRepo = MediaStoreRepository(context)
    val storageAnalyzer = StorageAnalyzer(mediaRepo)
    val discoveryManager = DeviceDiscoveryManager(context)
    val dataChannel = HighSpeedDataChannel()
    val controlChannel = TransferControlChannel()
    val webShareServer = EmbeddedWebShareServer(context)
    val pcRemoteServer = PcRemoteServer(context, mediaRepo)
    val groupShareEngine = GroupShareEngine(dataChannel)
    val safeBoxVault = SafeBoxVault(context, db.safeBoxDao())
    val junkCleaner = JunkCleanerEngine(context)
    val statusSaver = StatusSaverManager(context)
    val videoConverter = VideoToAudioConverter(context)

    // State
    val selectedSendFiles = MutableStateFlow<List<MediaItem>>(emptyList())
    val activePeer = MutableStateFlow<PeerDevice?>(null)
    val activeReceiverPayload = MutableStateFlow<ConnectionPayload?>(null)
    val transferProgress: StateFlow<TransferProgress> = dataChannel.transferProgress
    val storageBreakdown = MutableStateFlow<StorageBreakdown?>(null)
    val incomingTransferOffer = MutableStateFlow<com.swiftshare.nft.channel.models.TransferOffer?>(null)
    val lastTransferResult = MutableStateFlow<TransferResultData?>(null)

    fun acceptIncomingOffer(offer: com.swiftshare.nft.channel.models.TransferOffer) {
        controlChannel.sendTransferResponse(
            com.swiftshare.nft.channel.models.TransferResponse(
                batchId = offer.batchId,
                accepted = true,
                reason = com.swiftshare.nft.channel.models.TransferRejectionReasons.OK
            )
        )
        incomingTransferOffer.value = null
        viewModelScope.launch(Dispatchers.IO) {
            db.sessionDao().updateSessionStatus(offer.batchId, "IN_PROGRESS", 0L)
        }
    }

    fun rejectIncomingOffer(offer: com.swiftshare.nft.channel.models.TransferOffer, reason: String = com.swiftshare.nft.channel.models.TransferRejectionReasons.USER_REJECTED) {
        controlChannel.sendTransferResponse(
            com.swiftshare.nft.channel.models.TransferResponse(
                batchId = offer.batchId,
                accepted = false,
                reason = reason
            )
        )
        incomingTransferOffer.value = null
        viewModelScope.launch(Dispatchers.IO) {
            db.sessionDao().updateSessionStatus(offer.batchId, "REJECTED", 0L, System.currentTimeMillis())
        }
    }

    fun loadStorageBreakdown() {
        viewModelScope.launch(Dispatchers.IO) {
            storageBreakdown.value = storageAnalyzer.analyzeStorage()
        }
    }

    fun generateThumbnailBase64(item: MediaItem): String? {
        return try {
            val imgBitmap = when (item.category) {
                "photos" -> item.thumbnailUri?.let { mediaRepo.loadPhotoThumbnail(android.net.Uri.parse(it)) }
                "videos" -> item.thumbnailUri?.let { mediaRepo.loadVideoThumbnail(item.path, android.net.Uri.parse(it)) }
                "music" -> mediaRepo.loadAudioAlbumArt(item.path)
                "apps" -> mediaRepo.loadAppIconBitmap(item.packageName, item.path)
                else -> null
            } ?: return null
            val androidBmp = imgBitmap.asAndroidBitmap()
            val scaled = Bitmap.createScaledBitmap(androidBmp, 96, 96, true)
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun setSelectedFiles(files: List<MediaItem>) {
        selectedSendFiles.value = files
        // Also queue in WebShare server
        val manifestPairs = files.map { item ->
            Pair(
                FileManifestItem(
                    fileId = item.id,
                    name = item.name,
                    size = item.size,
                    mimeType = item.mimeType,
                    thumbnailBase64 = generateThumbnailBase64(item)
                ),
                File(item.path)
            )
        }
        webShareServer.setQueuedFiles(manifestPairs)
    }

    val connectedPeerEvent = MutableSharedFlow<PeerDevice>(extraBufferCapacity = 8)

    fun startReceiver(onReady: (ConnectionPayload) -> Unit) {
        val devName = try { Build.MODEL ?: "SwiftShare Receiver" } catch (_: Throwable) { "SwiftShare Receiver" }
        discoveryManager.startReceiverMode(
            deviceName = devName,
            avatarId = 1,
            onReady = { payload ->
                activeReceiverPayload.value = payload
                val freeStorage = com.swiftshare.common.StorageDestinationManager.getFreeSpace(context)

                controlChannel.startServer(
                    expectedSessionToken = payload.sessionToken,
                    myDeviceName = devName,
                    freeStorageBytes = freeStorage,
                    onHandshakeComplete = { req ->
                        val peer = PeerDevice(
                            id = req.deviceName,
                            deviceName = req.deviceName,
                            ip = payload.ip,
                            port = Constants.PORT_DATA,
                            sessionToken = payload.sessionToken
                        )
                        activePeer.value = peer
                        connectedPeerEvent.tryEmit(peer)
                    }
                )

                var currentIncomingBatchId: String? = null
                controlChannel.startHeartbeat(devName, onPeerLost = {
                    com.swiftshare.common.AppLogger.e("SwiftShareVM", "Peer lost during receiver mode (missed 3 heartbeats)", notifyUser = true)
                })

                viewModelScope.launch(Dispatchers.IO) {
                    controlChannel.incomingMessages.collect { msg ->
                        when (msg.type) {
                            com.swiftshare.nft.channel.models.MessageType.TRANSFER_OFFER -> {
                                msg.transferOffer?.let { offer ->
                                    val freeBytes = com.swiftshare.common.StorageDestinationManager.getFreeSpace(context)
                                    val needed = offer.totalBytes + 50L * 1024L * 1024L // 50 MB safety headroom
                                    if (freeBytes < needed) {
                                        com.swiftshare.common.AppLogger.e("SwiftShareVM", "Rejecting offer: NOT_ENOUGH_SPACE (free: $freeBytes, needed: $needed)", notifyUser = true)
                                        controlChannel.sendTransferResponse(
                                            com.swiftshare.nft.channel.models.TransferResponse(
                                                batchId = offer.batchId,
                                                accepted = false,
                                                reason = com.swiftshare.nft.channel.models.TransferRejectionReasons.NOT_ENOUGH_SPACE
                                            )
                                        )
                                    } else {
                                        currentIncomingBatchId = offer.batchId
                                        val destinationDir = com.swiftshare.common.StorageDestinationManager.getDestinationDirectory(context)
                                        val existingNames = if (destinationDir.exists()) destinationDir.list()?.toSet() ?: emptySet() else emptySet()

                                        val session = com.swiftshare.data.entities.SessionEntity(
                                            sessionId = offer.batchId,
                                            peerName = offer.senderName,
                                            peerIp = payload.ip,
                                            isIncoming = true,
                                            totalFiles = offer.fileCount,
                                            totalBytes = offer.totalBytes,
                                            transferredBytes = 0L,
                                            status = "PENDING",
                                            startTime = System.currentTimeMillis()
                                        )
                                        val items = offer.files.map { mi ->
                                            val alreadyExists = existingNames.contains(mi.name)
                                            com.swiftshare.data.entities.TransferItemEntity(
                                                sessionId = offer.batchId,
                                                fileId = mi.fileId,
                                                name = mi.name,
                                                size = mi.size,
                                                mimeType = mi.mimeType,
                                                transferredBytes = if (alreadyExists) mi.size else 0L,
                                                status = if (alreadyExists) "EXISTS" else "PENDING",
                                                uriOrPath = mi.name
                                            )
                                        }
                                        db.sessionDao().insertSession(session)
                                        db.sessionDao().insertItems(items)

                                        incomingTransferOffer.value = offer
                                    }
                                }
                            }
                            com.swiftshare.nft.channel.models.MessageType.TRANSFER_MANIFEST -> {
                                msg.manifest?.let { manifest ->
                                    currentIncomingBatchId = manifest.batchId
                                    manifest.files.forEach { item ->
                                        dataChannel.registerManifestItem(item)
                                    }
                                }
                            }
                            com.swiftshare.nft.channel.models.MessageType.CANCEL_TRANSFER -> {
                                msg.cancelTransfer?.let { cancel ->
                                    com.swiftshare.common.AppLogger.e("SwiftShareVM", "Peer cancelled transfer: ${cancel.reason}", notifyUser = true)
                                    dataChannel.cancel(explicitUserCancel = true)
                                    db.sessionDao().updateSessionStatus(cancel.batchId, "CANCELLED", 0L, System.currentTimeMillis())
                                }
                            }
                            com.swiftshare.nft.channel.models.MessageType.FILE_CHECKSUM -> {
                                msg.fileChecksum?.let { cs ->
                                    dataChannel.notifyChecksumReceived(cs.fileId, cs.sha256)
                                }
                            }
                            com.swiftshare.nft.channel.models.MessageType.AVATAR_UPDATE -> {
                                msg.avatarUpdate?.let { upd ->
                                    activePeer.value = activePeer.value?.copy(
                                        deviceName = upd.deviceName,
                                        avatarEmoji = upd.avatarEmoji,
                                        customAvatarBase64 = upd.customAvatarBase64
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // Send own avatar to connecting peer
                controlChannel.sendAvatarUpdate(
                    deviceName = devName,
                    avatarEmoji = com.swiftshare.common.LocaleManager.avatarEmoji.value,
                    customAvatarBase64 = com.swiftshare.common.LocaleManager.customAvatarBase64.value
                )

                val rxDestDir = com.swiftshare.common.StorageDestinationManager.getDestinationDirectory(context)
                dataChannel.startReceiverServer(
                    expectedSessionToken = payload.sessionToken,
                    downloadDir = rxDestDir,
                    onFileReceived = { file, item ->
                        val bId = currentIncomingBatchId ?: "batch_incoming"
                        recordTransfer(bId, item.fileId, item.name, file.absolutePath, item.size, item.mimeType, isIncoming = true, status = "COMPLETED")
                    },
                    onError = { e ->
                        com.swiftshare.common.AppLogger.e("SwiftShareVM", "Receiver channel failed: ${e.message}", e, notifyUser = true)
                    }
                )
                onReady(payload)
            },
            onError = { err ->
                com.swiftshare.common.AppLogger.e("SwiftShareVM", "Receiver mode failed: $err", notifyUser = true)
            }
        )
    }

    val userErrors = com.swiftshare.common.AppLogger.userVisibleErrors

    fun connectAndSendFiles(peer: PeerDevice) {
        activePeer.value = peer
        val files = selectedSendFiles.value
        val totalBytes = files.sumOf { it.size }
        val batchId = "batch_" + UUID.randomUUID().toString()
        val devName = try { Build.MODEL ?: "SwiftShare Sender" } catch (_: Throwable) { "SwiftShare Sender" }

        TransferForegroundService.startService(context, files.firstOrNull()?.name ?: "Transfer", totalBytes)

        val freeStorage = com.swiftshare.common.StorageDestinationManager.getFreeSpace(context)

        controlChannel.connectToHost(
            hostIp = peer.ip,
            myDeviceName = devName,
            sessionToken = peer.sessionToken ?: "",
            freeStorageBytes = freeStorage,
            onHandshakeSuccess = {
                controlChannel.startHeartbeat(devName, onPeerLost = {
                    com.swiftshare.common.AppLogger.e("SwiftShareVM", "Peer lost during transmission (missed 3 heartbeats)", notifyUser = true)
                })

                controlChannel.sendAvatarUpdate(
                    deviceName = devName,
                    avatarEmoji = com.swiftshare.common.LocaleManager.avatarEmoji.value,
                    customAvatarBase64 = com.swiftshare.common.LocaleManager.customAvatarBase64.value
                )

                viewModelScope.launch(Dispatchers.IO) {
                    controlChannel.incomingMessages.collect { msg ->
                        if (msg.type == com.swiftshare.nft.channel.models.MessageType.AVATAR_UPDATE) {
                            msg.avatarUpdate?.let { upd ->
                                activePeer.value = activePeer.value?.copy(
                                    deviceName = upd.deviceName,
                                    avatarEmoji = upd.avatarEmoji,
                                    customAvatarBase64 = upd.customAvatarBase64
                                )
                            }
                        }
                    }
                }

                val manifestItems = files.map { item ->
                    FileManifestItem(
                        fileId = UUID.randomUUID().toString(),
                        name = item.name,
                        size = item.size,
                        mimeType = item.mimeType,
                        thumbnailBase64 = generateThumbnailBase64(item)
                    )
                }

                // Record Outgoing Session & Items in Room
                val session = com.swiftshare.data.entities.SessionEntity(
                    sessionId = batchId,
                    peerName = peer.deviceName,
                    peerIp = peer.ip,
                    isIncoming = false,
                    totalFiles = manifestItems.size,
                    totalBytes = totalBytes,
                    transferredBytes = 0L,
                    status = "PENDING",
                    startTime = System.currentTimeMillis()
                )
                val items = manifestItems.map { mi ->
                    com.swiftshare.data.entities.TransferItemEntity(
                        sessionId = batchId,
                        fileId = mi.fileId,
                        name = mi.name,
                        size = mi.size,
                        mimeType = mi.mimeType,
                        transferredBytes = 0L,
                        status = "PENDING",
                        uriOrPath = mi.relativePath
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    db.sessionDao().insertSession(session)
                    db.sessionDao().insertItems(items)
                }

                // Send Transfer Offer first and listen for response
                controlChannel.sendTransferOffer(
                    com.swiftshare.nft.channel.models.TransferOffer(
                        batchId = batchId,
                        senderName = devName,
                        fileCount = manifestItems.size,
                        totalBytes = totalBytes,
                        files = manifestItems
                    )
                )

                viewModelScope.launch(Dispatchers.IO) {
                    controlChannel.incomingMessages.collect { msg ->
                        when (msg.type) {
                            com.swiftshare.nft.channel.models.MessageType.TRANSFER_RESPONSE -> {
                                val resp = msg.transferResponse
                                if (resp != null && resp.batchId == batchId) {
                                    if (resp.accepted) {
                                        db.sessionDao().updateSessionStatus(batchId, "IN_PROGRESS", 0L)
                                        controlChannel.sendManifest(
                                            TransferManifest(
                                                batchId = batchId,
                                                fileCount = manifestItems.size,
                                                totalBytes = totalBytes,
                                                files = manifestItems
                                            )
                                        )

                                        var cumulative = 0L
                                        var failedCount = 0
                                        for ((index, item) in files.withIndex()) {
                                            val file = File(item.path)
                                            val manifestItem = manifestItems.getOrNull(index) ?: FileManifestItem(
                                                fileId = UUID.randomUUID().toString(),
                                                name = item.name,
                                                size = item.size,
                                                mimeType = item.mimeType
                                            )
                                            if (file.exists()) {
                                                dataChannel.sendFileParallel(
                                                    targetIp = peer.ip,
                                                    manifestItem = manifestItem,
                                                    sourceFile = file,
                                                    sessionToken = peer.sessionToken,
                                                    controlChannel = controlChannel,
                                                    batchId = batchId,
                                                    streamCount = 4,
                                                    onSuccess = {
                                                        recordTransfer(batchId, manifestItem.fileId, item.name, item.path, item.size, item.mimeType, isIncoming = false, status = "COMPLETED")
                                                        controlChannel.sendStatusAck(batchId, manifestItem.fileId, FileTransferStatus.COMPLETED, item.size)
                                                    },
                                                    onError = { e ->
                                                        failedCount++
                                                        com.swiftshare.common.AppLogger.e("SwiftShareVM", "File transfer failed (${item.name}): ${e.message}", e, notifyUser = true)
                                                        recordTransfer(batchId, manifestItem.fileId, item.name, item.path, item.size, item.mimeType, isIncoming = false, status = "FAILED")
                                                        controlChannel.sendStatusAck(batchId, manifestItem.fileId, FileTransferStatus.FAILED, 0L)
                                                    }
                                                )
                                                cumulative += item.size
                                            }
                                        }
                                        val finalStatus = if (failedCount == 0) "COMPLETED" else "FAILED"
                                        db.sessionDao().updateSessionStatus(batchId, finalStatus, cumulative, System.currentTimeMillis())
                                        lastTransferResult.value = TransferResultData(
                                            batchId = batchId,
                                            isSuccess = (failedCount == 0),
                                            fileCount = files.size,
                                            totalBytes = cumulative,
                                            peerName = peer.deviceName
                                        )
                                    } else {
                                        com.swiftshare.common.AppLogger.e("SwiftShareVM", "Transfer offer was rejected by receiver: ${resp.reason}", notifyUser = true)
                                        db.sessionDao().updateSessionStatus(batchId, "REJECTED", 0L, System.currentTimeMillis())
                                        lastTransferResult.value = TransferResultData(
                                            batchId = batchId,
                                            isSuccess = false,
                                            fileCount = files.size,
                                            totalBytes = totalBytes,
                                            peerName = peer.deviceName,
                                            reason = resp.reason
                                        )
                                    }
                                }
                            }
                            com.swiftshare.nft.channel.models.MessageType.CANCEL_TRANSFER -> {
                                val cancel = msg.cancelTransfer
                                if (cancel != null && cancel.batchId == batchId) {
                                    com.swiftshare.common.AppLogger.e("SwiftShareVM", "Peer cancelled transfer: ${cancel.reason}", notifyUser = true)
                                    dataChannel.cancel(explicitUserCancel = true)
                                    db.sessionDao().updateSessionStatus(batchId, "CANCELLED", 0L, System.currentTimeMillis())
                                }
                            }
                            else -> {}
                        }
                    }
                }
            },
            onError = { err ->
                com.swiftshare.common.AppLogger.e("SwiftShareVM", "Handshake failed with peer ${peer.deviceName}: $err", notifyUser = true)
            }
        )
    }


    private fun recordTransfer(
        batchId: String,
        fileId: String,
        fileName: String,
        path: String,
        size: Long,
        mime: String,
        isIncoming: Boolean,
        status: String = "COMPLETED"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = TransferRecordEntity(
                batchId = batchId,
                fileId = fileId,
                fileName = fileName,
                filePath = path,
                fileSize = size,
                mimeType = mime,
                isIncoming = isIncoming,
                status = status,
                peerName = activePeer.value?.deviceName ?: "Peer",
                timestamp = System.currentTimeMillis()
            )
            db.transferDao().insert(record)
            db.sessionDao().updateItemStatus(batchId, fileId, status, if (status == "COMPLETED") size else 0L)
        }
    }

    fun cancelTransfer(batchId: String? = null) {
        controlChannel.close()
        dataChannel.cancel(explicitUserCancel = true)
        TransferForegroundService.stopService(getApplication())
        QrPayloadManager.unbindProcessNetwork(getApplication())
        batchId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                db.sessionDao().updateSessionStatus(id, "CANCELLED", 0L, System.currentTimeMillis())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stop()
        webShareServer.stop()
        pcRemoteServer.stop()
        controlChannel.close()
        dataChannel.cancel()
        QrPayloadManager.unbindProcessNetwork(getApplication())
    }
}

data class TransferResultData(
    val batchId: String,
    val isSuccess: Boolean,
    val fileCount: Int,
    val totalBytes: Long,
    val peerName: String,
    val reason: String = "OK"
)
