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

### Upcoming Phase 2 Tasks
- **T2.2**: 4 Parallel Streams & Non-Blocking NIO with Real Socket Timeouts.
- **T2.3**: Resumable Transfer Protocol over Wire (receiver sends existing chunk offsets to sender) & SHA-256 verification.
- **T2.4**: TargetSdk 35 Scoped Storage & MediaStore SAF Fallback.
- **T2.5**: Wi-Fi Direct UI Orchestration (`WifiP2pDiscovery.connectToDevice`).

---

## Known Pre-existing Issues & Lint Audit

A full `./gradlew lintDebug` comparison was conducted between `main` (`fb870af`) and current working branch:
- **`main` baseline:** 8 errors, 157 warnings.
- **`feat/phase-2`:** 11 errors, 159 warnings.
- **Android Lint Scope:** Android Lint checks only production sources (`src/main`). Test sources (`src/test`, `src/androidTest`) are **not** analyzed by default unless `lint.checkTestSources = true` is explicitly configured.

### Error Classification & Runtime Crash Risk:
1. **Real Runtime Crash (Critical):**
   - `LocalHotspotManager.kt:38, 48, 88` (6 errors): Uses `WifiManager.startLocalOnlyHotspot(...)`, `LocalOnlyHotspotCallback`, and `LocalOnlyHotspotReservation`. These APIs require API 26 (Android 8.0), whereas `minSdk` is 24. Invoking hotspot on Android 7.0/7.1 (API 24-25) triggers an unhandled `NoSuchMethodError` runtime crash.
2. **Logic & Type Mismatch (Medium):**
   - `VideoToAudioConverter.kt:83` (1 error): Assigns `MediaExtractor.sampleFlags` directly to `MediaCodec.BufferInfo.flags` (`WrongConstant`). In practice `SAMPLE_FLAG_SYNC` matches `BUFFER_FLAG_KEY_FRAME` numerically, but other flags may drift.
3. **Backup / Security Policy (No direct runtime crash):**
   - `data_extraction_rules.xml:11-13` (3 errors): Excludes `vault/` and `safebox.db` under `<cloud-backup>` where only `sharedpref` is included (`FullBackupContent`).
   - `AndroidManifest.xml:66` (1 error): `READ_SMS` permission without explicit `android.hardware.telephony` feature tag required for ChromeOS / Play Store compatibility.

