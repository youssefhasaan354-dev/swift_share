# SwiftShare Architecture & Current Transfer Flow Audit (Phase 0)

## 1. Overview
SwiftShare (`com.swiftshare`) is an offline peer-to-peer file sharing Android application built with Kotlin, Jetpack Compose, Ktor, Room, and CameraX + ML Kit.

---

## 2. Real Current Transfer Flow (Tap Send/Receive to File on Disk)

### 2.1 Sender Sequence
```
[HomeScreen]
    │ (Tap SEND)
    ▼
[FilePickerScreen]
    │ User selects files (MediaStoreRepository)
    │ Taps "Send"
    ▼
[SwiftShareViewModel.setSelectedFiles(files)]
    │ Navigates to "radar_send"
    ▼
[RadarScanScreen (isReceiverMode = false)]
    │ DeviceDiscoveryManager.startSenderMode()
    │   ├── NsdDiscoveryManager.startDiscovery()
    │   └── NsdDiscoveryManager.listenForUdpBeacons() (UDP: 55283)
    │ Discovered peers displayed in RadarScanView
    │ OR CameraScannerPreview scans receiver QR (QrPayloadManager.BarcodeAnalyzer)
    │ User taps peer or QR matches -> onPeerConnected(peer)
    ▼
[SwiftShareViewModel.connectAndSendFiles(peer)]
    │ TransferForegroundService.startService()
    │ Loops sequentially: for (item in selectedSendFiles)
    ▼
[HighSpeedDataChannel.sendFile]
    │ SocketChannel.open(InetSocketAddress(peer.ip, PORT_DATA_NIO: 2998))
    │ Writes raw binary header: [fileId (36B)][offset (8B)][totalSize (8B)][nameLength (4B)][name (NB)][thumbLen (4B)][thumbBytes (MB)]
    │ Zero-copy transfer: fileChannel.transferTo(position, 512KB, socketChannel)
    │ Emits progress into HighSpeedDataChannel._transferProgress StateFlow
    ▼
[TransferProgressScreen]
    │ Observes HighSpeedDataChannel.transferProgress
    │ Displays progress bar, speed, ETA
```

### 2.2 Receiver Sequence
```
[HomeScreen]
    │ (Tap RECEIVE)
    ▼
[RadarScanScreen (isReceiverMode = true)]
    │ DeviceDiscoveryManager.startReceiverMode()
    │   ├── HotspotManager.startLocalOnlyHotspot() OR WifiP2pDiscovery.createGroup()
    │   ├── NsdDiscoveryManager.registerService(PORT_CONTROL_HTTP: 2999)
    │   └── NsdDiscoveryManager.startUdpBroadcasting(PORT_CONTROL_HTTP: 2999)
    │ Builds ConnectionPayload (Hotspot IP / LAN IP, port: 2999, token)
    │ Displays QrCodeDialog (App QR / Wi-Fi QR / WebShare URL)
    ▼
[SwiftShareViewModel.startReceiver(payload)]
    ▼
[HighSpeedDataChannel.startReceiverServer]
    │ ServerSocketChannel.bind(PORT_DATA_NIO: 2998)
    │ ServerSocketChannel.accept() loop in coroutine
    ▼
[HighSpeedDataChannel.handleIncomingConnection -> receiveIncomingFile]
    │ Reads raw binary header [fileId, offset, totalSize, nameLength, fileName, thumb]
    │ File(downloadDir, fileName) -> direct FileOutputStream
    │ (downloadDir = /storage/emulated/0/Download/SwiftShare)
    │ Streams socketChannel.read() into FileChannel
    │ When EOF reached -> triggers onFileReceived(targetFile, manifestItem)
    ▼
[SwiftShareViewModel.recordTransfer]
    │ Inserts TransferHistoryEntity into Room DB (TransferDao)
```

---

## 3. Class Names & Roles in the Transfer Architecture

| Class Name | Package | Role in Current Flow |
|---|---|---|
| `MainActivity` | `com.swiftshare` | NavHost, Bottom Navigation, top-level intent interceptor |
| `HomeScreen` | `com.swiftshare.ui.screens` | Hero Send/Receive entry points and quick actions |
| `FilePickerScreen` | `com.swiftshare.ui.screens` | Media category picker & folder tree explorer |
| `RadarScanScreen` | `com.swiftshare.ui.screens` | Radar discovery canvas, QR generator, CameraX scanner |
| `TransferProgressScreen` | `com.swiftshare.ui.screens` | Transfer progress UI tracking `transferProgress` |
| `SwiftShareViewModel` | `com.swiftshare.ui` | Orchestrates discovery, file batching, and history |
| `DeviceDiscoveryManager` | `com.swiftshare.nft.discovery` | Manages Wi-Fi Direct, Hotspot, NSD, and UDP beaconing |
| `NsdDiscoveryManager` | `com.swiftshare.nft.discovery` | mDNS/NSD registration/discovery & UDP 55283 beaconing |
| `HotspotManager` | `com.swiftshare.nft.discovery` | Starts Android LocalOnlyHotspot |
| `QrPayloadManager` | `com.swiftshare.nft.discovery` | QR payload encoding/decoding and CameraX BarcodeAnalyzer |
| `HighSpeedDataChannel` | `com.swiftshare.nft.channel` | Custom NIO SocketChannel binary file transfer server/client |
| `TransferControlChannel` | `com.swiftshare.nft.channel` | JSON message handshake channel (**Currently dead code**) |
| `ChunkManager` | `com.swiftshare.nft.channel` | In-memory resume offset map and SHA-256 calculator |
| `TransferForegroundService`| `com.swiftshare.service` | Keeps process alive with status notification during transfer |
| `TransferDatabase` | `com.swiftshare.data.db` | Room database storing `TransferHistoryEntity` records |

---

## 4. Verification and Citations of Suspected Defects

1. **`TransferControlChannel` instantiated in `SwiftShareViewModel` but never started/used in send/receive flow**:
   - **CONFIRMED**: Instantiated at [`SwiftShareViewModel.kt:52`](file:///d:/share/app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt#L52) (`val controlChannel = TransferControlChannel()`), but `controlChannel.startServer()` and `controlChannel.connectAndHandshake()` are never called anywhere in the send/receive flow.

2. **In receiver mode (`radar_receive`), nothing triggers `onPeerConnected`**:
   - **CONFIRMED**: In [`RadarScanScreen.kt:76-85`](file:///d:/share/app/src/main/java/com/swiftshare/ui/screens/RadarScanScreen.kt#L76-L85), receiver mode only starts advertisement and opens `showQrDialog = true`. `onPeerConnected` is only invoked from `onBarcodeDetected` (which is guarded by `if (showCameraScanner && !isReceiverMode)` at line 159) or from manual peer tap on the sender's radar canvas. The receiver never receives an event that transitions it out of `radar_receive`.

3. **`Constants.PORT_CONTROL_HTTP = 2999` is used simultaneously by control channel, WebShare, PcRemote, and NSD**:
   - **CONFIRMED**:
     - `Constants.PORT_CONTROL_HTTP = 2999` at [`Constants.kt:4`](file:///d:/share/app/src/main/java/com/swiftshare/common/Constants.kt#L4).
     - `TransferControlChannel.kt:44`: binds `ServerSocket(Constants.PORT_CONTROL_HTTP)`.
     - `EmbeddedWebShareServer.kt:65`: default argument `port: Int = Constants.PORT_CONTROL_HTTP`.
     - `PcRemoteServer.kt:63`: default argument `port: Int = Constants.PORT_CONTROL_HTTP`.
     - `DeviceDiscoveryManager.kt:66-67`: registers NSD and broadcasts UDP packets advertising port 2999.
     Simultaneous execution leads to `java.net.BindException: Address already in use`.

4. **`HighSpeedDataChannel` does not check the session token and uses the remote file name directly in `File(downloadDir, name)`**:
   - **CONFIRMED**: In [`HighSpeedDataChannel.kt:89-127`](file:///d:/share/app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt#L89-L127), `receiveIncomingFile` parses raw header without reading or verifying any session token. At line 127, it executes `val targetFile = File(downloadDir, fileName)`, which permits path traversal (e.g., `../../filename`).

5. **`/api/upload` uses remote file name unsafely; `tokenValid` is returned but not enforced on WebShare/PC routes; CORS allows any host**:
   - **CONFIRMED**:
     - In [`EmbeddedWebShareServer.kt:70-75`](file:///d:/share/app/src/main/java/com/swiftshare/webshare/EmbeddedWebShareServer.kt#L70-L75), `install(CORS) { anyHost() }` allows arbitrary origins.
     - At line 117 (`/api/manifest`), `put("tokenValid", token == _activeSessionToken.value)` returns a boolean without rejecting unauthenticated requests. Routes `/api/download/{id}`, `/api/thumbnail/{id}`, `/api/download/all`, and `/api/upload` do not check tokens at all.
     - At line 205-206 (`/api/upload`), `val originalName = part.originalFileName` is directly passed to `val destination = File(uploadDir, originalName)` with no sanitization.

6. **`soTimeout` has no effect on blocking `SocketChannel` reads; `isCancelled` is a single shared flag; progress is a single shared state; files are sent sequentially although the README says 4 parallel streams**:
   - **CONFIRMED**:
     - In [`HighSpeedDataChannel.kt:56, 86`](file:///d:/share/app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt#L56), `socket().soTimeout` is set on NIO sockets, which is ignored during blocking `SocketChannel.read(ByteBuffer)` operations.
     - Line 35: `private var isCancelled = false` is a single global mutable boolean.
     - Line 37: `private val _transferProgress = MutableStateFlow(...)` is a single shared StateFlow.
     - In [`SwiftShareViewModel.kt:140-155`](file:///d:/share/app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt#L140-L155), `for (item in files)` iterates sequentially, sending one file at a time over a single connection.

7. **Resume offsets (`ChunkManager`) live only in memory and receiver never tells sender how many bytes it already has; SHA-256 helpers are never called**:
   - **CONFIRMED**:
     - In [`ChunkManager.kt:10`](file:///d:/share/app/src/main/java/com/swiftshare/nft/channel/ChunkManager.kt#L10), `resumeOffsets` is an in-memory `ConcurrentHashMap<String, Long>()`.
     - In [`HighSpeedDataChannel.kt:232`](file:///d:/share/app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt#L232), the sender reads its own memory map: `val offset = ChunkManager.getResumeOffset(...)`. The receiver never informs the sender over the wire of existing bytes.
     - `ChunkManager.verifySha256` is defined at line 38 but is never invoked anywhere in transfer routines.

8. **Receiving into `Environment.getExternalStoragePublicDirectory(DOWNLOADS)` with plain `File` fails on targetSdk 35 without `MANAGE_EXTERNAL_STORAGE`**:
   - **CONFIRMED**: In [`HighSpeedDataChannel.kt:41`](file:///d:/share/app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt#L41), default `downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftShare")`. On targetSdk 35, writing arbitrary files via plain `File` outside app-specific storage throws `IOException: Permission denied` unless `MANAGE_EXTERNAL_STORAGE` is granted by the system settings.

9. **Release signing passwords are hardcoded in `app/build.gradle.kts` and the `.jks` is git-ignored**:
   - **CONFIRMED**:
     - In [`app/build.gradle.kts:28-31`](file:///d:/share/app/build.gradle.kts#L28-L31): `storePassword = "swiftshare123"`, `keyPassword = "swiftshare123"` are written in plaintext.
     - In [`.gitignore:12`](file:///d:/share/.gitignore#L12): `*.jks` is ignored.

10. **`WifiP2pDiscovery.connectToDevice` is never called by the UI; `QrPayloadManager.autoConnectWifi` never unbinds the process network**:
    - **CONFIRMED**:
      - `WifiP2pDiscovery.connectToDevice` at [`WifiP2pDiscovery.kt:122`](file:///d:/share/app/src/main/java/com/swiftshare/nft/discovery/WifiP2pDiscovery.kt#L122) has 0 usages across all UI and ViewModel files.
      - In [`QrPayloadManager.kt:172`](file:///d:/share/app/src/main/java/com/swiftshare/nft/discovery/QrPayloadManager.kt#L172), `connectivityManager.bindProcessToNetwork(network)` is executed upon Wi-Fi connection, but `bindProcessToNetwork(null)` is never called anywhere upon transfer completion or cancellation, leaving the app process permanently bound to the hotspot network.
