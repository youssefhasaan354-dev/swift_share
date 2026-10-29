# SwiftShare Manual Device Testing Checklist
# قائمة الفحص والاختبار اليدوي على الأجهزة الحقيقية

This document defines the comprehensive manual verification protocol for SwiftShare across two physical Android test devices.

**Recommended Test Matrix**:
- **Device A (Sender / Modern)**: Android 13+ (API 33-35), TargetSDK 35, Scoped Storage enforced.
- **Device B (Receiver / Transition)**: Android 10 (API 29) or Android 11-12, Testing MediaStore transitions.

---

## 1. Multi-Stream Parallel Transfer (4 Streams / النقل المتوازي بـ 4 مسارات)

### Objective / الهدف
Verify that the NIO multi-stream engine establishes 4 concurrent TCP connections, balances file chunks across streams, and achieves maximum Wi-Fi throughput without socket exhaustion or buffer deadlocks.

### Test Procedure / خطوات الاختبار
1. On Device A (Sender), select a batch of files:
   - 1 Large video file (>= 500 MB)
   - 10 Medium photos/documents (5-10 MB each)
   - 50 Small thumbnail files (< 100 KB each)
2. Tap **Send** to enter discovery.
3. On Device B (Receiver), tap **Receive**.
4. Connect via QR code or Radar scan.
5. Monitor transfer UI and observe throughput metrics.

### Checkpoints / معايير التحقق
- [ ] **Stream Saturation**: HighSpeedDataChannel opens 4 concurrent worker sockets on port 2998.
- [ ] **Transfer Speed**: High sustained transfer rate (typical Wi-Fi 5/6: 25-60+ MB/s) without sudden stall.
- [ ] **Chunk Assembly**: Chunks are assembled sequentially or out-of-order in the receiver `.part` file without data corruption.
- [ ] **Hash Verification**: End-of-transfer SHA-256 matches exactly on both devices.
- [ ] **Transfer Complete State**: UI moves to completion state; notification displays final transfer summary.

---

## 2. Network Interruption & Resume (استئناف النقل بعد انقطاع الاتصال)

### Objective / الهدف
Verify that when Wi-Fi is toggled off or out-of-range during active transmission, the transfer state persists in `.part.meta`, the sender pauses, and resuming does not retransmit already verified chunks.

### Test Procedure / خطوات الاختبار
1. Start transferring a large video file (>= 1 GB) from Device A to Device B.
2. When progress reaches approximately 40%, toggle **Wi-Fi OFF** on Device A (or toggle Airplane Mode).
3. Observe transfer screen on both devices:
   - Verify connection drop is caught within watchdog timeout (default: 8-10 seconds).
   - Verify transfer status changes to `PAUSED` or `FAILED (Connection lost)`.
4. Re-enable **Wi-Fi** on Device A and reconnect both devices to the same network (or re-establish hotspot).
5. Tap **Resume** on Device A.

### Checkpoints / معايير التحقق
- [ ] **Ledger Persistence**: Receiver inspects `<file>.part.meta` and returns the bitmap of already received chunks.
- [ ] **Zero Over-Transmission**: Transfer immediately resumes from ~40%; the first 40% is NOT resent over the wire.
- [ ] **Integrity Guarantee**: Final file size and SHA-256 match the source file upon 100% completion.
- [ ] **Temporary Cleanup**: The `.part` and `.part.meta` files in `partsDir` are deleted immediately following successful publication.

---

## 3. MediaStore & Scoped Storage Publication (الاستقبال عبر MediaStore والتخزين المنعزل)

### Objective / الهدف
Verify that incoming files on Android 10+ are published directly to the Android MediaStore via `ContentResolver` (`IS_PENDING=1` -> stream copy -> `IS_PENDING=0`) without requiring `MANAGE_EXTERNAL_STORAGE`.

### Test Procedure / خطوات الاختبار
1. Ensure SwiftShare on Device B (Android 10 or 13+) has only standard storage permissions (no `All Files Access` / `MANAGE_EXTERNAL_STORAGE`).
2. Send a `.jpg` photo, an `.mp4` video, and an `.apk` installer to Device B.
3. Once transfer completes, open the system **Gallery** / **Photos** app and system **Downloads** app on Device B.

### Checkpoints / معايير التحقق
- [ ] **No Permission Crash**: Transfer succeeds without security exceptions or permission denials.
- [ ] **Immediate Gallery Indexing**: Photos and videos appear immediately in the native Android Gallery without requiring a device reboot or media scanner rescan.
- [ ] **Downloads Visibility**: Document and APK files appear under public `Downloads/SwiftShare/`.
- [ ] **Pending State Cleared**: Files are not locked in `IS_PENDING=1`; other applications can open them without permission error.
- [ ] **Temp File Deletion**: Temporary `.part` file in `context.getExternalFilesDir()` is safely unlinked.

---

## 4. Wi-Fi Direct & Hotspot Orchestration (الاتصال المباشر ونقطة الاتصال)

### Objective / الهدف
Verify peer-to-peer group formation using Android `WifiP2pManager`, autonomous Group Owner (GO) negotiation, IP address assignment (`192.168.49.1`), and automatic teardown upon transfer completion.

### Test Procedure / خطوات الاختبار
1. Ensure both devices are disconnected from any home/office Wi-Fi router.
2. Device A taps **Send** -> Device B taps **Receive**.
3. Choose Wi-Fi Direct connection mode.
4. Accept connection prompt on the target device.
5. Complete transfer, then exit transfer screen.

### Checkpoints / معايير التحقق
- [ ] **Group Owner Handshake**: Group forms within 5-10 seconds; GO negotiates IP `192.168.49.1` and Client receives `192.168.49.x`.
- [ ] **Process Network Binding**: Network traffic routes directly through the Wi-Fi Direct network interface (`bindProcessToNetwork`).
- [ ] **Fallback Mechanism**: If Wi-Fi Direct fails (e.g. timeout or hardware incompatibility), app prompts or automatically falls back to `LocalOnlyHotspot`.
- [ ] **Proper Unbinding**: When transfer finishes or screen exits, `ConnectivityManager.unbindProcessFromNetwork()` is called and Wi-Fi Direct group is removed (`removeGroup`). Normal Wi-Fi restores.

---

## 5. CameraX QR Code Pairing (مسح رمز الاستجابة السريعة بالكاميرا)

### Objective / الهدف
Verify seamless pairing via dynamic QR code using CameraX barcode analysis, autofocus under diverse lighting conditions, and auto-joining the sender's network.

### Test Procedure / خطوات الاختبار
1. On Device A (Sender), generate QR code containing SSID, passphrase, IP, and control port.
2. On Device B (Receiver), open QR Scanner tab.
3. Point Device B's camera at Device A's screen from various distances (10 cm to 50 cm) and angles.

### Checkpoints / معايير التحقق
- [ ] **Camera Preview**: CameraX lifecycle binds cleanly without black screen or frame freezes.
- [ ] **Rapid Focus & Decode**: QR code decodes within < 500 ms upon entering camera viewfinder.
- [ ] **Auto Wi-Fi Association**: Device B extracts Wi-Fi credentials and automatically joins Device A's hotspot without requiring manual Wi-Fi settings intervention.
- [ ] **Torch / Flashlight Toggle**: Flashlight button toggles camera torch reliably in low-light environments.

---

## 6. SafeBox Vault & Biometric Authentication (الخزنة المشفرة والبصمة)

### Objective / الهدف
Verify ChaCha20-Poly1305 / AES-GCM file encryption, BiometricPrompt authentication with PIN fallback, in-memory volatile media viewing, and secure file shredding.

### Test Procedure / خطوات الاختبار
1. Open **SafeBox** screen on Device A.
2. Set up initial 4-digit PIN and security recovery question.
3. Import a sensitive photo (`test_private.jpg`) into the vault:
   - SafeBox encrypts file into `.safebox/<uuid>.vault`.
   - SafeBox shreds original `test_private.jpg` from Gallery/Disk.
4. Exit SafeBox, lock device, and reopen SafeBox.
5. Authenticate via Fingerprint / Face Unlock (or PIN fallback).
6. Tap the encrypted photo to open in-memory viewer.
7. Test Forgot PIN recovery question.

### Checkpoints / معايير التحقق
- [ ] **Biometric Prompt**: `BiometricPrompt` triggers properly and unlocks vault upon valid fingerprint.
- [ ] **Vault Isolation**: Encrypted file has `.vault` extension; cannot be viewed by third-party gallery apps.
- [ ] **Original File Shredding**: The original photo is deleted from the public storage path.
- [ ] **Volatile Memory Viewing**: Photo decodes directly to RAM; no unencrypted temporary file is created on disk during preview.
- [ ] **Recovery Flow**: Answering recovery question resets PIN without data loss.

> [!NOTE]
> **Flash Storage Shredding Limitation**: On modern Android devices with solid-state NAND flash (UFS/eMMC), software zero-overwrite (`secureShred`) overwrites logical file blocks, but hardware Flash Translation Layer (FTL) wear-leveling may retain physical flash pages until garbage collection or TRIM runs.

---

## 7. PC Web Dashboard & Local HTTP Sharing (لوحة تحكم الويب للكمبيوتر)

### Objective / الهدف
Verify the embedded Ktor HTTP server (`http://<phone-ip>:8080/webshare`), PIN authentication, rate-limiting lockout, and browser-to-phone bidirectional sharing.

### Test Procedure / خطوات الاختبار
1. On Device A, tap **WebShare / PC Remote**.
2. Note displayed IP and 6-digit session PIN.
3. Open Google Chrome, Firefox, or Safari on a PC connected to the same Wi-Fi.
4. Navigate to `http://<phone-ip>:8080/webshare`.
5. Enter PIN and interact with dashboard:
   - Download individual files.
   - Upload files from PC to phone.
6. Test brute-force security: enter incorrect PIN 5 times consecutively.

### Checkpoints / معايير التحقق
- [ ] **PIN Authentication**: Protected routes reject unauthenticated requests with HTTP 401.
- [ ] **Brute-Force Lockout**: After 5 invalid PIN attempts, IP is locked out for 60 seconds with HTTP 429.
- [ ] **File Browsing**: Storage categories (Images, Videos, Audio, Documents) render accurately on web browser.
- [ ] **PC Upload**: Uploading a file from PC writes correctly to phone's public Downloads directory.

---

## 8. ZIP Streaming & Batch Download (تنزيل الحزم بصيغة ZIP)

### Objective / الهدف
Verify on-the-fly streaming ZIP compression from phone to PC browser without high memory consumption or OutOfMemoryError on large folders.

### Test Procedure / خطوات الاختبار
1. In the PC Web Dashboard, navigate to an album or folder containing multiple files (totaling >= 300 MB).
2. Click **Download All as ZIP** (`/api/zip`).
3. Save and extract the resulting `.zip` file on PC using Windows Explorer or 7-Zip.

### Checkpoints / معايير التحقق
- [ ] **Streaming Response**: ZIP download starts immediately (`Content-Type: application/zip`) without buffering the entire archive in phone RAM.
- [ ] **Phone Stability**: Phone memory usage remains stable during streaming.
- [ ] **Archive Integrity**: Extracted ZIP contains all files with intact names, timestamps, and identical sizes.

---

## 9. Zip-Slip Path Traversal Protection (الحماية من ثغرات Zip-Slip)

### Objective / الهدف
Verify that extracting a malicious ZIP archive containing relative path sequences (`../../etc/`) or absolute paths is strictly intercepted and blocked by `ZipExplorer`.

### Test Procedure / خطوات الاختبار
1. Prepare or transfer a test ZIP file containing an entry named `../../malicious.txt`.
2. Open **ZIP Explorer** inside SwiftShare.
3. Attempt to extract the archive to a destination folder.

### Checkpoints / معايير التحقق
- [ ] **Path Traversal Trap**: Extraction halts immediately with a `SecurityException` / validation error.
- [ ] **No File Escape**: No file is created outside the designated target folder.
- [ ] **UI Notification**: SwiftShare displays an error toast: "Extraction blocked: detected invalid file path".

---

## 10. Summary Verification Sign-off Table

| Test Suite | Device A (Android 13+) | Device B (Android 10) | Notes & Observations | Sign-off |
|---|---|---|---|---|
| **1. Multi-Stream 4-Path** | [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | Check throughput MB/s | ________ |
| **2. Interrupted Resume** | [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | Check .part & .part.meta | ________ |
| **3. MediaStore Publishing**| [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | Verify Gallery instant display| ________ |
| **4. Wi-Fi Direct / Hotspot**| [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | P2P group negotiation | ________ |
| **5. CameraX QR Scanner** | [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | Optical autofocus & parse | ________ |
| **6. SafeBox & Biometrics** | [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | Biometric prompt & shred | ________ |
| **7. PC Web Dashboard** | [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | PIN auth & upload | ________ |
| **8. ZIP Streaming** | [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | HTTP streaming zip | ________ |
| **9. Zip-Slip Defense** | [ ] Pass / [ ] Fail | [ ] Pass / [ ] Fail | Path escape blocked | ________ |
