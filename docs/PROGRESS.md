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
- **Verification**: Unit tests (`SafeFileNameTest.kt`) with 8 scenarios (path traversal `../../x`, `..\x`, `a/b`, empty/blank, 200+ byte truncation, Arabic/unicode characters, trailing dots/spaces, canonical directory containment check). `testDebugUnitTest` passed.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/SafeFileName.kt`
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/main/java/com/swiftshare/webshare/EmbeddedWebShareServer.kt`
  - `app/src/test/java/com/swiftshare/common/SafeFileNameTest.kt`
- **Commit**: `f0a32ac`

### T1.2: Distinct Port Allocation
- **Status**: DONE
- **Verification**: Code analysis, build verification, and unified port mappings: control TCP 2999, data HTTP 2998, WebShare+PC on one shared Ktor server at 8080. NSD/UDP beacon and QR payloads updated. No port collision on 2999.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/common/Constants.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/DeviceDiscoveryManager.kt`
  - `app/src/main/java/com/swiftshare/nft/discovery/QrPayloadManager.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/WebShareScreen.kt`
  - `app/src/main/java/com/swiftshare/ui/screens/PcConnectScreen.kt`
- **Commit**: `1173aa2`

### T1.3: Web Authentication for WebShare/PC
- **Status**: DONE
- **Verification**: Ktor `testApplication` unit tests in `WebAuthTest.kt` (401 without cookie, 200 with authenticated session cookie, PIN rate-limiting lockout after 5 attempts for 60 seconds, upload sanitization).
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/webshare/WebAuthManager.kt`
  - `app/src/main/java/com/swiftshare/webshare/EmbeddedWebShareServer.kt`
  - `app/src/main/java/com/swiftshare/pcremote/PcRemoteServer.kt`
  - `app/src/test/java/com/swiftshare/webshare/WebAuthTest.kt`
- **Commit**: `2353e71`

### T1.4: Data Channel Session Token Authentication
- **Status**: DONE
- **Verification**: Unit tests in `DataChannelTokenTest.kt` verifying `SWFT` magic header layout, session token extraction, and constant-time token comparison with `MessageDigest.isEqual`. Connections with missing or mismatched tokens are rejected and closed before opening/creating any destination file.
- **Files Touched**:
  - `app/src/main/java/com/swiftshare/nft/channel/HighSpeedDataChannel.kt`
  - `app/src/main/java/com/swiftshare/groupshare/GroupShareEngine.kt`
  - `app/src/main/java/com/swiftshare/ui/SwiftShareViewModel.kt`
  - `app/src/test/java/com/swiftshare/nft/channel/DataChannelTokenTest.kt`
- **Commit**: `4a7e05a`

### T1.5: Release Signing Security & R8 Optimization
- **Status**: DONE
- **Verification**: Built and verified `./gradlew.bat assembleRelease` which ran R8 code minification (`minifyReleaseWithR8`), resource shrinking (`shrinkReleaseRes`), and packaged release APK (`app/build/outputs/apk/release/app-release.apk`, 27.4 MB). Hardcoded passwords removed from `build.gradle.kts`. Signing now reads from `key.properties` or environment variables (`SWIFTSHARE_*`) with automatic fallback to debug signing if not configured. Template added at `key.properties.example`.
- **Note**: The legacy password `swiftshare123` exists in git history and must be rotated for production distribution.
- **Files Touched**:
  - `app/build.gradle.kts`
  - `app/proguard-rules.pro`
  - `key.properties.example`
- **Commit**: `07aff2d`

### T1.6: Manifest Hardening & Android 14+ Service Rules
- **Status**: DONE
- **Verification**: Built and verified `./gradlew.bat testDebugUnitTest` and `assembleDebug`. Verified `android.hardware.wifi.direct` marked `required="false"`. `network_security_config.xml` configured to block cleartext to public Internet and allow only loopback and private LAN endpoints. SafeBox encrypted vault (`files/vault/`) excluded from `backup_rules.xml` and `data_extraction_rules.xml`. `TransferForegroundService` updated with `connectedDevice | dataSync` types and safe Android 14+ background start exception handling.
- **Files Touched**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/xml/network_security_config.xml`
  - `app/src/main/res/xml/backup_rules.xml`
  - `app/src/main/res/xml/data_extraction_rules.xml`
  - `app/src/main/java/com/swiftshare/service/TransferForegroundService.kt`
- **Commit**: *(pending T1.6 commit)*

---

## Next Task
- **NEXT**: T1.7 (Network unbind: after a transfer ends or is cancelled call `bindProcessToNetwork(null)` and `unregisterNetworkCallback`).
