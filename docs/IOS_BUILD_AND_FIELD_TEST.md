# SwiftShare — دليل بناء تطبيق iOS واختباره ميدانياً
## iOS Build Guide & Physical Device Field Testing Protocol

وثيقة فنية تشغيلية توضح الخطوات الدقيقة لبناء تطبيق **SwiftShare iOS Companion** على بيئة macOS باستخدام Xcode، تليها قائمة اختبار ميداني خطوة بخطوة للتحقق من الاتصال واكتشاف الأجهزة ونقل الملفات بين هواتف Android و iPhone.

---

## 🛠️ متطلبات البناء على بيئة Mac (Build Requirements)

- جهاز حاسوب يعمل بنظام **macOS** (Sonoma 14+ أو Sequoia 15+).
- **Xcode 15+** مثبت مع أدوات سطر الأوامر (Command Line Tools).
- **Flutter SDK 3.24+** (مضاف إلى المسار PATH).
- **CocoaPods** مثبت (`sudo gem install cocoapods` أو `brew install cocoapods`).
- حساب مطور آبل (Apple Developer Account — يكفي الحساب المجاني للاختبار الشخصي).
- جهاز **iPhone حقيقي** يعمل بنظام iOS 15 أو أحدث.

---

## 📋 خطوات البناء والتثبيت على Mac (Step-by-Step Build)

1. **استنساخ المشروع والانتقال لمجلد تطبيق iOS:**
   ```bash
   git clone <REPO_URL>
   cd swiftshare/flutter_ios_companion
   ```

2. **تنزيل حزم دارت وفلاتر:**
   ```bash
   flutter pub get
   ```

3. **تثبيت حزم CocoaPods الأصلية:**
   ```bash
   cd ios
   pod install
   cd ..
   ```

4. **فتح المشروع داخل Xcode لتوقيع الشهادة:**
   ```bash
   open ios/Runner.xcworkspace
   ```
   - من الشريط الجانبي اختر الهدف الرئيسي **Runner**.
   - انتقل إلى تبويب **Signing & Capabilities**.
   - اختر حسابك في خانة **Team / Development Team**.
   - تأكد من تعيين **Bundle Identifier** فريد (مثلاً: `com.yourname.swiftshareCompanion`).

5. **تجهيز هاتف iPhone:**
   - صِل هاتف iPhone بالماك عبر كابل Lightning/USB-C موثوق.
   - على الهاتف: وافق على الوثوق بالكمبيوتر (**Trust This Computer**).
   - فعّل وضع المطورين على iOS 16+: **Settings -> Privacy & Security -> Developer Mode -> On** ثم أعد تشغيل الهاتف.

6. **التشغيل المباشر من الطرفية أو Xcode:**
   ```bash
   # لعرض معرف الجهاز المتصل
   flutter devices

   # التشغيل المباشر بوضع Release للأداء الأقصى
   flutter run -d <DEVICE_ID> --release
   ```

---

## 🧪 قائمة الاختبار الميداني المرتبة (Field Test Checklist)

نفّذ الخطوات التالية بالتسلسل وسجّل النتائج الفعلية:

### 1. فحص إذن الشبكة المحلية (Local Network Permission)
- **الإجراء:** افتح التطبيق على هاتف iPhone لأول مرة واضغط على زر البحث أو استلام الملفات.
- **النتيجة المتوقعة:** ظهور رسالة النظام المنبثقة فوراً:
  > *"SwiftShare requires local network access to discover nearby devices."*
- **التحقق:** عند الضغط على **OK / سماح**، يجب أن تبدأ خدمة Bonjour بالاستماع دون أخطاء `kDNSServiceErr_PolicyDenied`.

### 2. اكتشاف الجهاز عبر Bonjour / mDNS (Android ↔ iPhone)
- **الإجراء:**
  - هاتف Android: افتح SwiftShare واضغط **Send** أو **Receive** (مفعلاً خدمة mDNS على منفذ 55283).
  - هاتف iPhone: افتح SwiftShare Companion على نفس شبكة الـ Wi-Fi (أو عبر هوتسبوت Android).
- **النتيجة المتوقعة:**
  - يكتشف هاتف iPhone إعلان الخدمة `_swiftshare._tcp` الصادر من Android.
  - يظهر اسم هاتف Android في قائمة الرادار / الأجهزة المتاحة على iPhone خلال 1 إلى 3 ثوانٍ فقط مع عنوان الـ IP والمنفذ.

### 3. نقل ملف صغير (Small File Transfer ~ 1-5 MB)
- **الإجراء:** اختر صورة أو ملف PDF صغير من iPhone وأرسله إلى Android (والعكس).
- **النتيجة المتوقعة:**
  - فتح قناة اتصال TCP ذرية عبر المنفذ `2998`.
  - اكتمال النقل خلال أقل من ثانية واحدة.
  - نجاح فحص التحقق من تجزئة **SHA-256** وعدم وجود أي تلف في الملف.

### 4. نقل ملف كبير (Large File Transfer > 500 MB - 1 GB)
- **الإجراء:** اختر مقطع فيديو طويل أو حزمة ملفات بحجم يفوق 500 ميجابايت.
- **النتيجة المتوقعة:**
  - استقرار معدل النقل اللحظي بين **25 إلى 60 ميجابايت/ثانية** على شبكة 5 GHz Wi-Fi Direct.
  - تحديث مؤشر التقدم بدقة (Percentage & MB Transferred).
  - استمرار النقل حتى مع وضع التطبيق في الخلفية لفترة وجيزة بفضل مهام الخلفية.

### 5. محاكاة فشل الاتصال واستئناف النقل (Interruption & Resume)
- **الإجراء:** أثناء نقل ملف كبير عند نسبة 50%، افصل شبكة Wi-Fi لمدة ثانيتين ثم أعد وصلها.
- **النتيجة المتوقعة:**
  - عدم انهيار التطبيق (No Crash) وظهور إشعار بانقطاع الشبكة.
  - عند إعادة الاتصال، يستأنف التطبيق النقل من آخر إزاحة (Chunk Offset) بدلاً من إعادة إرسال الملف من البداية.

### 6. حفظ واستعراض الملفات في تطبيق الملفات (Files App Integration)
- **الإجراء:** بعد استلام ملف على iPhone، اغلق SwiftShare وافتح تطبيق **Files** الرسمي على iOS.
- **النتيجة المتوقعة:**
  - في تبويب **On My iPhone** يظهر مجلد باسم **SwiftShare**.
  - تجد كافة الملفات المستلمة بداخله بصيغها الأصلية، وقابلة للمعاينة والمشاركة مع التطبيقات الأخرى، بفضل تفعيل مفتاحي `UIFileSharingEnabled` و `LSSupportsOpeningDocumentsInPlace` في `Info.plist`.

---

## ⚠️ تنبيه فني حول قيود بيئة Windows

- بيئة التطوير الحالية مبنية على **Windows 11**، مما يتيح فحص الكود استاتيكياً (`flutter analyze` و `flutter test`) وتجميع تطبيق أندرويد بنجاح (`compileDebugKotlin`).
- بناء حزم `.ipa` الموقعة وتشغيل محاكي iOS أو أجهزة iPhone الفعلية **يتطلب حصرياً بيئة macOS مع Xcode**، وبالتالي فإن الاختبار الميداني أعلاه ملزم إجراؤه على جهاز Mac قبل نشر التطبيق النهائي على App Store أو TestFlight.
