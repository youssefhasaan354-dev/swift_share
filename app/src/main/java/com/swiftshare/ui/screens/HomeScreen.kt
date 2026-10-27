package com.swiftshare.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.NetworkUtils
import com.swiftshare.ui.SwiftShareViewModel
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.ElectricPurple
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SuccessGreen
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

data class FeatureItem(
    val title: String,
    val subtitle: String,
    val icon: String,
    val route: String
)

@Composable
fun HomeScreen(
    viewModel: SwiftShareViewModel,
    onNavigate: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val deviceName by LocaleManager.deviceName.collectAsState()
    val avatarEmoji by LocaleManager.avatarEmoji.collectAsState()
    val storage by viewModel.storageBreakdown.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStorageBreakdown()
    }

    val features = remember(currentLang) {
        if (currentLang == AppLanguage.AR) {
            listOf(
                FeatureItem("مشاركة الويب", "الهاتف ↔ المتصفح", "🌐", "webshare"),
                FeatureItem("الاتصال بالحاسوب", "عرض الشاشة والملفات", "💻", "pc_connect"),
                FeatureItem("تنظيف التخزين", "الملفات غير المرغوبة", "🧹", "cleaner"),
                FeatureItem("حفظ الحالات", "وسائط واتساب", "📱", "status_saver"),
                FeatureItem("تحويل إلى MP3", "استخراج الصوت", "🎵", "video_converter"),
                FeatureItem("سجل النقل", "الملفات المستلمة", "📋", "transfer_history"),
                FeatureItem("مشاركة جماعية", "غرفة متعددة الأجهزة", "👥", "group_share"),
                FeatureItem("نسخ الهاتف", "ترحيل كامل بضغطة واحدة", "📲", "phone_clone"),
                FeatureItem("الخزنة المشفرة", "تشفير عتادي AES-256", "🔒", "safebox"),
                FeatureItem("دعوة الأصدقاء", "بلوتوث أو نقطة اتصال", "🤝", "invite"),
                FeatureItem("مستعرض الملفات", "مدير الملفات والمجلدات", "📂", "file_picker")
            )
        } else {
            listOf(
                FeatureItem("WebShare", "Phone ↔ Browser", "🌐", "webshare"),
                FeatureItem("Connect to PC", "Remote View & PPT", "💻", "pc_connect"),
                FeatureItem("CleanIt", "Junk & Duplicate", "🧹", "cleaner"),
                FeatureItem("Status Saver", "WhatsApp Media", "📱", "status_saver"),
                FeatureItem("Video to MP3", "Audio Extractor", "🎵", "video_converter"),
                FeatureItem("History", "Transfer Records", "📋", "transfer_history"),
                FeatureItem("Group Share", "Multi-peer Room", "👥", "group_share"),
                FeatureItem("Phone Clone", "1-click Migration", "📲", "phone_clone"),
                FeatureItem("SafeBox", "AES-256 Vault", "🔒", "safebox"),
                FeatureItem("Invite Friends", "Bluetooth & Zero-Data", "🤝", "invite"),
                FeatureItem("File Explorer", "Storage Analyzer", "📂", "file_picker")
            )
        }
    }

    // Animated glow pulse for hero buttons
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroScale"
    )

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        // Background gradient blobs
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-60).dp, y = (-40).dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        listOf(PrimaryBlue.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 60.dp)
                .blur(100.dp)
                .background(
                    Brush.radialGradient(
                        listOf(AccentCyan.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            // ── Top Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar & Nickname Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showProfileDialog = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(PrimaryBlue, AccentCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(avatarEmoji, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = deviceName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "SwiftShare • High-Speed",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Language Switcher Toggle Pill (AR / EN)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable {
                                val next = if (currentLang == AppLanguage.EN) AppLanguage.AR else AppLanguage.EN
                                LocaleManager.setLanguage(next)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentLang == AppLanguage.EN) "العربية" else "English",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }

                    if (storage != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${NetworkUtils.formatBytes(storage!!.freeBytes)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Hero Send & Receive Buttons (animated glow) ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SEND
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(116.dp)
                        .scale(glowScale)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A4FFF), Color(0xFF0033CC))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(AccentCyan.copy(alpha = 0.4f), PrimaryBlue.copy(alpha = 0.2f))),
                            RoundedCornerShape(24.dp)
                        )
                        .clickable { onNavigate("file_picker") }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📤", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.send,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = strings.sendSubtitle,
                            fontSize = 10.sp,
                            color = TextPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                // RECEIVE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(116.dp)
                        .scale(1f / glowScale + glowScale - 1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00B4CC), Color(0xFF006680))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(AccentCyan.copy(alpha = 0.5f), PrimaryBlue.copy(alpha = 0.2f))),
                            RoundedCornerShape(24.dp)
                        )
                        .clickable { onNavigate("radar_receive") }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📥", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.receive,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = strings.receiveSubtitle,
                            fontSize = 10.sp,
                            color = TextPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── iOS Connect Button ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Color(0xFF636366), Color(0xFF3A3A3C))),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { onNavigate("webshare") }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🍏", fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            // Connect iPhone / iOS
                        text = strings.connectIos,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = strings.connectIosSub,
                            fontSize = 9.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("FAST", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Utilities Grid ────────────────────────────────────────────────
            Text(
                text = strings.smartUtilities,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(features) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clickable { onNavigate(item.route) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceDark.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.icon, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = item.subtitle,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Device Name & Avatar Customization Dialog ───────────────────────────
    if (showProfileDialog) {
        val emojis = listOf("⚡", "🚀", "🦊", "🦁", "🐼", "🤖", "💎", "🔥", "👑", "🦅")
        var chosenAvatar by remember { mutableStateOf(avatarEmoji) }
        var inputName by remember { mutableStateOf(deviceName) }

        Dialog(onDismissRequest = { showProfileDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = strings.deviceNameTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Avatar Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.take(5).forEach { emoji ->
                        val isSel = chosenAvatar == emoji
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSel) PrimaryBlue else SurfaceCard)
                                .clickable { chosenAvatar = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.drop(5).forEach { emoji ->
                        val isSel = chosenAvatar == emoji
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSel) PrimaryBlue else SurfaceCard)
                                .clickable { chosenAvatar = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Device Nickname") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = SurfaceBorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { showProfileDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(strings.cancel)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (inputName.isNotBlank()) {
                                LocaleManager.setDeviceProfile(inputName.trim(), chosenAvatar)
                            }
                            showProfileDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(strings.save)
                    }
                }
            }
        }
    }
}
