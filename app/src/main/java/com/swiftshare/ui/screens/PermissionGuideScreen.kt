package com.swiftshare.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.PermissionManager
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SuccessGreen
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

private data class PermissionStep(
    val icon: String,
    val titleEn: String,
    val titleAr: String,
    val rationaleEn: String,
    val rationaleAr: String,
    val permissions: List<String>
)

@Composable
fun PermissionGuideScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val isAr = currentLang == AppLanguage.AR

    val steps = remember {
        listOf(
            PermissionStep(
                icon = "📡",
                titleEn = "Nearby Devices & Wi-Fi",
                titleAr = "الأجهزة القريبة وشبكة Wi-Fi",
                rationaleEn = "SwiftShare establishes high-speed direct peer-to-peer Wi-Fi channels (up to 50 MB/s) to send files offline without consuming any mobile data.",
                rationaleAr = "يستخدم سويفت شير قنوات Wi-Fi مباشرة وفائقة السرعة (حتى 50 ميجابايت/ثانية) لنقل الملفات بدون استهلاك أي بيانات هاتف.",
                permissions = PermissionManager.getNearbyPermissions()
            ),
            PermissionStep(
                icon = "📁",
                titleEn = "Storage & Files Access",
                titleAr = "الوصول للملفات والتخزين",
                rationaleEn = "Required to allow you to browse and select photos, videos, apps, and documents to send, and to save received files directly to your storage.",
                rationaleAr = "مطلوب لتتمكن من استعراض واختيار الصور والفيديوهات والتطبيقات والمستندات لإرسالها، وحفظ الملفات المستلمة في جهازك.",
                permissions = PermissionManager.getStoragePermissions()
            ),
            PermissionStep(
                icon = "📷",
                titleEn = "Camera for QR Scanner",
                titleAr = "الكاميرا لمسح رمز QR",
                rationaleEn = "Allows instant one-tap connection by scanning the receiver's encrypted QR code, eliminating manual IP address setup.",
                rationaleAr = "يتيح الاتصال الفوري بلمسة واحدة عبر مسح رمز QR المشفر للمستلم دون الحاجة لأي إعدادات يدوية.",
                permissions = PermissionManager.getCameraPermissions()
            ),
            PermissionStep(
                icon = "ᛒ",
                titleEn = "Bluetooth Sharing & Pairing",
                titleAr = "البلوتوث والمشاركة السريعة",
                rationaleEn = "Allows sharing the SwiftShare APK installer offline to nearby friends who do not have the app installed yet.",
                rationaleAr = "يتيح إرسال ملف تثبيت تطبيق سويفت شير للأصدقاء القريبين الذين لا يملكون التطبيق بعد بدون أي إنترنت.",
                permissions = PermissionManager.getBluetoothPermissions()
            )
        )
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    var permissionsRefreshKey by remember { mutableIntStateOf(0) }

    val currentStep = steps[currentStepIndex]
    val isStepGranted = remember(currentStepIndex, permissionsRefreshKey) {
        PermissionManager.hasCategoryPermission(context, currentStep.permissions)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionsRefreshKey++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Step counter and Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isAr) "الخطوة ${currentStepIndex + 1} من ${steps.size}" else "Step ${currentStepIndex + 1} of ${steps.size}",
                color = AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (isAr) "تخطي الكل" else "Skip All",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        PermissionManager.setCompletedGuide(context, true)
                        onFinish()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Step Dots Indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.indices.forEach { idx ->
                val isCurrent = idx == currentStepIndex
                val isPassed = idx < currentStepIndex
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 24.dp else 10.dp, 10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isCurrent) AccentCyan else if (isPassed) PrimaryBlue else SurfaceCard)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Card Content
        AnimatedContent(
            targetState = currentStepIndex,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "StepAnimation"
        ) { stepIdx ->
            val step = steps[stepIdx]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Icon Badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.2f))
                        .border(2.dp, AccentCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(step.icon, fontSize = 38.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isAr) step.titleAr else step.titleEn,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isAr) step.rationaleAr else step.rationaleEn,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Status Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isStepGranted) SuccessGreen.copy(alpha = 0.15f) else Color(0x33FFA726))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStepGranted) (if (isAr) "ممنوح ✓" else "Granted ✓") else (if (isAr) "مطلوب للعمل ⚠️" else "Required for Offline Transfer ⚠️"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isStepGranted) SuccessGreen else Color(0xFFFFA726)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons inside Card
                if (!isStepGranted) {
                    Button(
                        onClick = {
                            launcher.launch(step.permissions.toTypedArray())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text(
                            text = if (isAr) "منح الإذن الآن" else "Grant Permission",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            PermissionManager.openAppSettings(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text(
                            text = if (isAr) "إعدادات التطبيق (استعادة)" else "Open App Settings (Recovery)",
                            fontSize = 12.sp,
                            color = AccentCyan
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Navigation Buttons (Back & Next / Finish)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStepIndex > 0) {
                OutlinedButton(
                    onClick = { currentStepIndex-- },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(if (isAr) "السابق" else "Back", fontSize = 13.sp, color = TextSecondary)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (currentStepIndex < steps.size - 1) {
                        currentStepIndex++
                    } else {
                        PermissionManager.setCompletedGuide(context, true)
                        onFinish()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isStepGranted) SuccessGreen else PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp).padding(horizontal = 8.dp)
            ) {
                val isLast = currentStepIndex == steps.size - 1
                val btnText = if (isLast) {
                    if (isAr) "بدء الاستخدام 🚀" else "Get Started 🚀"
                } else {
                    if (isAr) "التالي ←" else "Next →"
                }
                Text(btnText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

