# SwiftShare — High-Speed Cross-Platform P2P File Sharing Engine

**SwiftShare** is a full-featured, production-grade offline peer-to-peer (P2P) file transfer and device utility system modeled on the core architecture of SHAREit (`com.lenovo.anyshare.gps`), completely stripped of adware, bloatware, push notifications, and background telemetry. Modernized for **Android 10–15+**, with cross-platform Web and Desktop PC interoperability.

---

## 🏛️ System Architecture Layers

Reverse-engineered and modernized from SHAREit's core network stack:

| Architectural Layer | Corresponding SHAREit Package | SwiftShare Modernized Implementation |
|---|---|---|
| **Discovery & Handshake** | `com.ushareit.nft.discovery.*` | `com.swiftshare.nft.discovery` (Wi-Fi Direct `WifiP2pManager`, `LocalOnlyHotspot` SoftAP, Android `NsdManager` mDNS, UDP 55283 beacon, BLE Scanner/Advertiser, dynamic QR payload) |
| **Transport & Channel** | `com.ushareit.nft.channel.*` | `com.swiftshare.nft.channel` (NIO `FileChannel.transferTo()` zero-copy buffers on port 2998, bi-directional TCP/WebSocket framing on port 2999, resumable chunk offsets, SHA-256 verification, Split APK/OBB bundler) |
| **Session & Feature Modules** | `com.lenovo.anyshare.share.*` & `.pc.*` | `com.swiftshare.webshare`, `com.swiftshare.pc`, `com.swiftshare.groupshare`, `com.swiftshare.clone` (Phone-to-Browser WebShare, Desktop PC Suite with Remote View & PPT Remote, Multi-peer room multicast, 1-Click Phone Clone) |
| **Media & Storage Utilities** | `com.lenovo.anyshare.safebox`, `explorer` | `com.swiftshare.safebox`, `com.swiftshare.explorer`, `com.swiftshare.siplayer` (AES-256-GCM hardware vault, MediaStore 5-tab content picker, Storage Analyzer, Media3 ExoPlayer with caching proxy) |

---

## ⚡ Key Technical Highlights

1. **Zero-Copy High-Throughput Data Channel (Port 2998):**
   - Direct NIO `FileChannel.transferTo()` streaming straight to raw `SocketChannel` kernel buffers.
   - Saturates 5 GHz 802.11ac/ax Wi-Fi Direct links achieving transfer speeds up to 40–80 MB/s.
   - Resumable transfers via chunk tracking offsets and SHA-256 integrity verification.

2. **Zero-Click Multi-Mode Discovery (Port 2999 & 55283):**
   - **Receiver Mode (Group Owner / Hotspot):** Launches `WifiP2pManager.createGroup()` (5 GHz preferred) or fallback to `WifiManager.startLocalOnlyHotspot()`.
   - **Dynamic QR Code:** JSON framing encoding SSID, PSK, IP, Port, and Ephemeral Session Token.
   - **Sender Mode:** 60fps custom Canvas Radar Discovery View, CameraX + ML Kit Barcode scanning, and out-of-band BLE credential exchange.
   - **Same-LAN Wi-Fi Mode:** Automatic mDNS (`_swiftshare._tcp.`) and UDP broadcast on port 55283 when devices share an existing router.

3. **Embedded WebShare (Zero Install on Receiver):**
   - Embedded Ktor CIO HTTP Server serving self-contained HTML5 portal (`/webshare`).
   - Drag-and-drop uploads from PC/browser to phone, batch ZIP download, and `/apps/app-release.apk` zero-data invite landing page (`/invite`).

4. **Connect to PC & Presentation Remote:**
   - Desktop dashboard (`/pc`) with **Remote View** (photos, videos, music, documents, apps), **PlayTo** video streaming, and real-time **PPT Remote** (slide navigation + virtual laser pointer over WebSockets).

5. **SafeBox Encrypted Vault:**
   - AES-256-GCM encryption with keys secured in `AndroidKeyStore`.
   - Strips metadata and moves files out of MediaStore into isolated private storage.

6. **One-Click Phone Clone & Group Share:**
   - Full migration of Contacts (`ContactsContract` VCF streaming), Call Logs, Media, Documents, and non-system APK bundles.
   - Multi-peer Group Share portal with star-topology multicast distribution.

---

## 📂 Source Code Directory Structure

```
d:\share\
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle\
│   └── libs.versions.toml
├── app\
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src\main\
│       ├── AndroidManifest.xml
│       ├── assets\web\
│       │   ├── webshare.html
│       │   ├── pc\client.html
│       │   └── invite\home.html
│       ├── res\ (values, xml, drawable, mipmap)
│       └── java\com\swiftshare\
│           ├── SwiftShareApp.kt
│           ├── common\
│           │   ├── Constants.kt
│           │   ├── PermissionManager.kt
│           │   └── NetworkUtils.kt
│           ├── nft\
│           │   ├── discovery\ (WifiP2p, LocalHotspot, Nsd, Ble, QrPayload)
│           │   └── channel\ (ControlChannel, HighSpeedDataChannel, ChunkManager, SplitApkExtractor)
│           ├── webshare\EmbeddedWebShareServer.kt
│           ├── pc\PcRemoteServer.kt
│           ├── groupshare\GroupShareEngine.kt
│           ├── clone\PhoneCloneEngine.kt
│           ├── safebox\ (CryptoManager, SafeBoxVault)
│           ├── explorer\ (MediaStoreRepository, StorageAnalyzer)
│           ├── siplayer\ (SwiftMediaPlayer, LocalMediaCacheProxy)
│           ├── service\TransferForegroundService.kt
│           ├── data\ (Room DB, DAOs, Entities)
│           └── ui\ (MainActivity, Theme, Components, Screens)
```

---

## 🔒 Security & Privacy Guardrails

- **Zero Bloatware / Zero Adware:** Excluded all third-party ad networks, push tracking SDKs, accessibility-abuse auto-clickers, and shortcut injectors.
- **Cryptographic Session Security:** Generates ephemeral UUID session tokens verified on all control and transfer socket endpoints.
- **Scoped Android 10–15+ Permissions:** Explicitly handles `NEARBY_WIFI_DEVICES` (`neverForLocation`), `BLUETOOTH_SCAN`/`CONNECT`, and Android 13+ granular media permissions.
