package com.swiftshare.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.AvatarUtils
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.NetworkUtils
import com.swiftshare.common.SettingsManager
import com.swiftshare.common.StorageDestinationManager
import com.swiftshare.common.StorageVolumeType
import com.swiftshare.common.ThemeMode
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val isAr = currentLang == AppLanguage.AR

    val themeMode by SettingsManager.themeMode.collectAsState()
    val notificationsEnabled by SettingsManager.notificationsEnabled.collectAsState()
    val soundEnabled by SettingsManager.soundEnabled.collectAsState()
    val destinationPath by SettingsManager.destinationPath.collectAsState()

    val deviceName by LocaleManager.deviceName.collectAsState()
    val avatarEmoji by LocaleManager.avatarEmoji.collectAsState()
    val customAvatarBase64 by LocaleManager.customAvatarBase64.collectAsState()

    val activeVolumeType by StorageDestinationManager.selectedVolume.collectAsState()
    val volumes = remember(activeVolumeType) {
        StorageDestinationManager.getAvailableVolumes(context)
    }

    var editingName by remember { mutableStateOf(deviceName) }
    var selectedAvatar by remember { mutableStateOf(avatarEmoji) }
    var customPhotoBase64 by remember { mutableStateOf(customAvatarBase64) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val b64 = AvatarUtils.compressUriToBase64(context, uri)
            if (b64 != null) {
                customPhotoBase64 = b64
                selectedAvatar = "🖼️"
                LocaleManager.setDeviceProfile(editingName, selectedAvatar, b64)
            } else {
                Toast.makeText(context, "Image could not be compressed under 32KB", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val customBitmap = remember(customPhotoBase64) {
        customPhotoBase64?.let { AvatarUtils.decodeBase64ToBitmap(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(18.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 20.sp, color = TextPrimary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isAr) "الإعدادات العامة" else "App Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Section 1: Device Profile ──────────────────────────────────────
            item {
                SettingsSectionHeader(title = if (isAr) "الملف الشخصي والجهاز" else "Profile & Device")
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue)
                                .border(2.dp, AccentCyan, CircleShape)
                                .clickable { photoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (customBitmap != null) {
                                Image(
                                    bitmap = customBitmap.asImageBitmap(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(selectedAvatar, fontSize = 30.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = {
                                    editingName = it
                                    LocaleManager.setDeviceProfile(it, selectedAvatar, customPhotoBase64)
                                },
                                label = { Text(if (isAr) "اسم الجهاز" else "Device Nickname") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = SurfaceBorderDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isAr) "اختر صورة تعبيرية سريعة" else "Choose Avatar Emoji",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val emojis = listOf("⚡", "🚀", "🦊", "🦁", "🐼", "🤖", "💎", "🔥", "👑", "🦅")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(emojis) { emo ->
                            val isChosen = selectedAvatar == emo && customPhotoBase64 == null
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isChosen) PrimaryBlue else SurfaceDark)
                                    .border(1.dp, if (isChosen) AccentCyan else SurfaceBorderDark, CircleShape)
                                    .clickable {
                                        selectedAvatar = emo
                                        customPhotoBase64 = null
                                        LocaleManager.setDeviceProfile(editingName, emo, null)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emo, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Appearance & Theme ──────────────────────────────────
            item {
                SettingsSectionHeader(title = if (isAr) "المظهر والتصميم" else "Appearance & Theme")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = themeMode == mode
                        val icon = when (mode) {
                            ThemeMode.SYSTEM -> "🌓"
                            ThemeMode.DARK -> "🌙"
                            ThemeMode.LIGHT -> "☀️"
                        }
                        val title = if (isAr) mode.titleAr else mode.titleEn

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) PrimaryBlue else SurfaceCard)
                                .border(1.dp, if (isSelected) AccentCyan else SurfaceBorderDark, RoundedCornerShape(14.dp))
                                .clickable { SettingsManager.setThemeMode(context, mode) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon, fontSize = 22.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ── Section 3: Language ────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = if (isAr) "اللغة / Language" else "Language / اللغة")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (currentLang == AppLanguage.EN) "English" else "العربية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (currentLang == AppLanguage.EN) "Application interface language" else "لغة واجهة التطبيق الحالية",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            val next = if (currentLang == AppLanguage.EN) AppLanguage.AR else AppLanguage.EN
                            LocaleManager.setLanguage(next)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (currentLang == AppLanguage.EN) "العربية" else "English", fontSize = 12.sp)
                    }
                }
            }

            // ── Section 4: Notifications & Sounds ──────────────────────────────
            item {
                SettingsSectionHeader(title = if (isAr) "الإشعارات والأصوات" else "Notifications & Sound")
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAr) "إشعارات النقل" else "Transfer Notifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isAr) "عرض إشعار مستمر بالسرعة والتقدم" else "Show persistent foreground notification during transfer",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { SettingsManager.setNotificationsEnabled(context, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = PrimaryBlue)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAr) "صوت اكتمال النقل" else "Completion Sound",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isAr) "تشغيل نغمة عند انتهاء استلام أو إرسال الملفات" else "Play alert sound when file batch finishes",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { SettingsManager.setSoundEnabled(context, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = PrimaryBlue)
                        )
                    }
                }
            }

            // ── Section 5: Destination Folder ──────────────────────────────────
            item {
                SettingsSectionHeader(title = if (isAr) "مجلد الحفظ والتخزين" else "Storage & Destination")
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (isAr) "مسار حفظ الملفات المستلمة:" else "Destination Path:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = destinationPath,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    volumes.forEach { vol ->
                        val isSelected = activeVolumeType == vol.type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PrimaryBlue.copy(alpha = 0.2f) else SurfaceDark)
                                .border(1.dp, if (isSelected) AccentCyan else SurfaceBorderDark, RoundedCornerShape(10.dp))
                                .clickable {
                                    StorageDestinationManager.setVolumeType(context, vol.type)
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (vol.type == StorageVolumeType.INTERNAL) (if (isAr) "التخزين الداخلي" else "Internal Storage") else (if (isAr) "بطاقة الذاكرة (SD Card)" else "SD Card"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Free: ${NetworkUtils.formatBytes(vol.freeBytes)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onNavigate("storage_analyzer") },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isAr) "📊 فتح محلل التخزين والتنظيف" else "📊 Open Storage Analyzer & Cleaner", fontSize = 13.sp)
                    }
                }
            }

            // ── Section 6: Permissions & System ────────────────────────────────
            item {
                SettingsSectionHeader(title = if (isAr) "النظام والأذونات" else "System & Permissions")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                        .clickable { onNavigate("permission_guide") }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isAr) "دليل الأذونات وحالة التشغيل" else "Permission Guide & Diagnostics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isAr) "Wi-Fi Direct، البلوتوث، ونظام أندرويد" else "Verify Wi-Fi Direct, Bluetooth & Storage status",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text("→", fontSize = 18.sp, color = AccentCyan)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SwiftShare v2.5.0-phase4 • Build 2026",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = AccentCyan
    )
}

