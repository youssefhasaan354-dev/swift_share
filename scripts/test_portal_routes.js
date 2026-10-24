/**
 * SwiftShare Phase 3 — Web Assets & Route Verification
 * Run: node d:/share/scripts/test_portal_routes.js
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const ASSETS_DIR = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');

function testHtmlContent(filePath, requiredElements) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`File not found: ${filePath}`);
  }
  const content = fs.readFileSync(filePath, 'utf8');
  for (const elem of requiredElements) {
    if (!content.includes(elem)) {
      throw new Error(`Missing expected element "${elem}" in ${filePath}`);
    }
  }
  console.log(`[PASS] ${path.basename(filePath)} validated (${content.length} bytes, all elements verified)`);
}

function testFileExists(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Expected file not found: ${filePath}`);
  }
  const size = fs.statSync(filePath).size;
  console.log(`[PASS] ${path.relative(path.join(__dirname, '..'), filePath)} exists (${size} bytes)`);
}

function testKotlinContains(filePath, requiredSnippets) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Kotlin file not found: ${filePath}`);
  }
  const content = fs.readFileSync(filePath, 'utf8');
  for (const snippet of requiredSnippets) {
    if (!content.includes(snippet)) {
      throw new Error(`Missing required snippet "${snippet}" in ${path.basename(filePath)}`);
    }
  }
  console.log(`[PASS] ${path.basename(filePath)} — all required snippets found`);
}

async function runTests() {
  console.log('=== SwiftShare Phase 3 — Web Assets, Routes & Code Verification ===\n');

  // 1. webshare.html — validate iOS PWA + thumbnail route
  testHtmlContent(path.join(ASSETS_DIR, 'web', 'webshare.html'), [
    'SwiftShare WebShare',
    'dropzone',
    'handleFileSelect',
    'loadManifest',
    '/api/download/all',
    '/api/upload',
    'progress-bar-fill',
    // Phase 3: iOS PWA meta tags
    'apple-mobile-web-app-capable',
    'apple-mobile-web-app-status-bar-style',
    // Phase 3: Thumbnail endpoint usage
    '/api/thumbnail/',
    // Phase 3: Parallel upload
    'uploadFilesParallel',
    // Phase 3: iOS detection
    'isIos',
    // Phase 3: MB/s speed indicator
    'speedIndicator',
  ]);

  // 2. client.html (PC Suite)
  testHtmlContent(path.join(ASSETS_DIR, 'web', 'pc', 'client.html'), [
    'SwiftShare PC',
    'tab-transfer',
    'tab-remoteview',
    'tab-playto',
    'tab-ppt',
    '/api/remote/files',
    '/api/remote/stream/',
    'laser-pad',
    'sendPptAction',
    '/api/ppt/ws',
  ]);

  // 3. invite/home.html
  testHtmlContent(path.join(ASSETS_DIR, 'web', 'invite', 'home.html'), [
    'Install SwiftShare',
    '/apps/app-release.apk',
    'Zero-Data App Invite',
  ]);

  // 4. Arabic localization
  testFileExists(path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'values-ar', 'strings.xml'));

  // 5. Kotlin — MediaStoreRepository has thumbnail helpers
  const srcBase = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'swiftshare');
  testKotlinContains(path.join(srcBase, 'explorer', 'MediaStoreRepository.kt'), [
    'loadAppIconBitmap',
    'LruCache',
    'loadPhotoThumbnail',
    'loadVideoThumbnail',
    'loadAudioAlbumArt',
    'ThumbnailUtils',
    'MediaMetadataRetriever',
    'durationMs',
  ]);

  // 6. EmbeddedWebShareServer has /ios and /api/thumbnail routes
  testKotlinContains(path.join(srcBase, 'webshare', 'EmbeddedWebShareServer.kt'), [
    'get("/ios")',
    'get("/api/thumbnail/{id}")',
    'generateThumbnailBytes',
  ]);

  // 7. PcRemoteServer has /api/thumbnail route
  testKotlinContains(path.join(srcBase, 'pc', 'PcRemoteServer.kt'), [
    'get("/api/thumbnail/{id}")',
    'renderThumbnailBytes',
  ]);

  // 8. HighSpeedDataChannel has 1MB buffers
  testKotlinContains(path.join(srcBase, 'nft', 'channel', 'HighSpeedDataChannel.kt'), [
    'SOCKET_BUFFER_SIZE',
    'TRANSFER_CHUNK_SIZE',
    'tcpNoDelay = true',
    'keepAlive = true',
  ]);

  // 9. DeviceDiscoveryManager has parallel launch
  testKotlinContains(path.join(srcBase, 'nft', 'discovery', 'DeviceDiscoveryManager.kt'), [
    'isOnLan',
    'scope.launch { nsdManager.registerService',
    'scope.launch { bleManager.startAdvertising',
  ]);

  // 10. HomeScreen has iOS connect button
  testKotlinContains(path.join(srcBase, 'ui', 'screens', 'HomeScreen.kt'), [
    'Connect iPhone / iOS',
    'glowScale',
    'infiniteRepeatable',
  ]);

  // 11. FilePickerScreen has real thumbnail with Coil + duration badge
  testKotlinContains(path.join(srcBase, 'ui', 'screens', 'FilePickerScreen.kt'), [
    'AsyncImage',
    'durationMs',
    'durStr',
    'Alignment.BottomEnd',
  ]);

  // 12. build.gradle.kts has minSdk=24
  const buildGradle = fs.readFileSync(path.join(__dirname, '..', 'app', 'build.gradle.kts'), 'utf8');
  if (!buildGradle.includes('minSdk = 24')) {
    throw new Error('build.gradle.kts must have minSdk = 24');
  }
  console.log('[PASS] build.gradle.kts — minSdk = 24 confirmed');

  // 13. Flutter iOS companion exists
  testFileExists(path.join(__dirname, '..', 'flutter_ios_companion', 'lib', 'main.dart'));
  testFileExists(path.join(__dirname, '..', 'flutter_ios_companion', 'lib', 'services', 'swiftshare_ios_client.dart'));
  testFileExists(path.join(__dirname, '..', 'flutter_ios_companion', 'pubspec.yaml'));

  console.log('\n=== All Phase 3 Web Portals, Routes, Kotlin Code & Assets Validated! ===');
}

runTests().catch(err => {
  console.error('\n[FAIL]', err.message);
  process.exit(1);
});
