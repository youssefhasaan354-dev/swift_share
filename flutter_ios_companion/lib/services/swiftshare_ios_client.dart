import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:multicast_dns/multicast_dns.dart';
import 'package:path_provider/path_provider.dart';

// ─────────────────────────────────────────────────────────────────────────────
// SwiftShare iOS Client
//
// Protocol:
//   • Port 2999 — JSON control & HTTP REST (manifest, upload, download, thumbnail)
//   • Port 2998 — binary stream (zero-copy NIO channel)
//   • mDNS     — _swiftshare._tcp. Bonjour service discovery
// ─────────────────────────────────────────────────────────────────────────────

class SwiftShareDevice {
  final String id;
  final String name;
  final String ip;
  final int port;
  final String sessionToken;

  const SwiftShareDevice({
    required this.id,
    required this.name,
    required this.ip,
    required this.port,
    this.sessionToken = '',
  });

  String get baseUrl => 'http://$ip:$port';
}

class SwiftShareFileInfo {
  final String fileId;
  final String name;
  final int size;
  final String mimeType;
  final String? thumbnailUrl;

  const SwiftShareFileInfo({
    required this.fileId,
    required this.name,
    required this.size,
    required this.mimeType,
    this.thumbnailUrl,
  });

  factory SwiftShareFileInfo.fromJson(Map<String, dynamic> json) =>
      SwiftShareFileInfo(
        fileId: json['fileId'] as String? ?? '',
        name: json['name'] as String? ?? 'Unknown',
        size: (json['size'] as num?)?.toInt() ?? 0,
        mimeType: json['mimeType'] as String? ?? '*/*',
        thumbnailUrl: json['thumbnailUrl'] as String?,
      );
}

class SwiftShareClient {
  static const String _serviceType = '_swiftshare._tcp.';
  static const int _controlPort = 2999;
  static const int dataPort = 2998;
  static const Duration _scanTimeout = Duration(seconds: 8);

  MDnsClient? _mdns;

  /// Discover SwiftShare devices on the local network via Bonjour/mDNS.
  Future<List<SwiftShareDevice>> discoverDevices() async {
    final devices = <SwiftShareDevice>[];
    _mdns?.stop();
    _mdns = MDnsClient(rawDatagramSocketFactory: (dynamic host, int port, {bool? reuseAddress, bool? reusePort, int? ttl}) =>
        RawDatagramSocket.bind(host, port, reuseAddress: reuseAddress ?? true, reusePort: reusePort ?? false, ttl: ttl ?? 255));

    try {
      await _mdns!.start();

      await for (final PtrResourceRecord ptr in _mdns!
          .lookup<PtrResourceRecord>(ResourceRecordQuery.serverPointer(_serviceType))
          .timeout(_scanTimeout, onTimeout: (_) {})) {
        // Resolve SRV (port + hostname)
        await for (final SrvResourceRecord srv in _mdns!
            .lookup<SrvResourceRecord>(ResourceRecordQuery.service(ptr.domainName))
            .timeout(const Duration(seconds: 2), onTimeout: (_) {})) {
          // Resolve A (IPv4 address)
          await for (final IPAddressResourceRecord ip in _mdns!
              .lookup<IPAddressResourceRecord>(ResourceRecordQuery.addressIPv4(srv.target))
              .timeout(const Duration(seconds: 2), onTimeout: (_) {})) {
            final deviceId = ptr.domainName;
            if (!devices.any((d) => d.id == deviceId)) {
              final deviceName = ptr.domainName
                  .replaceAll('.$_serviceType', '')
                  .replaceAll('SwiftShareNode.', '');
              devices.add(SwiftShareDevice(
                id: deviceId,
                name: deviceName.isNotEmpty ? deviceName : ip.address.address,
                ip: ip.address.address,
                port: srv.port,
              ));
            }
          }
        }
      }
    } on TimeoutException {
      // Normal — scan completed
    } catch (e) {
      debugPrint('[SwiftShareClient] mDNS discovery error: $e');
    }

    // Fallback: try common LAN subnet scan if mDNS found nothing
    if (devices.isEmpty) {
      final fallback = await _fallbackLanScan();
      devices.addAll(fallback);
    }

    return devices;
  }

  /// Quick sweep of common LAN addresses for port 2999 (fallback when mDNS unavailable).
  Future<List<SwiftShareDevice>> _fallbackLanScan() async {
    final devices = <SwiftShareDevice>[];
    // Get local IP to derive subnet
    final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv4);
    for (final iface in interfaces) {
      for (final addr in iface.addresses) {
        final parts = addr.address.split('.');
        if (parts.length != 4 || parts[0] == '127') continue;
        final subnet = '${parts[0]}.${parts[1]}.${parts[2]}';

        // Scan 1–254 in parallel batches of 32
        for (int base = 1; base <= 254; base += 32) {
          await Future.wait(List.generate(32, (i) async {
            final host = '$subnet.${base + i}';
            if (host == addr.address) return;
            try {
              final socket = await Socket.connect(host, _controlPort,
                  timeout: const Duration(milliseconds: 300));
              socket.destroy();
              // Try to get device name from manifest
              try {
                final res = await http.get(Uri.parse('http://$host:$_controlPort/api/manifest'))
                    .timeout(const Duration(seconds: 2));
                if (res.statusCode == 200) {
                  final data = jsonDecode(res.body) as Map<String, dynamic>;
                  final name = data['deviceName'] as String? ?? host;
                  devices.add(SwiftShareDevice(id: host, name: name, ip: host, port: _controlPort));
                }
              } catch (_) {
                devices.add(SwiftShareDevice(id: host, name: host, ip: host, port: _controlPort));
              }
            } catch (_) {}
          }));
        }
        return devices; // Only scan one interface
      }
    }
    return devices;
  }

  /// Fetch the file manifest from a connected device.
  Future<List<SwiftShareFileInfo>> fetchManifest(SwiftShareDevice device) async {
    final url = '${device.baseUrl}/api/manifest?token=${Uri.encodeQueryComponent(device.sessionToken)}';
    final res = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 10));
    if (res.statusCode != 200) throw Exception('Manifest fetch failed: ${res.statusCode}');
    final data = jsonDecode(res.body) as Map<String, dynamic>;
    final files = (data['files'] as List<dynamic>?) ?? [];
    return files.map((f) {
      final fi = f as Map<String, dynamic>;
      fi['thumbnailUrl'] = '${device.baseUrl}/api/thumbnail/${Uri.encodeComponent(fi['fileId'] ?? '')}';
      return SwiftShareFileInfo.fromJson(fi);
    }).toList();
  }

  /// Download a file from the connected device to the iOS Documents directory.
  Future<File> downloadFile(SwiftShareDevice device, SwiftShareFileInfo fileInfo, {
    void Function(int received, int total)? onProgress,
  }) async {
    final url = '${device.baseUrl}/api/download/${fileInfo.fileId}?token=${Uri.encodeQueryComponent(device.sessionToken)}';
    final request = http.Request('GET', Uri.parse(url));
    final response = await http.Client().send(request).timeout(const Duration(minutes: 10));

    if (response.statusCode != 200) throw Exception('Download failed: ${response.statusCode}');

    final tempDir = await _getDownloadDirectory();
    final file = File('${tempDir.path}/${fileInfo.name}');
    final sink = file.openWrite();
    int received = 0;
    final total = response.contentLength ?? 0;

    await response.stream.forEach((chunk) {
      sink.add(chunk);
      received += chunk.length;
      onProgress?.call(received, total);
    });
    await sink.flush();
    await sink.close();
    return file;
  }

  /// Upload files from iOS to the connected Android device.
  Future<void> uploadFiles(SwiftShareDevice device, List<File> files, {
    void Function(String fileName, int sent, int total)? onProgress,
  }) async {
    for (final file in files) {
      final fileBytes = await file.readAsBytes();
      final request = http.MultipartRequest('POST', Uri.parse('${device.baseUrl}/api/upload'));
      request.files.add(http.MultipartFile.fromBytes(
        'file', fileBytes, filename: file.path.split('/').last,
      ));
      request.fields['token'] = device.sessionToken;

      final streamed = await http.Client().send(request).timeout(const Duration(minutes: 10));
      if (streamed.statusCode < 200 || streamed.statusCode >= 300) {
        throw Exception('Upload failed for ${file.path}: ${streamed.statusCode}');
      }
    }
  }

  /// Open iOS file picker and send selected files to device.
  Future<void> sendFilesFromIos(SwiftShareDevice device, BuildContext context) async {
    final result = await FilePicker.platform.pickFiles(allowMultiple: true, withData: false, withReadStream: false);
    if (result == null || result.files.isEmpty) return;

    final files = result.paths.whereType<String>().map((p) => File(p)).toList();
    try {
      await uploadFiles(device, files);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('✅ Sent ${files.length} file(s) to ${device.name}'),
          backgroundColor: const Color(0xFF10B981),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('❌ Upload failed: $e'),
          backgroundColor: const Color(0xFFEF4444),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ));
      }
    }
  }

  /// Browse and download files from the Android device.
  Future<void> browseDeviceFiles(SwiftShareDevice device, BuildContext context) async {
    try {
      final manifest = await fetchManifest(device);
      if (!context.mounted) return;
      showModalBottomSheet(
        context: context,
        backgroundColor: const Color(0xFF131B2E),
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
        builder: (ctx) => _FileListSheet(device: device, files: manifest, client: this),
      );
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed to load file list: $e'),
          backgroundColor: const Color(0xFFEF4444),
        ));
      }
    }
  }

  Future<Directory> _getDownloadDirectory() async {
    if (Platform.isIOS || Platform.isAndroid || Platform.isMacOS || Platform.isWindows) {
      return await getApplicationDocumentsDirectory();
    }
    return Directory.systemTemp;
  }

  void dispose() {
    _mdns?.stop();
  }
}

class _FileListSheet extends StatelessWidget {
  final SwiftShareDevice device;
  final List<SwiftShareFileInfo> files;
  final SwiftShareClient client;

  const _FileListSheet({required this.device, required this.files, required this.client});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const SizedBox(height: 12),
        Container(width: 40, height: 4, decoration: BoxDecoration(color: const Color(0xFF23314E), borderRadius: BorderRadius.circular(2))),
        const SizedBox(height: 12),
        Text('Files on ${device.name}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
        const SizedBox(height: 12),
        Expanded(
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: files.length,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (ctx, i) {
              final f = files[i];
              return ListTile(
                tileColor: const Color(0xFF1E293B),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                  side: const BorderSide(color: Color(0xFF23314E)),
                ),
                leading: SizedBox(width: 44, height: 44,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: f.thumbnailUrl != null
                        ? Image.network(f.thumbnailUrl!, fit: BoxFit.cover, errorBuilder: (_, __, ___) => const Text('📄', style: TextStyle(fontSize: 22)))
                        : const Center(child: Text('📄', style: TextStyle(fontSize: 22))),
                  ),
                ),
                title: Text(f.name, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13), maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text(_formatBytes(f.size), style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8))),
                trailing: IconButton(
                  icon: const Icon(Icons.download, color: Color(0xFF00E5FF)),
                  onPressed: () async {
                    try {
                      await client.downloadFile(device, f);
                      if (ctx.mounted) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
                          content: Text('✓ Downloaded ${f.name}'),
                          backgroundColor: const Color(0xFF10B981),
                          behavior: SnackBarBehavior.floating,
                        ));
                      }
                    } catch (e) {
                      if (ctx.mounted) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
                          content: Text('✗ Failed: $e'),
                          backgroundColor: const Color(0xFFEF4444),
                        ));
                      }
                    }
                  },
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 16),
      ],
    );
  }

  String _formatBytes(int bytes) {
    if (bytes == 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    int i = 0;
    double val = bytes.toDouble();
    while (val >= 1024 && i < units.length - 1) { val /= 1024; i++; }
    return '${val.toStringAsFixed(2)} ${units[i]}';
  }
}
