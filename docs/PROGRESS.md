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
- **Status**: IN PROGRESS
- **Architectural Decisions**:
  - **(a) Timeouts & Watchdog**: `soTimeout` (15s) protects reads; an active watchdog tracks `lastProgressTimestamp` for both reads/writes and forcibly closes the socket after timeout (injectable `watchdogTimeoutMs`) to unblock hanging threads.
  - **(b) File Completion**: Received chunks tracked via synchronized `BitSet` rejecting duplicate chunks. Completion requires all chunk bits set + full-file SHA-256 match; `isEof` header flag only denotes stream finish.
  - **(c) Checksum & Limitation**: Receiver waits for async `FileChecksumMessage` on control channel after last chunk; fails closed (deletes `.part`) if timed out or mismatched. Limitation: CRC32/SHA-256 detect corruption, not active tampering, since transport is cleartext and session token is the sole auth boundary.
  - **(d) Defensive Header Bounds**: Rejects `offset < 0`, `offset % 64KB != 0`, `offset >= fileSize`, `length != expectedLength`, or unknown `fileId`. Enforced with boundary tests (negative, Long.MAX_VALUE, offset == size).
  - **(e) Chunk Distribution & Failure Policy**: Shared thread-safe queue across 4 streams; workers start pulling only after all 4 streams authenticate so every stream carries bytes. 5th connection rejected. Failure policy v1: any stream failure aborts the entire transfer cleanly.
  - **(f) Storage Writing & Reading**: Direct `FileChannel` positional writes (`RandomAccessFile.channel.write`) verified on JVM. MediaStore (`IS_PENDING`) and SAF `ParcelFileDescriptor` (`Os.pwrite`) marked "NOT VERIFIED (needs device)". Sender reads from `FileChannel` / `ParcelFileDescriptor`.
  - **(g) TransferManifest & Safe Paths**: `FileManifestItem` holds `fileId` and `size`. `.part` and target files use `SafeFileName` deduplication without overwriting. Any write `IOException` cleanly aborts and deletes `.part`.
  - **(h) Leak Prevention**: Injected channel registry asserts all channels `isOpen == false`, port is immediately re-bindable, and active thread count returns to baseline within 5s polling.
  - **(i) Throughput & Watchdog Test**: 100MB synthetic loopback benchmarks 1-stream vs 4-stream speed (noting loopback != Wi-Fi). Watchdog test asserts stalled peer causes sender failure in `< watchdogTimeoutMs`.

### Upcoming Phase 2 Tasks
- **T2.3**: Resumable Transfer Protocol over Wire (receiver sends existing chunk offsets to sender) & SHA-256 verification.
- **T2.4**: TargetSdk 35 Scoped Storage & MediaStore SAF Fallback.
- **T2.5**: Wi-Fi Direct UI Orchestration (`WifiP2pDiscovery.connectToDevice`).

---

## Static Analysis & Android Lint Audit

A comparative audit of `./gradlew lintDebug` was executed against `origin/main` (`fb870af`):
- **`origin/main` Baseline:** 8 errors, 157 warnings.
- **`feat/phase-2` (Current):** 8 errors, 157 warnings (0 new errors, 0 new warnings).
- **Build Behavior:** `lintDebug` succeeds (`BUILD SUCCESSFUL`) despite 8 errors because `lint.abortOnError = false` is configured in `app/build.gradle.kts`.
- **Test Sources Scope:** Android Lint analyzes only production sources (`src/main`). Test sources (`src/test` and `src/androidTest`) are **not** analyzed by default (`lint.checkTestSources = true` is not enabled).

### Error Classification:
1. **Pre-existing on `main` (8 errors):**
   - **Real Runtime Crash (API 24-25):** `LocalHotspotManager.kt:38, 48, 88` (6 errors): `WifiManager.startLocalOnlyHotspot(...)` requires API 26 (Android 8.0). On Android 7.0/7.1 (API 24-25, within `minSdk = 24`), calling hotspot immediately crashes with unhandled `NoSuchMethodError`.
   - **Type Mismatch:** `VideoToAudioConverter.kt:83` (1 error): Direct assignment of `MediaExtractor.sampleFlags` to `MediaCodec.BufferInfo.flags` (`WrongConstant`).
   - **Policy Requirement:** `AndroidManifest.xml:66` (1 error): `READ_SMS` permission implies telephony hardware without `<uses-feature android:name="android.hardware.telephony" required="false">` tag (`PermissionImpliesUnsupportedChromeOsHardware`).
2. **Introduced on our branch and fixed (3 errors):**
   - `data_extraction_rules.xml:11-13` (3 errors): In commit `e858f46`, `<include domain="root"/>` was added alongside `<exclude>` rules for `file` and `database`, triggering `FullBackupContent` errors.
   - **Resolution:** Removed the rogue `<include domain="root"/>` in `<device-transfer>`. Both `vault/` and `safebox.db` are strictly excluded across API 31+ (`data_extraction_rules.xml`) and API 24-30 (`backup_rules.xml`), completely eliminating all 3 errors.
   - **Warning Fix:** Removed obsolete `SDK_INT >= M` checks in `QrPayloadManager.kt` (`minSdk = 24`), eliminating 2 `ObsoleteSdkInt` warnings.


