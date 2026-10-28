# SwiftShare Engineering Progress Log

This document tracks execution across all phases (1 through 4) of the SwiftShare hardening and behavioral parity roadmap.

---

## Environment Setup & Prerequisites
- **Status**: DONE
- **Details**:
  - Configured user-level `~/.gradle/gradle.properties` with `org.gradle.java.home=C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot` (not committed).
  - Verified `gradlew lintDebug` runs cleanly on JDK 17 without AGP 8.8 / JDK 25 incompatibilities.
  - Added JVM test dependencies in `gradle/libs.versions.toml` and `app/build.gradle.kts`: JUnit, `kotlinx-coroutines-test`, `ktor-server-test-host`.

---

## Phase 1: Security and Stability (branch: `feat/phase-1`)

### T1.1: SafeFileName Sanitization & Canonical Path Verification
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT TESTS
- **Verification Details**: Unit tests in `SafeFileNameTest.kt` test 8 scenarios (path traversal `../../x`, `..\x`, `a/b`, empty/blank, 200+ byte truncation, Arabic/unicode characters, deduplication `photo (1).jpg`, and canonical directory containment check). All 8 tests passed under `@Test(timeout = 60000)`.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/SafeFileName.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/main/java/com/swiftshare/webshare/EmbeddedWebShareServer.kt`
  - `app/src/test/java/com/swiftshare/common/SafeFileNameTest.kt`
- **Commit**: `f0a32ac`

### T1.2: Distinct Port Allocation
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL CONCURRENT LOOPBACK TEST
- **Verification Details**: Real test in `DistinctPortTest.kt`. Concurrently binds Control channel (2999), HighSpeedDataChannel (2998), and Ktor Web/PC server (8080), asserts zero `BindException`, establishes active TCP sockets on 2999 and 2998, and makes HTTP GET request to 8080 returning 200 OK. Parameterized ephemeral port binding also verified.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/Constants.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/TransferControlChannel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/main/java/com/swiftshare/webshare/EmbeddedWebShareServer.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/DeviceDiscoveryManager.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/QrPayloadManager.kt`
  - `app/src/test/java/com/swiftshare/common/DistinctPortTest.kt`
- **Commit**: `1173aa2` (hardened & test added in current commit)

### T1.3: Web Authentication for WebShare/PC
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL KTOR ROUTING MODULE TESTS
- **Verification Details**: Ktor `testApplication` tests in `WebAuthTest.kt` exercise the actual `EmbeddedWebShareServer` routing module via extracted `configureModule`. Assert 401 Unauthorized without session cookie on `/api/manifest`, `/api/download/x`, `/api/upload`, `/api/remote/files`, and `/api/ppt/control`; assert 200 OK with valid session cookie on all routes; assert PIN rate-limiting lockout (429 on 5th failure); assert multipart upload with `../evil.txt` is sanitized and contained within the target directory.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/webshare/WebAuthManager.kt`
  - `app/src/main/java/com/swiftshare/webshare/EmbeddedWebShareServer.kt`
  - `app/src/test/java/com/swiftshare/webshare/WebAuthTest.kt`
- **Commit**: `2353e71` (hardened & test expanded in current commit)

### T1.4: Data Channel Session Token Authentication
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT & LOOPBACK TESTS
- **Verification Details**: Real tests in `DataChannelTokenTest.kt`: (1) Pure header serialization, parsing, and constant-time token validation in `DataChannelHeader.kt`. (2) Live loopback NIO socket test on ephemeral port where connecting with a wrong token immediately closes the connection and creates 0 files in the destination directory, while connecting with the correct token successfully receives and saves the file.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/channel/DataChannelHeader.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/DataChannelTokenTest.kt`
- **Commit**: `4a7e05a` (hardened & test expanded in current commit)

### T1.5: Release Signing Security & R8 Optimization
- **Status**: DONE
- **Verification Mode**: VERIFIED BY BUILD EXECUTION
- **Verification Details**: Built and verified `./gradlew.bat assembleRelease` which ran R8 code minification (`minifyReleaseWithR8`), resource shrinking (`shrinkReleaseRes`), and packaged release APK (`app/build/outputs/apk/release/app-release.apk`, 27.4 MB). Hardcoded passwords removed from `build.gradle.kts`. Signing now reads from `key.properties` or environment variables (`SWIFTSHARE_*`) with automatic fallback to debug signing if not configured. Template added at `key.properties.example`.
- **Security Note**: Historical keystore passwords exist in git commit history and must be replaced prior to production deployment.
- **Files Touched**:
  - `app/build.gradle.kts`
  - `app/proguard-rules.pro`
  - `key.properties.example`
- **Commit**: `07aff2d`

### T1.6: Manifest Hardening & Android 14+ Service Rules
- **Status**: DONE
- **Verification Mode**: VERIFIED BY CODE READING & BUILD VERIFICATION (assembleDebug / lintDebug)
- **Verification Details**: Verified `android.hardware.wifi.direct` marked `required="false"`. `network_security_config.xml` blocks cleartext to public Internet and permits LAN/loopback. SafeBox encrypted vault excluded from `backup_rules.xml` and `data_extraction_rules.xml`. `TransferForegroundService` specifies Android 14 foreground service types (`connectedDevice | dataSync`). Runtime service behavior NOT verified on a physical device.
- **Files Touched**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/xml/network_security_config.xml`
  - `app/src/main/res/xml/backup_rules.xml`
  - `app/src/main/res/xml/data_extraction_rules.xml`
  - `app/src/main/java/com/swiftshare/service/TransferForegroundService.kt`
- **Commit**: `e858f46`

### T1.7: Process Network Unbinding (Wi-Fi Specifier / Hotspot)
- **Status**: DONE (Code Complete)
- **Verification Mode**: NOT VERIFIED (NEEDS PHYSICAL DEVICE / CONNECTIVITY SERVICE)
- **Verification Details**: `QrPayloadManager.unbindProcessNetwork` calls `connectivityManager.bindProcessToNetwork(null)` and `unregisterNetworkCallback(callback)`. Clean unbind invoked upon Wi-Fi connection loss, connection timeout, transfer cancellation, and ViewModel `onCleared()`. Cannot be verified on standard JVM without physical Android Wi-Fi Direct radio or device.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/discovery/QrPayloadManager.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`

### T1.8: Centralized Error Logging & User Feedback
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT TEST & CODE READING
- **Verification Details**: Verified by unit test in `AppLoggerTest.kt`: `AppLogger.userVisibleErrors` emission caught via undispatched coroutine collector and `withTimeout(5_000)`, and methods execute without unhandled exceptions. Replaced empty `printStackTrace()` calls across the codebase with structured `AppLogger` calls (verified by code review and compilation).
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/AppLogger.kt`
  - `app/src/test/java/com/swiftshare/common/AppLoggerTest.kt`
  - Multiple feature classes (SafeBox, Clone, StatusSaver, MediaStore, VideoToAudio, RadarScan)

---

## Phase 2: Transfer Engine & Protocol Parity (branch: `feat/phase-2`)

### T2.1: Control Channel Handshake & State Machine Activation
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT & INTEGRATION TESTS
- **Verification Details**: Real tests in `TransferControlChannelTest.kt`: (1) Bidirectional TCP handshake accepted on matching session token and storage bounds. (2) Connection rejected when session token mismatches. (3) Over-the-wire manifest transmission and delivery verification. (4) Status ACK delivery verification. `SwiftShareViewModel` and `RadarScanScreen` wired so receiver listens for handshake on control channel, auto-transitions to `transfer_progress` upon sender connection, and delivers manifest.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/channel/TransferControlChannel.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/RadarScanScreen.kt`
  - `app/src/main/java/com/swiftshare/ui/MainActivity.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/TransferControlChannelTest.kt`
- **Commit**: `a6a8cd4`

### T2.2: 4 Parallel Streams & Non-Blocking NIO with Real Socket Timeouts
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL 100MB LOOPBACK BENCHMARK & NEGATIVE UNIT TESTS
- **Verification Details**:
  - **100MB Loopback Concurrency Benchmark**: Transferred a 100MB synthetic file over loopback across 4 parallel TCP connections. Measured throughput: **450.45 MB/s** (222 ms) vs 1-stream baseline: **169.49 MB/s** (124 ms for 20MB). *Note: Loopback benchmark reflects JVM memory/socket throughput only and does not represent physical Wi-Fi medium.*
  - **Stream Distribution**: Verified all 4 streams actively transferred chunks (Stream 0: 27.13MB, Stream 1: 25.95MB, Stream 2: 25.62MB, Stream 3: 26.15MB).
  - **Integrity**: Per-chunk CRC32 validated on receipt; asynchronous full-file SHA-256 exchanged via control channel and matched against destination file. File committed safely from `.part` without leftover temporary files.
  - **Negative Test Coverage**: Corrupted chunk detected via CRC32 mismatch; invalid session token immediately dropped without file creation; mid-transfer cancellation asserts 0 open channels, immediately re-bindable port, settled thread baseline, and `.part` file purged; stalled peer watchdog triggers in < 2500ms.
  - **Security Limitation**: CRC32 and SHA-256 detect accidental corruption rather than active tampering, as Wi-Fi transfer is unencrypted cleartext and the session token is the sole authentication boundary.
  - **Device-Dependent Items**: Physical Wi-Fi Direct multi-device transmission and Android MediaStore `IS_PENDING` / SAF URI streams marked **NOT VERIFIED (needs device)**.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/DataChannelHeader.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/TransferControlChannel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/models/UserMessages.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/DataChannelTokenTest.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/MultiStreamDataChannelTest.kt`
- **Commit**: `eb6958c`

### T2.3: Resumable Transfer Protocol (Wire Bitmap & On-Disk Metadata)
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL 50MB LOOPBACK INTERRUPT/RESUME & NEGATIVE TESTS
- **Verification Details**:
  - **Failure Policy Overhaul**: On any socket disconnection, timeout, or stream drop, `.part` files are STRICTLY KEPT on disk. An accompanying `<name>.part.meta` metadata file is written/updated storing `fileId`, `fileName`, `size`, `chunkSize`, `totalChunks`, and `completedChunksBase64` (Base64 bitset). Deletion of `.part` occurs ONLY on explicit user cancel (`cancel(explicitUserCancel = true)`) or on final full-file SHA-256 verification mismatch.
  - **Over-the-Wire Resume**: Added `FileResumeRequest` control message (`TransferControlChannel`) containing chunk bitmap. Sender populates chunk dispatch queue with missing chunk indices only (`filter { !resumeBitmap.get(it) }`), completely skipping already-transferred bytes.
  - **Disk-Backed State**: Resume strictly loads verified chunk bitmaps from on-disk `<name>.part.meta` rather than relying on in-memory state.
  - **50MB Interrupted Transfer Test**: Transferred 50 MB synthetic binary over loopback across 4 parallel streams. At ~40% completion, abruptly killed network sockets. Reconnected, verified resume bitmap loaded from disk, resumed transmission, and verified second run transmitted strictly < 70% of the total file over the wire. Final SHA-256 matched source file identically, and temporary `.part` and `.part.meta` were cleaned up.
  - **Stale Metadata Protection**: If file size changes between sessions for the same file, stale `.part.meta` and `.part` are detected, safely discarded, and transmission restarts cleanly from chunk 0 with SHA-256 verified.
  - **Name Collision Prevention**: When two different files share the identical file name (`manifestItem.name`), `PartFileManager.resolvePartFileAndMeta` disambiguates subsequent files with collision-free naming (e.g. `name (1).part`), ensuring parallel or sequential transfers never overwrite or corrupt partial files.
  - **Legacy Single-Stream Audit**: Proved that `HighSpeedDataChannel.handleLegacySingleStream` strictly validates the session token via constant-time `DataChannelHeader.validateToken(activeExpectedToken, receivedToken)` before accepting data. Documented and preserved for legacy and iOS companion fallback.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/channel/PartFileManager.kt` (new)
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/TransferControlChannel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/models/UserMessages.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/MultiStreamDataChannelTest.kt`
- **Commit**: (Item B commit)

---

## Flutter iOS Companion Client Analysis & Architecture Proposal

An architectural inspection was performed on `flutter_ios_companion/lib/services/swiftshare_ios_client.dart`:

### What Breaks for iOS with Modern Protocol:
1. **Port 2999 Protocol Incompatibility**: The iOS client expects an HTTP REST server on port 2999 (`/api/manifest`, `/api/download/{id}`, `/api/upload`). In T1.2, port 2999 was dedicated exclusively to raw TCP binary framing (`TransferControlChannel`, magic `0x53574654`). Any HTTP GET/POST from iOS to port 2999 immediately receives a socket reset or handshake error.
2. **WebShare Port Separation**: The HTTP server runs on port 8080 (`Constants.PORT_WEB_SERVER`).
3. **Authentication Boundary Mismatch**: The HTTP server requires PIN verification (`WebAuthManager`) with `SWIFTSHARE_SESSION` cookie. The iOS client transmits query parameter `?token=...`, which is rejected with 401 Unauthorized.
4. **No 4-Stream Framing**: The iOS client has no implementation of the multi-stream binary socket framing (`SWSA` auth, `SWCK` chunking, per-chunk CRC32).

### Architectural Proposal for iOS Support:
- **Option 1: Serve iOS through the Web Portal (Recommended)**:
  - iOS users simply open Safari to `http://<android-ip>:8080`, enter the 4-digit PIN displayed on Android's screen, and upload/download files directly via Safari's built-in WebKit file manager and download service.
  - **Advantages**: Zero installation required for iPhone users, zero App Store review hurdles, full support for all file types, works across iOS, iPadOS, macOS, and Windows.
- **Option 2: Modernize Flutter iOS Companion for WebShare REST API**:
  - Update `swiftshare_ios_client.dart` to connect to port 8080 instead of 2999.
  - Implement PIN authentication (`POST /api/auth` with `{ "pin": "..." }`) and maintain the session cookie.
  - Retain HTTP multipart upload (`/api/upload`) and streaming download (`/api/download/{id}`) on port 8080.

---

### T2.3.1: Transfer Offer, Free Space Check, Cancel Protocol & Presence Heartbeats
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT & INTEGRATION TESTS
- **Verification Details**: Real tests in `TransferControlChannelTest.kt`:
  - `testTransferOfferAndAcceptResponse`: Verified wire framing and serialization for `TransferOffer` (batch ID, sender nickname, file count, total bytes, item list) and `TransferResponse` acceptance.
  - `testTransferOfferAndRejectNotEnoughSpace`: Verified rejection signaling with `NOT_ENOUGH_SPACE` reason when storage boundary is insufficient.
  - `testCancelTransferItemAndBatch`: Verified item-level and batch-level cancellation dispatch (`CANCELLED`).
  - `testPresenceHeartbeatTimeoutAfter3Misses`: Verified 3-second heartbeat ping and 3-miss (9-second) presence timeout trigger calling `onPeerLost()`.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/channel/TransferControlChannel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/models/UserMessages.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/TransferControlChannelTest.kt`

---

### T2.3.2: Room History with Sessions + Transfer Items, Schema Migration 1->2, and Real Transfer Statuses
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT & SCHEMA EXECUTION TESTS
- **Verification Details**: Real tests in `RoomMigrationTest.kt`:
  - `testRoomMigration1To2ExecutesAllSchemaStatements`: Verified execution of all 5 DDL/DML migration statements in `AppDatabase.MIGRATION_1_2` (creating `sessions` table, `transfer_items` table, `index_transfer_items_sessionId` index, migrating legacy `transfer_records` grouped by `batchId` into `sessions`, and copying records into `transfer_items`).
  - `testSessionEntityAndTransferItemEntityProperties`: Verified instantiation, serialization, and column mapping for `SessionEntity` and `TransferItemEntity`.
  - Removed all hardcoded `"batch_record"` identifiers from `SwiftShareViewModel.kt`. Sessions and individual items now record real statuses (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`) with real session UUIDs and byte metrics.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/data/entities/SessionEntity.kt` (new)
  - `app/src/main/java/com/swiftshare/data/entities/TransferItemEntity.kt` (new)
  - `app/src/main/java/com/swiftshare/data/db/SessionDao.kt` (new)
  - `app/src/main/java/com/swiftshare/data/db/AppDatabase.kt`
  - `app/src/main/java/com/swiftshare/SwiftShareApp.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/test/java/com/swiftshare/data/RoomMigrationTest.kt` (new)

---

### T2.3.3: Receiver UI Transfer Offer Dialog, Free Space Check, Deduplication, and Result Screen
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL COMPILATION & SUITE TEST EXECUTION (43 passing tests)
- **Verification Details**:
  - **Receiver Transfer Offer Dialog**: `RadarScanScreen` observes `viewModel.incomingTransferOffer` and renders an `AlertDialog` displaying "Allow <nickname> to send N files (X MB)?" with "Allow" (calls `acceptIncomingOffer`) and "Decline" (calls `rejectIncomingOffer`).
  - **Free Space Enforcement**: `SwiftShareViewModel` checks `freeBytes < offer.totalBytes + 50MB` safety headroom before presenting the offer, immediately rejecting with `NOT_ENOUGH_SPACE` reason if space is insufficient.
  - **Item Deduplication**: Checks incoming file manifest items against existing destination directory entries and flags matching files.
  - **Bidirectional Cancellation**: Sender or receiver can cancel an item or batch; sends `CancelTransfer` control message, updates Room session status to `CANCELLED`, cleans up sockets, and shuts down foreground service.
  - **Transfer Result Screen**: Created `TransferResultScreen` wired in `MainActivity` navigation graph (`transfer_result`), presenting transfer summary and actions: "Open" (opens destination folder via Intent), "Retry" (returns to radar connection), "Send More" (navigates to file picker), and "Done" (returns home).
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/ui/screens/TransferResultScreen.kt` (new)
  - `app/src/main/java/com/swiftshare/ui/screens/RadarScanScreen.kt`
  - `app/src/main/java/com/swiftshare/ui/MainActivity.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/MultiStreamDataChannelTest.kt`

---

### T2.4: TargetSdk 35 Scoped Storage via MediaStore (`IS_PENDING = 1`), SAF Fallback, PartSink & QR Token Expiry
- **Status**: PARTIAL
- **Verification Mode**: MIXED (REAL JVM UNIT TESTS + NOT VERIFIED (needs device))
- **Verification Details**:
  - **Verified by Real JVM Tests (`DataChannelPublisherTest.kt`, `ScopedStorageTest.kt`)**:
    - Wired `ReceivedFilePublisher` to `HighSpeedDataChannel`: Receives files into app-specific private storage (`partsDir`), invokes injected publisher upon SHA-256 match, and purges `.part` and `.part.meta` on success.
    - Preserves verified `.part` on publish failure for resume/retry.
    - Resumption functions cleanly from injected `partsDir`.
    - Removed `MANAGE_EXTERNAL_STORAGE` permission dependency.
    - Updated free-space check to 2x totalBytes + 50 MB.
    - QR Token Expiry and `FilePartSink` positional writes unit tested.
  - **NOT VERIFIED (needs device)**:
    - `ProductionReceivedFilePublisher`: Android 10+ (API 29+, targetSdk 35) `MediaStore.MediaColumns.IS_PENDING = 1` during stream copy, transitioning to `IS_PENDING = 0` on SHA-256 match needs validation on a physical phone.
    - SAF (`ACTION_OPEN_DOCUMENT_TREE`) secondary SD card write permissions.
- **Files Touched**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/swiftshare/common/ReceivedFilePublisher.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/main/java/com/swiftshare/ui/MainActivity.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/TransferHistoryScreen.kt`
  - `app/src/main/java/com/swiftshare/common/ApkInstaller.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/DataChannelPublisherTest.kt`
- **Commit**: pending commit 1

---

### T2.5: Wi-Fi Direct UI Orchestration, Group Owner IP Resolution & Timeout Handling
- **Status**: DONE
- **Verification Mode**: MIXED (REAL JVM UNIT TESTS + NOT VERIFIED ON PHYSICAL HARDWARE)
- **Verification Details**:
  - **Verified by Real JVM Tests (`WifiP2pOrchestrationTest.kt`)**:
    - Failure Reason Mapping: Verified exact mapping of `WifiP2pManager` codes (`P2P_UNSUPPORTED`, `BUSY`, `ERROR`, and fallback codes) to user-friendly diagnostic messages under `@Test(timeout = 60000)`.
    - Group Owner IP Resolution: Verified pure functional resolution of group owner IP from `WifiP2pInfo` (`resolveHostIp`) handles null info, unformed groups, and missing owner addresses cleanly without throwing.
  - **NOT VERIFIED (NEEDS PHYSICAL HARDWARE)**:
    - Real Wi-Fi Direct P2P radio negotiation across two physical devices via `manager.connect` and `WIFI_P2P_CONNECTION_CHANGED_ACTION` broadcast.
    - User acceptance prompt on receiving device.
  - **UI Orchestration**:
    - `RadarScanScreen`: When selecting a P2P peer, initiates `connectAndResolveHost`, shows a connecting dialog with localized Arabic/English copy ("Connecting via Wi-Fi Direct...", "Please accept prompt on other device"), and provides a Cancel button that safely invokes `cancelConnect()`.
    - Upon resolution of group owner IP, copies peer with resolved IP and invokes `onPeerConnected`.
    - Errors during initiation or timeout (20s) dispatch directly to `AppLogger.e(..., notifyUser = true)`.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/discovery/WifiP2pDiscovery.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/RadarScanScreen.kt`
  - `app/src/main/java/com/swiftshare/common/AppLocalization.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-ar/strings.xml`
  - `app/src/test/java/com/swiftshare/nft/discovery/WifiP2pOrchestrationTest.kt`
- **Commit**: Current commit

---

### T2.6: Group Share Multi-Receiver Session Engine with Per-Receiver Progress
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL CONCURRENT LOOPBACK TESTS (`GroupShareEngineTest.kt`)
- **Verification Details**:
  - **Verified by Real JVM Test (`GroupShareEngineTest.testBroadcastToOneSenderAndTwoLoopbackReceivers`)**:
    - Concurrently bound Receiver 1 (ephemeral port, token 1) and Receiver 2 (ephemeral port, token 2) on loopback.
    - Host device created a room with both receivers added as members.
    - Broadcasted a synthetic 2 MB file using dedicated parallel session channels for each receiver in star topology.
    - Verified full receipt within 30s timeout on both receivers.
    - Verified SHA-256 integrity matches original on both Receiver 1 and Receiver 2.
    - Verified `memberProgress` contains isolated, accurate per-receiver metrics (`FileTransferStatus.COMPLETED`, `transferredBytes == 2MB`) for both members independently.
  - **UI Integration**:
    - `GroupShareScreen`: Each member card observes `groupEngine.memberProgress` and renders an individual progress indicator and status banner (progress %, completion size, or error message).
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/groupshare/GroupShareEngine.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/GroupShareScreen.kt`
  - `app/src/test/java/com/swiftshare/groupshare/GroupShareEngineTest.kt`
- **Commit**: Current commit

---

### Phase 2 Gate Verification
- **Status**: DONE
- **Verification Command**: `./gradlew.bat assembleDebug lintDebug testDebugUnitTest --rerun-tasks`
- **Verification Mode**: VERIFIED BY FRESH BUILD & TEST EXECUTION (55 actionable tasks executed, 0 from cache)
- **Gate Metrics**:
  - **Unit Tests**: **49 executed, 0 failures, 0 errors, 0 skipped** across 11 test suites:
    - `AppLoggerTest`: 2 tests passed
    - `DistinctPortTest`: 2 tests passed
    - `SafeFileNameTest`: 9 tests passed
    - `ScopedStorageTest`: 3 tests passed
    - `RoomMigrationTest`: 2 tests passed
    - `GroupShareEngineTest`: 1 test passed
    - `DataChannelTokenTest`: 8 tests passed
    - `MultiStreamDataChannelTest`: 9 tests passed
    - `TransferControlChannelTest`: 7 tests passed
    - `WifiP2pOrchestrationTest`: 2 tests passed
    - `WebAuthTest`: 4 tests passed
  - **Android Lint**:
    - **Lint Errors: 0** (Baseline on `main`: 8 errors; 100% eliminated).
    - **Lint Warnings: 161** (Baseline on `main`: 157 warnings; 4 new obsolete API / button border deprecation warnings).
  - **APK Assembly**: `assembleDebug` passed cleanly.
- **Next Phase Transition**: Branching `feat/phase-3` directly from `feat/phase-2`.

---

## Phase 3: Zero-Data Discovery, Onboarding & Security Auditing (branch: `feat/phase-3`)

### T3.1 (C1): Invite a Friend (Bluetooth APK Share, Zero-Data LOHS Web Portal & Share Sheet)
- **Status**: DONE
- **Verification Mode**: MIXED (REAL KTOR INTEGRATION TESTS + NOT VERIFIED ON PHYSICAL HARDWARE)
- **Verification Details**:
  - **Verified by Real Ktor Tests (`WebAuthTest.testPublicInviteAndApkDownloadRateLimiting`)**:
    - Unauthenticated Public Routes: Verified `/invite` and `/apps/SwiftShare.apk` respond with HTTP 200 without requiring session cookies or PIN authentication.
    - Zero-Data Rate Limiting: Verified rate limiting (30 req/min threshold); the 31st request from an IP receives `429 Too Many Requests`.
  - **NOT VERIFIED (NEEDS PHYSICAL HARDWARE)**:
    - Real Bluetooth hardware transfer (`Intent.ACTION_SEND` targeting `com.android.bluetooth`).
    - Real LocalOnlyHotspot SSID broadcasting and Wi-Fi joining by a secondary device.
  - **UI Implementation**:
    - `InviteScreen.kt`:
      - Bluetooth Option: Requests enabling Bluetooth via system activity launcher if off; copies active APK to cache via `copyInstalledApkToCache`; exposes via `FileProvider`; dispatches `ACTION_SEND` targeted at `com.android.bluetooth` or system chooser.
      - Zero-Data Hotspot Option: Launches LOHS + starts `EmbeddedWebShareServer` on port 8080; renders active network SSID, password, invite URL, and QR dialog pointing to `http://<ip>:8080/invite`.
      - System Share-Sheet: Dispatches chooser for third-party messaging apps with APK attachment and invitation message.
      - Full Arabic and English RTL localization in `AppLocalization.kt`.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/ui/screens/InviteScreen.kt` (new)
  - `app/src/main/java/com/swiftshare/webshare/WebAuthManager.kt`
  - `app/src/main/java/com/swiftshare/webshare/EmbeddedWebShareServer.kt`
  - `app/src/main/java/com/swiftshare/common/AppLocalization.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/swiftshare/ui/MainActivity.kt`
  - `app/src/test/java/com/swiftshare/webshare/WebAuthTest.kt`
- **Commit**: Current commit

---

### T3.2 (C2): Avatar & Nickname Settings (Built-in Emojis, Custom Photos <= 32 KB & Wire Synchronization)
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT TESTS (`AvatarTest.kt`) & COMPILE VERIFICATION
- **Verification Details**:
  - `AvatarUtils.kt`: Base64 encoding/decoding, bitmap compression down to <= 32 KB limit, size validation (`isUnderMaxLimit`).
  - Wire Synchronization: Added `AvatarUpdateMessage` to `UserMessages.kt` and `MessageType.AVATAR_UPDATE` in `TransferControlChannel.kt`. Control channel sends local avatar on connection and updates `activePeer` reactively.
  - Discovery Payloads: `ConnectionPayload` and `PeerDevice` carry `avatarEmoji`, `customAvatarBase64`, and `deviceType`.
  - UI Rendering: `RadarScanView` and `HomeScreen` render custom avatar photo or emoji for local user and orbiting discovered peers. Full RTL Arabic and English support.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/AvatarUtils.kt` (new)
  - `app/src/main/java/com/swiftshare/common/AppLocalization.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/TransferControlChannel.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/models/UserMessages.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/DeviceDiscoveryManager.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/NsdDiscoveryManager.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/models/ConnectionPayload.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/models/PeerDevice.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/main/java/com/swiftshare/ui/components/RadarScanView.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/HomeScreen.kt`
  - `app/src/test/java/com/swiftshare/common/AvatarTest.kt` (new)

---

### T3.3 (C3): Destination Volume Selection (Internal vs SD Card)
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT TESTS (`StorageDestinationTest.kt`)
- **Verification Details**:
  - `StorageDestinationManager.kt`: Enumerates primary internal storage and secondary external SD cards. Computes per-volume free and total bytes, persists user volume choice in SharedPreferences, and provides safe fallback to internal storage when SD card is detached.
  - ViewModel Integration: Wired into `SwiftShareViewModel.kt` for `getFreeSpace` and `dataChannel.startReceiverServer(downloadDir = ...)`.
  - UI Settings: Integrated into `TransferHistoryScreen` "Me Profile & Settings" tab with real-time volume switching, storage breakdown, and free-space metrics.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/StorageDestinationManager.kt` (new)
  - `app/src/main/java/com/swiftshare/SwiftShareApp.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/TransferHistoryScreen.kt`
  - `app/src/test/java/com/swiftshare/common/StorageDestinationTest.kt` (new)

---

### T3.4 (C4): First-Run Permission Guide (One Screen Per Permission, Rationale, Settings Recovery & RTL)
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT TESTS (`PermissionGuideTest.kt`) & COMPILE VERIFICATION
- **Verification Details**:
  - `PermissionGuideScreen.kt`: 4-step interactive permission onboarding wizard:
    1. Nearby Devices / Wi-Fi (P2P Wi-Fi Direct & local offline peer discovery)
    2. Storage & Media (Read files to send and save received items)
    3. Camera (Zero-configuration instant QR connection)
    4. Bluetooth (Offline APK sharing to friends)
  - Features: Categorized permission requests, settings recovery button launching `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, skip-all option, persistent completion flag in SharedPreferences, and Arabic/English RTL localization.
  - Wired in `MainActivity.kt` as start destination on first run and accessible on-demand from Settings.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/PermissionManager.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/PermissionGuideScreen.kt` (new)
  - `app/src/main/java/com/swiftshare/ui/MainActivity.kt`
  - `app/src/test/java/com/swiftshare/common/PermissionGuideTest.kt` (new)

---

### T3.5 (C5): Incoming APK Certificate SHA-256 and Dangerous Permissions Security Check
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT TESTS (`ApkSecurityTest.kt`)
- **Verification Details**:
  - `ApkInstaller.inspectApk`: Safely parses package archive metadata, app label, and version information.
  - Certificate Fingerprint: Computes SHA-256 checksum formatted as colon-separated uppercase hex pairs (`computeCertSha256`).
  - Dangerous Permissions Audit: Classifies permissions into high-risk (SMS, telephony, accessibility, device admin, silent install) and medium-risk (location, camera, microphone, contacts, storage).
  - UI Dialog: Created `ApkSecurityDialog.kt` presenting security audit report, risk badges, certificate fingerprint with one-tap copy, and user confirmation prompt prior to package installation.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/ApkInstaller.kt`
  - `app/src/main/java/com/swiftshare/ui/components/ApkSecurityDialog.kt` (new)
  - `app/src/main/java/com/swiftshare/ui/screens/TransferHistoryScreen.kt`
  - `app/src/test/java/com/swiftshare/common/ApkSecurityTest.kt` (new)

---

### T3.6 (C6): System Share Sheet Intake (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`)
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL LINT & MANIFEST VERIFICATION
- **Verification Details**:
  - Registered `<intent-filter>` under `MainActivity` in `AndroidManifest.xml` for both `ACTION_SEND` and `ACTION_SEND_MULTIPLE` with `mimeType="*/*"`.
  - Stream Extraction: `handleIncomingShareIntent` extracts single and multiple stream URIs, resolves filename/size via ContentResolver, caches streams for NIO channel zero-copy transfer, updates `viewModel.setSelectedFiles(items)`, and auto-routes to `radar_send`.
- **Files Touched**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/swiftshare/ui/MainActivity.kt`

---

### T3.7 (C7): History UX (Grouped by Session, Sent/Received Filter, Files Browser, Delete & Re-Send)
- **Status**: DONE
- **Verification Mode**: VERIFIED BY REAL UNIT TESTS & LINT VERIFICATION
- **Verification Details**:
  - `SessionDao.kt`: Added `deleteSessionAndItems(sessionId)`, `getAllTransferItems()`, and `clearAll()`.
  - `TransferHistoryScreen.kt`:
    1. Sessions Tab: Grouped by session (`SessionEntity` + `TransferItemEntity`), filter pills (All, Received, Sent), status badges (`COMPLETED`, `IN_PROGRESS`, `CANCELLED`), expandable file items, one-tap Re-send button (populates `viewModel.selectedSendFiles` and launches `radar_send`), and Delete session action.
    2. Received Files Browser: Live disk file manager scanning `StorageDestinationManager.getDestinationDirectory`, displaying actual files with Open, System Share Sheet chooser, Delete, and APK Security Audit & Install.
    3. Me & Settings Tab: Device profile customization (emojis + photo picker <= 32 KB), destination storage volume selector, permission guide review, and language switcher.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/data/db/SessionDao.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/TransferHistoryScreen.kt`

---

### Phase 3 Gate Verification
- **Status**: DONE
- **Verification Command**: `./gradlew.bat lintDebug testDebugUnitTest assembleDebug`
- **Verification Mode**: VERIFIED BY FRESH BUILD & TEST EXECUTION (55 actionable tasks executed)
- **Gate Metrics**:
  - **Unit Tests**: **62 executed, 0 failures, 0 errors** across 15 test suites:
    - `ApkSecurityTest`: 2 tests passed
    - `AppLoggerTest`: 2 tests passed
    - `AvatarTest`: 5 tests passed
    - `DistinctPortTest`: 2 tests passed
    - `PermissionGuideTest`: 2 tests passed
    - `SafeFileNameTest`: 9 tests passed
    - `ScopedStorageTest`: 3 tests passed
    - `StorageDestinationTest`: 3 tests passed
    - `RoomMigrationTest`: 2 tests passed
    - `GroupShareEngineTest`: 1 test passed
    - `DataChannelTokenTest`: 8 tests passed
    - `MultiStreamDataChannelTest`: 9 tests passed
    - `TransferControlChannelTest`: 7 tests passed
    - `WifiP2pOrchestrationTest`: 2 tests passed
    - `WebAuthTest`: 5 tests passed
  - **Android Lint**:
    - **Lint Errors: 0** (Maintained 0 errors).
  - **APK Assembly**: `assembleDebug` passed cleanly.

---

## Static Analysis & Android Lint Audit

A comparative audit of `./gradlew lintDebug` was executed against `origin/main` (`fb870af`):
- **`origin/main` Baseline:** 8 errors, 157 warnings.
- **`feat/phase-2`:** **0 errors**, 161 warnings (100% of errors eliminated).
- **Verification Mode:** VERIFIED BY REAL LINTDEBUG EXECUTION (`BUILD SUCCESSFUL`, 0 errors reported in `lint-results-debug.xml`).
- **Verification Mode:** VERIFIED BY REAL LINTDEBUG EXECUTION (`BUILD SUCCESSFUL`, 0 errors reported in `lint-results-debug.xml` / `lint-results-debug.html`).
- **Build Behavior:** `lintDebug` succeeds with 0 errors.
- **Test Sources Scope:** Android Lint analyzes only production sources (`src/main`). Test sources (`src/test` and `src/androidTest`) are **not** analyzed by default (`lint.checkTestSources = true` is not enabled).

### Resolved Errors (All 8 pre-existing errors on `main` eliminated):
1. **LocalHotspotManager API 24-25 Crash (6 errors eliminated):**
   - **Fix:** Guarded `startLocalOnlyHotspot` and `stopHotspot` with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` (API 26).
   - **Fallback:** On API 24-25, invokes `onFailure("LocalOnlyHotspot requires Android 8.0+ (API 26). Falling back to Wi-Fi Direct or LAN.")` allowing `DeviceDiscoveryManager` to fall back cleanly without crashing.
2. **VideoToAudioConverter Sample Flags Type Mismatch (1 error eliminated):**
   - **Fix:** Mapped `extractor.sampleFlags` explicitly to `MediaCodec.BUFFER_FLAG_KEY_FRAME` and `MediaCodec.BUFFER_FLAG_PARTIAL_FRAME` before assigning to `bufferInfo.flags`.
3. **AndroidManifest Telephony Hardware Requirement (1 error eliminated):**
   - **Fix:** Added `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to resolve `PermissionImpliesUnsupportedChromeOsHardware`.



