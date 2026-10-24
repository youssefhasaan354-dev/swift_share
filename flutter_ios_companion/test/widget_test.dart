import 'package:flutter_test/flutter_test.dart';
import 'package:swiftshare_ios_client/main.dart';

void main() {
  testWidgets('SwiftShareCompanionApp smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const SwiftShareCompanionApp());
    expect(find.text('SwiftShare'), findsWidgets);
    expect(find.text('iOS Companion'), findsOneWidget);
  });
}
