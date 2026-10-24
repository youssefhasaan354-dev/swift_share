const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 2999;
const ASSETS_DIR = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');

const mockFiles = [
  { fileId: "f1", name: "Camera_DCIM_Sunset_4K.mp4", size: 45200000, mimeType: "video/mp4" },
  { fileId: "f2", name: "Project_Architecture_Document.pdf", size: 4200000, mimeType: "application/pdf" },
  { fileId: "f3", name: "SwiftShare_App_v1.0.apk", size: 18500000, mimeType: "application/vnd.android.package-archive" },
  { fileId: "f4", name: "Studio_Session_FLAC_Master.flac", size: 32100000, mimeType: "audio/flac" },
  { fileId: "f5", name: "Photo_Mountain_Trek.jpg", size: 6800000, mimeType: "image/jpeg" }
];

const mockRemoteMedia = [
  { id: "m1", name: "Vacation_Highlights.mp4", size: 52000000, type: "videos", thumbnail: "" },
  { id: "m2", name: "Family_Portrait_2026.jpg", size: 5400000, type: "photos", thumbnail: "" },
  { id: "m3", name: "Favorite_Album_Track01.mp3", size: 8200000, type: "music", thumbnail: "" },
  { id: "m4", name: "Cyberpunk_2077_Companion.apk", size: 48000000, type: "apps", thumbnail: "" },
  { id: "m5", name: "Financial_Report_Q3.pdf", size: 2100000, type: "docs", thumbnail: "" }
];

const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://localhost:${PORT}`);
  const pathname = parsedUrl.pathname;

  // Set CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (pathname === '/' || pathname === '/webshare') {
    const html = fs.readFileSync(path.join(ASSETS_DIR, 'web', 'webshare.html'));
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(html);
  } else if (pathname === '/pc') {
    const html = fs.readFileSync(path.join(ASSETS_DIR, 'web', 'pc', 'client.html'));
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(html);
  } else if (pathname === '/invite') {
    const html = fs.readFileSync(path.join(ASSETS_DIR, 'web', 'invite', 'home.html'));
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(html);
  } else if (pathname === '/api/manifest') {
    const payload = {
      deviceName: "Pixel 9 Pro (SwiftShare Node)",
      freeSpace: 124800000000,
      tokenValid: true,
      files: mockFiles
    };
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(payload));
  } else if (pathname === '/api/remote/files') {
    const category = parsedUrl.searchParams.get('category') || 'photos';
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(mockRemoteMedia));
  } else if (pathname.startsWith('/api/download/')) {
    res.writeHead(200, {
      'Content-Type': 'application/octet-stream',
      'Content-Disposition': 'attachment; filename="mock_file.dat"'
    });
    res.end(Buffer.alloc(1024 * 1024, 0x41)); // 1MB mock payload
  } else if (pathname === '/api/upload') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: "success", uploadedCount: 1 }));
  } else if (pathname === '/apps/app-release.apk') {
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': 'attachment; filename="SwiftShare.apk"'
    });
    res.end(Buffer.alloc(1024 * 512, 0x50)); // Mock APK
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not Found');
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`SwiftShare Verification Server listening on http://127.0.0.1:${PORT}`);
});
