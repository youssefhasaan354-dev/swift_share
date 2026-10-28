import 'package:flutter/material.dart';
import 'package:swiftshare_ios_client/services/swiftshare_ios_client.dart';

void main() {
  runApp(const SwiftShareCompanionApp());
}

class SwiftShareCompanionApp extends StatelessWidget {
  const SwiftShareCompanionApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SwiftShare',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true).copyWith(
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF1A4FFF),
          secondary: Color(0xFF00E5FF),
          surface: Color(0xFF131B2E),
        ),
        scaffoldBackgroundColor: const Color(0xFF090D16),
        cardTheme: CardThemeData(
          color: const Color(0xFF1E293B),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: const BorderSide(color: Color(0xFF23314E)),
          ),
        ),
      ),
      home: const SwiftShareHomeScreen(),
    );
  }
}

class SwiftShareHomeScreen extends StatefulWidget {
  const SwiftShareHomeScreen({super.key});

  @override
  State<SwiftShareHomeScreen> createState() => _SwiftShareHomeScreenState();
}

class _SwiftShareHomeScreenState extends State<SwiftShareHomeScreen> {
  final SwiftShareClient _client = SwiftShareClient();
  List<SwiftShareDevice> _devices = [];
  bool _isScanning = false;
  SwiftShareDevice? _connected;

  @override
  void initState() {
    super.initState();
    _startScan();
  }

  Future<void> _startScan() async {
    setState(() => _isScanning = true);
    final found = await _client.discoverDevices();
    if (mounted) setState(() { _devices = found; _isScanning = false; });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                children: [
                  Container(
                    width: 44, height: 44,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(colors: [Color(0xFF1A4FFF), Color(0xFF00E5FF)]),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Center(child: Text('⚡', style: TextStyle(fontSize: 22))),
                  ),
                  const SizedBox(width: 12),
                  const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('SwiftShare', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: Colors.white)),
                    Text('iOS Companion', style: TextStyle(fontSize: 11, color: Color(0xFF94A3B8))),
                  ]),
                ],
              ),
              const SizedBox(height: 24),

              // Scan button
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: _isScanning ? null : _startScan,
                  icon: _isScanning
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Icon(Icons.radar, color: Colors.white),
                  label: Text(_isScanning ? 'Scanning for SwiftShare devices…' : 'Scan for Nearby Devices',
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF1A4FFF),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Zero-install Safari tip card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFF131B2E),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFF23314E)),
                ),
                child: const Row(
                  children: [
                    Text('💡', style: TextStyle(fontSize: 18)),
                    SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'You can also open Safari on this iPhone and browse to http://<phone-ip>:2999/webshare for instant transfer without installing an app.',
                        style: TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Device list
              const Text('Nearby Devices', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF94A3B8))),
              const SizedBox(height: 10),
              Expanded(
                child: _devices.isEmpty && !_isScanning
                    ? const Center(
                        child: Column(mainAxisSize: MainAxisSize.min, children: [
                          Text('📡', style: TextStyle(fontSize: 48)),
                          SizedBox(height: 12),
                          Text('No SwiftShare devices found.\nMake sure the Android device has SwiftShare open.',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: Color(0xFF94A3B8), fontSize: 14)),
                        ]),
                      )
                    : ListView.separated(
                        itemCount: _devices.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 8),
                        itemBuilder: (context, index) {
                          final device = _devices[index];
                          final isConnected = _connected?.id == device.id;
                          return Card(
                            child: ListTile(
                              leading: Container(
                                width: 42, height: 42,
                                decoration: BoxDecoration(
                                  gradient: LinearGradient(
                                    colors: isConnected
                                      ? [const Color(0xFF10B981), const Color(0xFF059669)]
                                      : [const Color(0xFF1A4FFF), const Color(0xFF00E5FF)],
                                  ),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Icon(isConnected ? Icons.check_circle : Icons.smartphone, color: Colors.white, size: 20),
                              ),
                              title: Text(device.name, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                              subtitle: Text('${device.ip}:${device.port}', style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8))),
                              trailing: isConnected
                                  ? const Text('Connected', style: TextStyle(color: Color(0xFF10B981), fontSize: 12, fontWeight: FontWeight.w600))
                                  : ElevatedButton(
                                      onPressed: () => _promptPinAndConnect(device),
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: const Color(0xFF1A4FFF),
                                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                      ),
                                      child: const Text('Connect', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12)),
                                    ),
                            ),
                          );
                        },
                      ),
              ),

              // Connected device panel
              if (_connected != null)
                _ConnectedPanel(device: _connected!, client: _client),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _promptPinAndConnect(SwiftShareDevice device) async {
    final pinController = TextEditingController();
    final shouldConnect = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF131B2E),
        title: Text('Connect to ${device.name}', style: const TextStyle(fontWeight: FontWeight.w700)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Enter the 4-digit PIN shown on the Android device:', style: TextStyle(fontSize: 13, color: Color(0xFF94A3B8))),
            const SizedBox(height: 12),
            TextField(
              controller: pinController,
              keyboardType: TextInputType.number,
              maxLength: 6,
              style: const TextStyle(fontSize: 18, letterSpacing: 4),
              decoration: InputDecoration(
                filled: true,
                fillColor: const Color(0xFF1E293B),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                hintText: 'PIN',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF1A4FFF)),
            child: const Text('Connect', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );

    if (shouldConnect == true) {
      final pin = pinController.text.trim();
      final success = await _client.authenticateWithPin(device, pin);
      if (!mounted) return;

      if (success) {
        setState(() => _connected = device);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Connected to ${device.name}'),
            backgroundColor: const Color(0xFF10B981),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Incorrect PIN. Please check the code on your phone.'),
            backgroundColor: const Color(0xFFEF4444),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
        );
      }
    }
  }

  @override
  void dispose() {
    _client.dispose();
    super.dispose();
  }
}

class _ConnectedPanel extends StatelessWidget {
  final SwiftShareDevice device;
  final SwiftShareClient client;

  const _ConnectedPanel({required this.device, required this.client});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFF10B981).withValues(alpha: 0.4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Connected: ${device.name}',
            style: const TextStyle(fontWeight: FontWeight.w700, color: Color(0xFF10B981), fontSize: 14)),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: ElevatedButton.icon(
              onPressed: () => client.sendFilesFromIos(device, context),
              icon: const Icon(Icons.upload, color: Colors.white, size: 18),
              label: const Text('Send Files', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF1A4FFF),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            )),
            const SizedBox(width: 10),
            Expanded(child: ElevatedButton.icon(
              onPressed: () => client.browseDeviceFiles(device, context),
              icon: const Icon(Icons.download, color: Colors.white, size: 18),
              label: const Text('Receive', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF0D9488),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            )),
          ]),
        ],
      ),
    );
  }
}
