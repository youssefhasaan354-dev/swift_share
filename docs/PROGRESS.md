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
- **Commit**: *(pending T1.4 commit)*

---

## Next Task
- **NEXT**: T1.5 (Release signing from `key.properties` / env vars with debug-signing fallback; remove hardcoded passwords from `build.gradle.kts`; enable R8 with keep rules; verify `assembleRelease`).
