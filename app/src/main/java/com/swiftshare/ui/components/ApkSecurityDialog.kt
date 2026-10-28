package com.swiftshare.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swiftshare.common.ApkRiskLevel
import com.swiftshare.common.ApkSecurityReport
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.LocaleManager
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.DangerRed
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SuccessGreen
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

@Composable
fun ApkSecurityDialog(
    report: ApkSecurityReport,
    onDismiss: () -> Unit,
    onProceedInstall: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val isAr = currentLang == AppLanguage.AR

    val riskColor = when (report.riskLevel) {
        ApkRiskLevel.HIGH_RISK -> DangerRed
        ApkRiskLevel.MEDIUM_RISK -> Color(0xFFFFA726)
        ApkRiskLevel.SAFE -> SuccessGreen
    }

    val riskTitle = when (report.riskLevel) {
        ApkRiskLevel.HIGH_RISK -> if (isAr) "⚠️ تحذير أمني: خطر مرتفع" else "⚠️ Security Warning: High Risk"
        ApkRiskLevel.MEDIUM_RISK -> if (isAr) "🔍 تنبيه: أذونات حساسة" else "🔍 Notice: Sensitive Permissions"
        ApkRiskLevel.SAFE -> if (isAr) "✓ الحزمة آمنة وموثوقة" else "✓ Package Verified Safe"
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(riskColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (report.riskLevel) {
                            ApkRiskLevel.HIGH_RISK -> "🛑"
                            ApkRiskLevel.MEDIUM_RISK -> "⚠️"
                            ApkRiskLevel.SAFE -> "🛡️"
                        },
                        fontSize = 22.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isAr) "فحص أمان الحزمة (APK)" else "APK Security Audit",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = riskTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = riskColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Metadata Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .padding(12.dp)
            ) {
                Text(
                    text = report.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    text = "${report.packageName} • v${report.versionName} (${report.versionCode})",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Certificate SHA-256
                Text(
                    text = if (isAr) "بصمة الشهادة (SHA-256):" else "Certificate Fingerprint (SHA-256):",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentCyan
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(report.certSha256))
                            Toast.makeText(context, if (isAr) "تم نسخ البصمة" else "Fingerprint copied", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = report.certSha256.take(24) + "…",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextPrimary
                    )
                    Text("📋 Copy", fontSize = 10.sp, color = AccentCyan)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dangerous Permissions List
            Text(
                text = if (isAr) {
                    "الأذونات الحساسة المطلوبة (${report.dangerousPermissions.size}):"
                } else {
                    "Requested Sensitive Permissions (${report.dangerousPermissions.size}):"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (report.dangerousPermissions.isEmpty()) {
                Text(
                    text = if (isAr) "لا توجد أذونات خطرة مطلوبة." else "No dangerous permissions requested.",
                    fontSize = 11.sp,
                    color = SuccessGreen,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundDark)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(report.dangerousPermissions) { perm ->
                        val shortName = perm.substringAfterLast(".")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("•", color = riskColor, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                            Text(shortName, fontSize = 11.sp, color = TextPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isAr) "إلغاء" else "Cancel", fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onProceedInstall()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (report.riskLevel == ApkRiskLevel.HIGH_RISK) DangerRed else PrimaryBlue
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isAr) "متابعة التثبيت" else "Proceed to Install",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
