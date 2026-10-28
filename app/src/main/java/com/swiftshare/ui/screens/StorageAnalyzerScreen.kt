package com.swiftshare.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaItem
import com.swiftshare.explorer.MediaStoreRepository
import com.swiftshare.explorer.StorageAnalyzer
import com.swiftshare.explorer.StorageBreakdown
import com.swiftshare.explorer.StorageCategory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageAnalyzerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val scope = rememberCoroutineScope()

    val repo = remember { MediaStoreRepository(context) }
    val analyzer = remember { StorageAnalyzer(mediaRepo = repo, context = context) }

    var breakdown by remember { mutableStateOf<StorageBreakdown?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Active drill-down category (null = main overview)
    var drillDownCategory by remember { mutableStateOf<StorageCategory?>(null) }
    var categoryItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val selectedItems = remember { mutableStateListOf<MediaItem>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    fun refreshBreakdown() {
        scope.launch {
            isLoading = true
            val b = withContext(Dispatchers.IO) { analyzer.analyzeStorage() }
            breakdown = b
            isLoading = false
        }
    }

    fun openDrillDown(cat: StorageCategory) {
        drillDownCategory = cat
        selectedItems.clear()
        scope.launch {
            isLoading = true
            val items = withContext(Dispatchers.IO) { analyzer.getCategoryItems(cat.key) }
            categoryItems = items
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshBreakdown()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
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
                    .clickable {
                        if (drillDownCategory != null) {
                            drillDownCategory = null
                            selectedItems.clear()
                            refreshBreakdown()
                        } else {
                            onBack()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 20.sp, color = TextPrimary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (drillDownCategory != null) {
                    val title = if (currentLang == AppLanguage.AR) drillDownCategory!!.titleAr else drillDownCategory!!.titleEn
                    "${drillDownCategory!!.icon} $title"
                } else {
                    if (currentLang == AppLanguage.AR) "محلل التخزين" else "Storage Analyzer"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AccentCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (drillDownCategory == null) {
            // Main Overview
            breakdown?.let { b ->
                StorageOverviewCard(breakdown = b)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (currentLang == AppLanguage.AR) "فئات التخزين والتفاصيل" else "Storage Categories",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val categories = listOf(
                        StorageCategory.PHOTOS to b.photosBytes,
                        StorageCategory.VIDEOS to b.videosBytes,
                        StorageCategory.MUSIC to b.musicBytes,
                        StorageCategory.APPS to b.appsBytes,
                        StorageCategory.DOCS to b.docsBytes,
                        StorageCategory.OTHER to b.otherBytes
                    )

                    items(categories) { (cat, bytes) ->
                        val catTitle = if (currentLang == AppLanguage.AR) cat.titleAr else cat.titleEn
                        val percent = if (b.totalBytes > 0) (bytes * 100f / b.totalBytes) else 0f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCard)
                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                                .clickable {
                                    if (cat != StorageCategory.OTHER) {
                                        openDrillDown(cat)
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(cat.icon, fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = catTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${NetworkUtils.formatBytes(bytes)} (${String.format("%.1f", percent)}%)",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            if (cat != StorageCategory.OTHER) {
                                Text("→", fontSize = 18.sp, color = AccentCyan)
                            }
                        }
                    }
                }
            }
        } else {
            // Category Drill-Down View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${categoryItems.size} items • ${NetworkUtils.formatBytes(categoryItems.sumOf { it.size })}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                TextButton(
                    onClick = {
                        if (selectedItems.size == categoryItems.size) {
                            selectedItems.clear()
                        } else {
                            selectedItems.clear()
                            selectedItems.addAll(categoryItems)
                        }
                    }
                ) {
                    Text(
                        text = if (selectedItems.size == categoryItems.size) "Deselect All" else "Select All",
                        color = AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // File items list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(categoryItems, key = { "${it.id}_${it.path}" }) { item ->
                    val isSelected = selectedItems.contains(item)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) AccentCyan else SurfaceBorderDark,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isSelected) selectedItems.remove(item) else selectedItems.add(item)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) selectedItems.add(item) else selectedItems.remove(item)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentCyan,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = BackgroundDark
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${NetworkUtils.formatBytes(item.size)} • ${item.path}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom Delete Action Bar
            AnimatedVisibility(visible = selectedItems.isNotEmpty()) {
                val selectedBytes = selectedItems.sumOf { it.size }
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text(
                        text = "🗑️ Delete Selected (${selectedItems.size} items • ${NetworkUtils.formatBytes(selectedBytes)})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val selectedBytes = selectedItems.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Permanent Delete Confirmation",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete ${selectedItems.size} files (${NetworkUtils.formatBytes(selectedBytes)})? This cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            val itemsToDelete = selectedItems.toList()
                            val result = withContext(Dispatchers.IO) {
                                analyzer.deleteItems(itemsToDelete)
                            }
                            categoryItems = categoryItems.filterNot { itemsToDelete.contains(it) }
                            selectedItems.clear()
                            Toast.makeText(
                                context,
                                "Freed ${NetworkUtils.formatBytes(result.freedBytes)} (${result.deletedCount} files deleted)",
                                Toast.LENGTH_SHORT
                            ).show()
                            refreshBreakdown()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
fun StorageOverviewCard(breakdown: StorageBreakdown) {
    val usedPercent = if (breakdown.totalBytes > 0) {
        (breakdown.usedBytes.toFloat() / breakdown.totalBytes.toFloat()) * 100f
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Storage Space",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = "${NetworkUtils.formatBytes(breakdown.usedBytes)} of ${NetworkUtils.formatBytes(breakdown.totalBytes)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            }
            Text(
                text = "${String.format("%.1f", usedPercent)}% Used",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Multi-segment storage bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceDark)
        ) {
            val total = breakdown.totalBytes.toFloat()
            if (total > 0f) {
                Segment(weight = breakdown.photosBytes / total, color = AccentCyan)
                Segment(weight = breakdown.videosBytes / total, color = ElectricPurple)
                Segment(weight = breakdown.musicBytes / total, color = Color(0xFFFFB300))
                Segment(weight = breakdown.appsBytes / total, color = SuccessGreen)
                Segment(weight = breakdown.docsBytes / total, color = PrimaryBlue)
                Segment(weight = breakdown.otherBytes / total, color = Color(0xFF757575))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem(color = AccentCyan, label = "Photos")
            LegendItem(color = ElectricPurple, label = "Videos")
            LegendItem(color = Color(0xFFFFB300), label = "Audio")
            LegendItem(color = SuccessGreen, label = "Apps")
            LegendItem(color = PrimaryBlue, label = "Docs")
            LegendItem(color = Color(0xFF757575), label = "Other")
        }
    }
}

@Composable
fun RowScope.Segment(weight: Float, color: Color) {
    if (weight > 0.005f) {
        Box(
            modifier = Modifier
                .weight(weight)
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}
