package com.swiftshare.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import com.swiftshare.explorer.MediaStoreRepository
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.cleaner.JunkCategory
import com.swiftshare.cleaner.JunkCleanerEngine
import com.swiftshare.cleaner.JunkItem
import com.swiftshare.common.NetworkUtils
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SuccessGreen
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun CleanerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { MediaStoreRepository(context) }
    val cleaner = remember { JunkCleanerEngine(context) }
    val scanState by cleaner.scanState.collectAsState()
    val scope = rememberCoroutineScope()

    val selectedItems = remember { mutableStateListOf<JunkItem>() }

    LaunchedEffect(Unit) {
        cleaner.startScan()
    }

    LaunchedEffect(scanState.items) {
        selectedItems.clear()
        selectedItems.addAll(scanState.items.filter { it.isSelected })
    }

    val strings = com.swiftshare.common.LocalAppStrings.current
    val isArabic = strings.appName.contains("سويفت")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                fontSize = 24.sp,
                color = TextPrimary,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 16.dp)
            )
            Text(
                text = if (isArabic) "تنظيف الملفات غير المرغوبة (CleanIt)" else "CleanIt Junk Cleaner",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hero Card with Storage Counter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (scanState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = AccentCyan,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isArabic) "جارٍ فحص تخزين الجهاز…" else "Scanning Device Storage...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isArabic) "تحليل الملفات المؤقتة والملفات المكررة" else "Analyzing temp cache, APKs & duplicates",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                } else if (scanState.isCleanFinished) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isArabic) "تم تنظيف التخزين!" else "Storage Cleaned!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = SuccessGreen
                    )
                    Text(
                        text = if (isArabic) "تم تفريغ ${NetworkUtils.formatBytes(scanState.bytesFreed)} من المساحة" else "Freed ${NetworkUtils.formatBytes(scanState.bytesFreed)} of space",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                } else {
                    val bytesToClean = selectedItems.sumOf { it.size }
                    Text(
                        text = if (isArabic) "الملفات المحددة للتنظيف" else "JUNK DETECTED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = NetworkUtils.formatBytes(bytesToClean),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCyan
                    )
                    Text(
                        text = if (isArabic) "${selectedItems.size} عنصر محدد" else "${selectedItems.size} junk item(s) selected",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Junk Items List
        if (!scanState.isCleanFinished && !scanState.isScanning) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scanState.items) { item ->
                    val isChecked = selectedItems.contains(item)
                    val icon = when (item.category) {
                        JunkCategory.TEMP_FILES -> "🗑️"
                        JunkCategory.OBSOLETE_APKS -> "📦"
                        JunkCategory.LARGE_FILES -> "💾"
                        JunkCategory.DUPLICATES -> "📑"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                            .clickable {
                                if (isChecked) selectedItems.remove(item) else selectedItems.add(item)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            val apkIcon = if (item.category == JunkCategory.OBSOLETE_APKS) {
                                remember(item.file.absolutePath) { repo.loadAppIconBitmap(null, item.file.absolutePath) }
                            } else null

                            if (apkIcon != null) {
                                Image(
                                    bitmap = apkIcon,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Text(icon, fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                            Text("${item.category.name.replace("_", " ")} • ${NetworkUtils.formatBytes(item.size)}", fontSize = 11.sp, color = TextSecondary)
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { check ->
                                if (check) selectedItems.add(item) else selectedItems.remove(item)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = AccentCyan)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    scope.launch {
                        cleaner.cleanSelected(selectedItems)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    text = "Clean Now (${NetworkUtils.formatBytes(selectedItems.sumOf { it.size })})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
