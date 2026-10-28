package com.swiftshare.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.cleaner.CleanableItem
import com.swiftshare.cleaner.CleanerCategory
import com.swiftshare.cleaner.CleanerEngine
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaStoreRepository
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
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val isArabic = currentLang == AppLanguage.AR

    val repo = remember { MediaStoreRepository(context) }
    val cleaner = remember { CleanerEngine(context) }
    val scanState by cleaner.scanState.collectAsState()
    val scope = rememberCoroutineScope()

    val selectedItems = remember { mutableStateListOf<CleanableItem>() }
    var selectedCategoryFilter by remember { mutableStateOf<CleanerCategory?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cleaner.startScan()
    }

    LaunchedEffect(scanState.items) {
        selectedItems.clear()
        selectedItems.addAll(scanState.items.filter { it.isSelected })
    }

    val displayedItems = remember(scanState.items, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) {
            scanState.items
        } else {
            scanState.items.filter { it.category == selectedCategoryFilter }
        }
    }

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
                text = if (isArabic) "تنظيف التخزين (CleanIt)" else "CleanIt Storage Cleaner",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Hero Card with Storage Counter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (scanState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(54.dp),
                        color = AccentCyan,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isArabic) "جارٍ فحص التخزين والملفات المكررة…" else "Scanning Storage & Duplicates...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isArabic) "تحليل الذاكرة المؤقتة، بقايا التطبيقات والملفات الكبيرة" else "Analyzing temp cache, APKs, app leftovers & large files",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                } else if (scanState.isCleanFinished) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isArabic) "تم تنظيف التخزين بنجاح!" else "Storage Cleaned Successfully!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = SuccessGreen
                    )
                    Text(
                        text = if (isArabic) "تم تفريغ ${NetworkUtils.formatBytes(scanState.bytesFreed)} من المساحة" else "Freed ${NetworkUtils.formatBytes(scanState.bytesFreed)} of space",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                } else {
                    val bytesToClean = selectedItems.sumOf { it.size }
                    Text(
                        text = if (isArabic) "مساحة غير مرغوبة تم اكتشافها" else "JUNK & LEFTOVERS DETECTED",
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
                        text = if (isArabic) "${selectedItems.size} عنصر محدد للتنظيف" else "${selectedItems.size} item(s) selected for cleaning",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!scanState.isCleanFinished && !scanState.isScanning) {
            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAll = selectedCategoryFilter == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAll) PrimaryBlue else SurfaceDark)
                            .border(1.dp, if (isAll) AccentCyan else SurfaceBorderDark, RoundedCornerShape(10.dp))
                            .clickable { selectedCategoryFilter = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isArabic) "الكل (${scanState.items.size})" else "All (${scanState.items.size})",
                            fontSize = 11.sp,
                            fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAll) TextPrimary else TextSecondary
                        )
                    }
                }

                items(CleanerCategory.values()) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val count = scanState.items.count { it.category == cat }
                    val title = if (isArabic) cat.titleAr else cat.titleEn
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PrimaryBlue else SurfaceDark)
                            .border(1.dp, if (isSelected) AccentCyan else SurfaceBorderDark, RoundedCornerShape(10.dp))
                            .clickable { selectedCategoryFilter = cat }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${cat.icon} $title ($count)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Items List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedItems, key = { "${it.category}_${it.file.absolutePath}" }) { item ->
                    val isChecked = selectedItems.contains(item)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, if (isChecked) AccentCyan.copy(alpha = 0.5f) else SurfaceBorderDark, RoundedCornerShape(12.dp))
                            .clickable {
                                if (isChecked) selectedItems.remove(item) else selectedItems.add(item)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            val apkIcon = if (item.category == CleanerCategory.OBSOLETE_APKS) {
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
                                Text(item.category.icon, fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            val catTitle = if (isArabic) item.category.titleAr else item.category.titleEn
                            Text(
                                text = "$catTitle • ${NetworkUtils.formatBytes(item.size)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            if (!item.detail.isNullOrBlank()) {
                                Text(
                                    text = item.detail,
                                    fontSize = 10.sp,
                                    color = AccentCyan.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { check ->
                                if (check) selectedItems.add(item) else selectedItems.remove(item)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentCyan,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = BackgroundDark
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showConfirmDialog = true },
                enabled = selectedItems.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    disabledContainerColor = SurfaceDark
                )
            ) {
                Text(
                    text = "🧹 Clean Selected (${NetworkUtils.formatBytes(selectedItems.sumOf { it.size })})",
                    fontSize = 15.sp,
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

    // Confirmation Dialog
    if (showConfirmDialog) {
        val totalBytes = selectedItems.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = if (isArabic) "تأكيد تنظيف الملفات" else "Clean Confirmation",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = if (isArabic) {
                        "هل أنت متأكد من رغبتك في حذف ${selectedItems.size} عنصر (${NetworkUtils.formatBytes(totalBytes)})؟\n\nتنويه الأمان: لن يتم المساس بالصور الشخصية المحمية أو ملفات الخزنة المشفرة."
                    } else {
                        "Are you sure you want to permanently clean ${selectedItems.size} items (${NetworkUtils.formatBytes(totalBytes)})?\n\nSafety guarantee: Camera photos and SafeBox vault files are strictly protected and never touched."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        scope.launch {
                            cleaner.cleanSelected(selectedItems.toList())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(
                        text = if (isArabic) "تنظيف الآن" else "Clean Now",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(
                        text = if (isArabic) "إلغاء" else "Cancel",
                        color = TextSecondary
                    )
                }
            },
            containerColor = SurfaceDark
        )
    }
}
