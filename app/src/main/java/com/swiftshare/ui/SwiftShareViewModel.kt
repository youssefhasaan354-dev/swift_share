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
import com.swiftshare.nft.channel.models.TransferManifest
import com.swiftshare.nft.channel.models.TransferProgress
import com.swiftshare.nft.discovery.DeviceDiscoveryManager
import com.swiftshare.nft.discovery.models.ConnectionPayload
import com.swiftshare.nft.discovery.models.PeerDevice
import com.swiftshare.pc.PcRemoteServer
import com.swiftshare.safebox.SafeBoxVault
import com.swiftshare.service.TransferForegroundService
import com.swiftshare.statussaver.StatusSaverManager
import com.swiftshare.webshare.EmbeddedWebShareServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    fun startReceiver(onReady: (ConnectionPayload) -> Unit) {
        discoveryManager.startReceiverMode(
            deviceName = Build.MODEL,
            avatarId = 1,
            onReady = { payload ->
                activeReceiverPayload.value = payload
                // Start control channel and NIO data server
                dataChannel.startReceiverServer(
                    expectedSessionToken = payload.sessionToken,
                    onFileReceived = { file, item ->
                        recordTransfer(item.name, file.absolutePath, item.size, item.mimeType, isIncoming = true)
                    },
                    onError = { e -> e.printStackTrace() }
                )
                onReady(payload)
            },
            onError = { /* ignore or log */ }
        )
    }

    fun connectAndSendFiles(peer: PeerDevice) {
        activePeer.value = peer
        val files = selectedSendFiles.value
        val totalBytes = files.sumOf { it.size }
        val batchId = "batch_" + UUID.randomUUID().toString()

        TransferForegroundService.startService(context, files.firstOrNull()?.name ?: "Transfer", totalBytes)

        viewModelScope.launch(Dispatchers.IO) {
            var cumulative = 0L
            for (item in files) {
                val file = File(item.path)
                if (file.exists()) {
                    val manifestItem = FileManifestItem(
                        fileId = UUID.randomUUID().toString(),
                        name = item.name,
                        size = item.size,
                        mimeType = item.mimeType,
                        thumbnailBase64 = generateThumbnailBase64(item)
                    )
                    dataChannel.sendFile(
                        targetIp = peer.ip,
                        manifestItem = manifestItem,
                        sourceFile = file,
                        batchTotalBytes = totalBytes,
                        cumulativeBytesStart = cumulative,
                        sessionToken = peer.sessionToken,
                        onSuccess = {
                            recordTransfer(item.name, item.path, item.size, item.mimeType, isIncoming = false)
                        },
                        onError = { e -> e.printStackTrace() }
                    )
                    cumulative += item.size
                }
            }
        }
    }

    private fun recordTransfer(fileName: String, path: String, size: Long, mime: String, isIncoming: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = TransferRecordEntity(
                batchId = "batch_record",
                fileId = UUID.randomUUID().toString(),
                fileName = fileName,
                filePath = path,
                fileSize = size,
                mimeType = mime,
                isIncoming = isIncoming,
                status = "COMPLETED",
                peerName = activePeer.value?.deviceName ?: "Peer",
                timestamp = System.currentTimeMillis()
            )
            db.transferDao().insert(record)
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stop()
        webShareServer.stop()
        pcRemoteServer.stop()
        dataChannel.cancel()
    }
}
