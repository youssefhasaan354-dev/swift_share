# SwiftShare Two-Device Physical Manual Testing Guide

This manual testing guide specifies the mandatory physical test scenarios for validating SwiftShare across real Android hardware (minimum two devices: e.g. Phone A [Android 14+, TargetSdk 35] and Phone B [Android 10/11/12]).

---

## Pre-Test Setup & Prerequisites

1. **Build Artifacts**:
   - Build a debug APK via `./gradlew assembleDebug`.
   - Install `app-debug.apk` on both test devices:
     ```bash
     adb -s <device_A_serial> install -r app/build/outputs/apk/debug/app-debug.apk
     adb -s <device_B_serial> install -r app/build/outputs/apk/debug/app-debug.apk
     ```
2. **Test Payload Preparation**:
   - Small batch: 5 images/documents (~20 MB total).
   - Medium file: 1 video file (~100 MB).
   - Large file: 1 large video or dummy binary (~2 GB):
     ```bash
     # Generate a 2 GB dummy file on Phone A
     adb -s <device_A_serial> shell "dd if=/dev/urandom of=/sdcard/Download/test_2gb.bin bs=1M count=2048"
     ```
3. **Log Monitoring**:
   - On separate terminal windows, attach logcat filtered by `SwiftShare`:
     ```bash
     adb -s <device_A_serial> logcat -s SwiftShare DataChannel TransferService
     adb -s <device_B_serial> logcat -s SwiftShare DataChannel TransferService
     ```

---

## Scenario 1: Same Wi-Fi Network (LAN Transfer)

**Objective**: Verify discovery, session handshake, 4-stream data transfer, and SHA-256 verification over an existing Wi-Fi LAN router.

### Test Procedure:
1. Connect Phone A and Phone B to the same 5 GHz / 2.4 GHz Wi-Fi router.
2. Open SwiftShare on Phone B and tap **Receive** (enters Radar / Listening mode on port 2999).
3. Open SwiftShare on Phone A, select files (e.g., 5 photos, ~20 MB), and tap **Send**.
4. Phone A discovers Phone B via mDNS / UDP broadcast.
5. Tap Phone B on Phone A's radar screen.
6. **Expected Behavior**:
   - Phone B displays the incoming transfer prompt: *"Allow Phone A to send 5 files (20 MB)?"*.
   - Tap **Accept** on Phone B.
   - 4 parallel TCP connections are established on port 2998 (`SWSA` auth headers verified).
   - Progress bar updates smoothly on both devices.
   - Transfer finishes, async SHA-256 matches on receiver.
   - Temporary `.part` and `.part.meta` files are cleanly renamed/committed to public storage.
   - Files appear in Phone B's Gallery / Downloads directory immediately.

---

## Scenario 2: Wi-Fi Direct (P2P Handshake & Transfer)

**Objective**: Verify peer-to-peer Wi-Fi Direct connection without any external router or internet access.

### Test Procedure:
1. Turn off Wi-Fi on both devices or disconnect from any AP. Leave Wi-Fi radio and Location toggles ON.
2. On Phone B, tap **Receive** -> select **Wi-Fi Direct**.
3. On Phone A, select 100 MB video -> tap **Send** -> select **Wi-Fi Direct**.
4. Both devices begin P2P peer discovery. Phone A discovers Phone B's P2P device name.
5. Tap Phone B on Phone A to initiate Wi-Fi Direct group negotiation.
6. Accept the system P2P connection dialog on Phone B.
7. Once connected, Phone A resolves Phone B's Group Owner IP (`WifiP2pInfo.groupOwnerAddress.hostAddress`).
8. **Expected Behavior**:
   - Handshake initiates over port 2999.
   - 4 streams connect and deliver the 100 MB file.
   - Throughput should reach typical P2P Wi-Fi speeds (20-60 MB/s).
   - Complete transfer without socket timeout.
   - P2P group disconnects cleanly upon exit.

---

## Scenario 3: QR Code + Local-Only Hotspot (LOHS) Mode

**Objective**: Verify that when no Wi-Fi network exists, the receiver creates an Android Local-Only Hotspot (API 26+) and encodes the SSID/Password/IP in a QR code, which the sender scans and joins.

### Test Procedure:
1. Ensure both devices are disconnected from Wi-Fi.
2. On Phone B (Receiver), tap **Receive** -> **QR Code / Hotspot**.
3. Verify Phone B calls `startLocalOnlyHotspot()` (or falls back to Wi-Fi Direct on API < 26) and displays a QR code containing `SSID`, `PreSharedKey`, and `IP`.
4. On Phone A (Sender), select a file -> tap **Scan QR Code**.
5. Aim Phone A camera at Phone B screen.
6. Phone A decodes the QR payload and calls `WifiNetworkSpecifier` / process network binding.
7. **Expected Behavior**:
   - Phone A connects to Phone B's ephemeral hotspot without internet connectivity warning dialogs breaking the connection.
   - Transfer progresses over the hotspot network.
   - Upon completion or cancellation, Phone A unbinds process network (`bindProcessToNetwork(null)`).
   - Phone B closes hotspot reservation cleanly (`reservation.close()`).

---

## Scenario 4: Large File Transfer (2 GB+ Single File)

**Objective**: Verify memory stability, 64 KB chunk cycling, multi-stream load balancing, and 64-bit file offset handling without OOM or 32-bit integer overflow.

### Test Procedure:
1. Select the 2 GB dummy file (`test_2gb.bin`) on Phone A.
2. Connect to Phone B and start transfer.
3. Monitor memory consumption on both devices using Android Studio Profiler or `adb shell dumpsys meminfo com.swiftshare`.
4. **Expected Behavior**:
   - Memory usage remains constant and flat (JVM heap < 128 MB), proving streams stream chunks directly to disk using `Os.pwrite` / random-access channel rather than buffering in RAM.
   - All 4 streams stay saturated and balanced.
   - File offsets exceed `2^31 - 1` (2,147,483,647 bytes) correctly without integer rollover.
   - Final SHA-256 computation over 2 GB matches the source file exactly:
     ```bash
     adb -s <device_A_serial> shell "sha256sum /sdcard/Download/test_2gb.bin"
     adb -s <device_B_serial> shell "sha256sum /sdcard/Download/test_2gb.bin"
     ```

---

## Scenario 5: Airplane-Mode / Network Interruption & Resumable Transfer (T2.3)

**Objective**: Validate that a mid-transfer network drop does NOT delete the `.part` file, preserves `.part.meta`, and successfully resumes missing chunks on reconnection.

### Test Procedure:
1. Start transferring a 100 MB file from Phone A to Phone B.
2. At ~40% progress, toggle **Airplane Mode ON** on Phone A (or abruptly disable Wi-Fi).
3. **Expected Behavior on Disconnect**:
   - Both devices detect socket disconnect within the socket timeout window.
   - Receiver logs: *"Stream error; keeping partial file and writing .part.meta"*.
   - Verify on Phone B storage:
     - `<filename>.part` EXISTS on disk.
     - `<filename>.part.meta` EXISTS on disk with chunk bitmap.
     - Neither file was deleted!
4. Re-enable Wi-Fi and reconnect Phone A and Phone B.
5. Re-initiate transfer of the exact same file from Phone A.
6. **Expected Behavior on Resume**:
   - Receiver inspects existing `.part.meta` and transmits `FileResumeRequest` containing the completed-chunk bitmap to Phone A.
   - Phone A skips chunks 0 to ~40% and transmits only the remaining chunks (~60%).
   - Phone B writes incoming chunks into the existing `.part` file at their true positional offsets.
   - Transfer finishes in significantly less time than a full transfer.
   - Final SHA-256 calculation passes, `.part.meta` is removed, and final file is valid.

---

## Scenario 6: App Process Killed Mid-Transfer & Recovery

**Objective**: Verify resilience against OS process death (e.g. system low memory killer or user swipe-kill).

### Test Procedure:
1. Start transferring a large file.
2. At 50% progress, force-kill the SwiftShare app on Phone B (Receiver):
   ```bash
   adb -s <device_B_serial> shell "am force-stop com.swiftshare"
   ```
3. Verify on Phone A that sender terminates gracefully without infinite hanging or socket leak.
4. Relaunch SwiftShare on Phone B.
5. Check Transfer History / Downloads folder:
   - Partial file `<filename>.part` and `<filename>.part.meta` remain intact on disk.
   - Relaunching the transfer resumes from the on-disk state.

---

## Scenario 7: OS-Specific Scoped Storage Verification (Android 10 vs 14)

**Objective**: Verify targetSdk 35 Scoped Storage compliance, MediaStore `IS_PENDING` handling, and SAF fallback.

### Device Matrix:
- **Device 1**: Android 10 (API 29) or Android 11 (API 30)
- **Device 2**: Android 14 (API 34) or Android 15 (API 35)

### Test Procedure:
1. Receive images, audio, video, and documents on Android 14:
   - Images -> `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` (`Pictures/SwiftShare`)
   - Video -> `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` (`Movies/SwiftShare`)
   - Audio -> `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` (`Music/SwiftShare`)
   - Documents/Binaries -> `MediaStore.Downloads.EXTERNAL_CONTENT_URI` (`Download/SwiftShare`)
2. Verify that while transferring, MediaStore entry has `IS_PENDING = 1` (other apps cannot see incomplete partial files).
3. Upon transfer completion and SHA-256 match, `IS_PENDING` is updated to `0`.
4. Open system Files app and Gallery app: verify the file immediately appears without requiring device reboot.
5. Repeat on Android 10: verify legacy external storage or MediaStore works without permission denied errors.

---

## Pass/Fail Verification Checklist

| Scenario | Criteria | Pass / Fail |
|----------|----------|-------------|
| **1. LAN Transfer** | 4 parallel streams saturate Wi-Fi, SHA-256 matches, 0 leftover temp files | [ ] |
| **2. Wi-Fi Direct** | Automatic P2P group negotiation, GO IP resolved, transfer completes | [ ] |
| **3. QR + LOHS** | Hotspot config scanned via QR, process bound to network, clean unbind on exit | [ ] |
| **4. 2 GB File** | Flat memory profile (<128 MB RAM), 64-bit offsets pass, SHA-256 matches | [ ] |
| **5. Resume (T2.3)**| Interrupted transfer keeps `.part` + `.meta`, second run transfers < 70% | [ ] |
| **6. App Killed** | Process death leaves no corrupted destination file, resumable on restart | [ ] |
| **7. Scoped Storage**| `IS_PENDING=1` hides partial file; `IS_PENDING=0` indexes file in Gallery | [ ] |
