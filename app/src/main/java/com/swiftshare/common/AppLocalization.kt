package com.swiftshare.common

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    AR("ar", "العربية")
}

data class AppStrings(
    val appName: String,
    val send: String,
    val receive: String,
    val sendSubtitle: String,
    val receiveSubtitle: String,
    val connectIos: String,
    val connectIosSub: String,
    val smartUtilities: String,
    val navTransfer: String,
    val navFiles: String,
    val navMedia: String,
    val navHistory: String,
    val tabApps: String,
    val tabPhotos: String,
    val tabVideos: String,
    val tabMusic: String,
    val tabDocs: String,
    val tabFolders: String,
    val searchHint: String,
    val selectAll: String,
    val deselectAll: String,
    val selectedFilesToSend: String,
    val freeSpace: String,
    val cleanNow: String,
    val safeBoxTitle: String,
    val safeBoxEmpty: String,
    val safeBoxUnlock: String,
    val safeBoxBiometric: String,
    val groupShareTitle: String,
    val groupCreate: String,
    val groupJoin: String,
    val phoneCloneTitle: String,
    val phoneCloneOld: String,
    val phoneCloneNew: String,
    val mediaHubTitle: String,
    val convertToMp3: String,
    val playNow: String,
    val deviceNameTitle: String,
    val save: String,
    val cancel: String,
    val radarSearching: String,
    val radarWaiting: String,
    val waitingToReceive: String,
    val searchingForPeers: String,
    val readyForConnection: String,
    val tapAvatarOrScan: String,
    val radarView: String,
    val scanQr: String,
    val showQr: String,
    val groupDesc: String,
    val groupLeave: String,
    val groupMembers: String,
    val groupBroadcastBtn: String,
    val connectingWifiDirect: String,
    val acceptPromptOnOtherDevice: String,
    val inviteFriendsTitle: String,
    val inviteBluetooth: String,
    val inviteBluetoothDesc: String,
    val inviteZeroData: String,
    val inviteZeroDataDesc: String,
    val inviteShareSheet: String,
    val inviteShareSheetDesc: String,
    val bluetoothNotSupported: String,
    val preparingApk: String,
    val scanToInstall: String
)

val EnglishStrings = AppStrings(
    appName = "SwiftShare",
    send = "SEND",
    receive = "RECEIVE",
    sendSubtitle = "Select & Transfer",
    receiveSubtitle = "Hotspot / QR Mode",
    connectIos = "Connect iPhone / iOS",
    connectIosSub = "Scan Wi-Fi QR → open Safari → AirDrop-style transfer",
    smartUtilities = "Smart Utilities",
    navTransfer = "Transfer",
    navFiles = "Files & Apps",
    navMedia = "Media Hub",
    navHistory = "History & Me",
    tabApps = "Apps",
    tabPhotos = "Photos",
    tabVideos = "Videos",
    tabMusic = "Music",
    tabDocs = "Docs",
    tabFolders = "Folders",
    searchHint = "Search apps, photos, videos, files…",
    selectAll = "Select All",
    deselectAll = "Deselect All",
    selectedFilesToSend = "Select Files to Send",
    freeSpace = "Free",
    cleanNow = "Clean Now",
    safeBoxTitle = "SafeBox Vault (AES-256-GCM)",
    safeBoxEmpty = "Vault is empty. Encrypted files are hidden from MediaStore.",
    safeBoxUnlock = "Unlock Vault",
    safeBoxBiometric = "Fingerprint / Biometric",
    groupShareTitle = "Group Share Portal",
    groupCreate = "Create Room (Host)",
    groupJoin = "Join Room (Member)",
    phoneCloneTitle = "Phone Clone Migration",
    phoneCloneOld = "Old Phone (Send)",
    phoneCloneNew = "New Phone (Receive)",
    mediaHubTitle = "Media Hub",
    convertToMp3 = "Convert to MP3",
    playNow = "Play",
    deviceNameTitle = "Device Name & Avatar",
    save = "Save",
    cancel = "Cancel",
    radarSearching = "Searching for Peers",
    radarWaiting = "Waiting to Receive",
    waitingToReceive = "Waiting to Receive",
    searchingForPeers = "Searching for Peers",
    readyForConnection = "Ready for connection",
    tapAvatarOrScan = "Tap avatar or scan QR",
    radarView = "Radar View",
    scanQr = "Scan QR",
    showQr = "Show QR",
    groupDesc = "Host broadcasts files simultaneously to all connected room members.",
    groupLeave = "Leave Room",
    groupMembers = "Connected Members",
    groupBroadcastBtn = "Select Files to Broadcast",
    connectingWifiDirect = "Connecting via Wi-Fi Direct…",
    acceptPromptOnOtherDevice = "Please accept the connection prompt on the other device.",
    inviteFriendsTitle = "Invite Friends",
    inviteBluetooth = "Send via Bluetooth",
    inviteBluetoothDesc = "Direct offline APK transfer without Wi-Fi",
    inviteZeroData = "Zero-Data Wi-Fi Invite",
    inviteZeroDataDesc = "Share APK via hotspot web portal at /invite",
    inviteShareSheet = "Share via Apps",
    inviteShareSheetDesc = "Send app link or installer via WhatsApp, Telegram, etc.",
    bluetoothNotSupported = "Bluetooth is not supported or available on this device.",
    preparingApk = "Preparing SwiftShare APK package…",
    scanToInstall = "Scan QR to download SwiftShare APK without mobile data"
)

val ArabicStrings = AppStrings(
    appName = "سويفت شير",
    send = "إرسال",
    receive = "استقبال",
    sendSubtitle = "اختر الملفات وابدأ النقل",
    receiveSubtitle = "وضع النقطة الساخنة / QR",
    connectIos = "الاتصال بـ iPhone / iOS",
    connectIosSub = "امسح رمز QR للانضمام إلى Wi-Fi ثم افتح Safari",
    smartUtilities = "الأدوات الذكية",
    navTransfer = "إرسال واستقبال",
    navFiles = "الملفات والتطبيقات",
    navMedia = "مركز الوسائط",
    navHistory = "السجل وملفي",
    tabApps = "التطبيقات",
    tabPhotos = "الصور",
    tabVideos = "الفيديوهات",
    tabMusic = "الموسيقى",
    tabDocs = "المستندات",
    tabFolders = "المجلدات",
    searchHint = "ابحث في التطبيقات، الصور، الفيديوهات والملفات…",
    selectAll = "تحديد الكل",
    deselectAll = "إلغاء التحديد",
    selectedFilesToSend = "اختر الملفات للإرسال",
    freeSpace = "مساحة حرة",
    cleanNow = "نظّف الآن",
    safeBoxTitle = "خزنة SafeBox المشفرة (AES-256)",
    safeBoxEmpty = "الخزنة فارغة. الملفات المشفرة مخفية تماماً.",
    safeBoxUnlock = "فتح الخزنة",
    safeBoxBiometric = "بصمة الإصبع",
    groupShareTitle = "بوابة المشاركة الجماعية",
    groupCreate = "إنشاء غرفة (مضيف)",
    groupJoin = "انضمام لغرفة (عضو)",
    phoneCloneTitle = "نسخ الهاتف ونقل البيانات",
    phoneCloneOld = "الهاتف القديم (إرسال)",
    phoneCloneNew = "الهاتف الجديد (استقبال)",
    mediaHubTitle = "مركز الوسائط",
    convertToMp3 = "تحويل إلى MP3",
    playNow = "تشغيل",
    deviceNameTitle = "اسم الجهاز والصورة الرمزية",
    save = "حفظ",
    cancel = "إلغاء",
    radarSearching = "جارٍ البحث عن الأجهزة القريبة",
    radarWaiting = "بانتظار الاستقبال",
    waitingToReceive = "بانتظار الاستقبال",
    searchingForPeers = "جارٍ البحث عن أجهزة قريبة",
    readyForConnection = "جاهز للاتصال المباشر",
    tapAvatarOrScan = "اضغط على الصورة الرمزية أو امسح الرمز",
    radarView = "عرض الرادار",
    scanQr = "مسح QR",
    showQr = "عرض QR",
    groupDesc = "يبث المضيف الملفات في نفس الوقت لجميع الأعضاء المنضمين للغرفة.",
    groupLeave = "مغادرة الغرفة",
    groupMembers = "الأعضاء المتصلون",
    groupBroadcastBtn = "اختيار ملفات للبث الجماعي",
    connectingWifiDirect = "جارٍ الاتصال عبر Wi-Fi Direct…",
    acceptPromptOnOtherDevice = "يرجى قبول طلب الاتصال على الجهاز الآخر.",
    inviteFriendsTitle = "دعوة الأصدقاء",
    inviteBluetooth = "إرسال عبر البلوتوث",
    inviteBluetoothDesc = "نقل مباشر لتطبيق SwiftShare بدون إنترنت أو شبكة",
    inviteZeroData = "مشاركة بدون إنترنت (Zero-Data)",
    inviteZeroDataDesc = "تنزيل التطبيق عبر النقطة الساخنة والمتصفح",
    inviteShareSheet = "مشاركة عبر التطبيقات",
    inviteShareSheetDesc = "إرسال رابط التطبيق أو الملف عبر واتساب وتيليجرام",
    bluetoothNotSupported = "البلوتوث غير متوفر أو غير مدعوم على هذا الجهاز.",
    preparingApk = "جارٍ تجهيز ملف التثبيت SwiftShare APK…",
    scanToInstall = "امسح رمز QR لتنزيل تطبيق SwiftShare مجاناً بدون إنترنت"
)

val LocalAppStrings = compositionLocalOf { EnglishStrings }

object LocaleManager {
    private const val PREFS_NAME = "swiftshare_settings"
    private const val KEY_LANG = "app_language"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_AVATAR = "avatar_emoji"
    private const val KEY_CUSTOM_AVATAR = "custom_avatar_base64"
    const val MAX_AVATAR_BYTES = 32 * 1024 // 32 KB

    private lateinit var prefs: SharedPreferences

    private val _currentLanguage = MutableStateFlow(AppLanguage.EN)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val _deviceName = MutableStateFlow(Build.MODEL)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _avatarEmoji = MutableStateFlow("⚡")
    val avatarEmoji: StateFlow<String> = _avatarEmoji.asStateFlow()

    private val _customAvatarBase64 = MutableStateFlow<String?>(null)
    val customAvatarBase64: StateFlow<String?> = _customAvatarBase64.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLang = prefs.getString(KEY_LANG, AppLanguage.EN.code) ?: AppLanguage.EN.code
        _currentLanguage.value = if (savedLang == "ar") AppLanguage.AR else AppLanguage.EN
        _deviceName.value = prefs.getString(KEY_DEVICE_NAME, Build.MODEL) ?: Build.MODEL
        _avatarEmoji.value = prefs.getString(KEY_AVATAR, "⚡") ?: "⚡"
        _customAvatarBase64.value = prefs.getString(KEY_CUSTOM_AVATAR, null)
    }

    fun setLanguage(lang: AppLanguage) {
        _currentLanguage.value = lang
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_LANG, lang.code).apply()
        }
    }

    fun setDeviceProfile(name: String, avatar: String, customBase64: String? = null) {
        _deviceName.value = name
        _avatarEmoji.value = avatar
        val validatedBase64 = if (customBase64 != null) {
            val byteCount = (customBase64.length * 3) / 4
            if (byteCount <= MAX_AVATAR_BYTES) customBase64 else null
        } else null
        _customAvatarBase64.value = validatedBase64
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_DEVICE_NAME, name)
                .putString(KEY_AVATAR, avatar)
                .putString(KEY_CUSTOM_AVATAR, validatedBase64)
                .apply()
        }
    }

    fun getStrings(): AppStrings {
        return if (_currentLanguage.value == AppLanguage.AR) ArabicStrings else EnglishStrings
    }

    fun getLayoutDirection(): LayoutDirection {
        return if (_currentLanguage.value == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    }
}
