package com.swiftshare.ui.screens

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun FilePickerScreen(
    onFilesSelected: (List<MediaItem>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val repo = remember { MediaStoreRepository(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        strings.tabApps,
        strings.tabPhotos,
        strings.tabVideos,
        strings.tabMusic,
        strings.tabDocs,
        strings.tabFolders
    )

    var searchQuery by remember { mutableStateOf("") }
    val currentItems = remember { mutableStateListOf<MediaItem>() }
    val selectedItems = remember { mutableStateListOf<MediaItem>() }

    // Folder browsing state
    var currentFolder by remember {
        @Suppress("DEPRECATION")
        mutableStateOf(Environment.getExternalStorageDirectory())
    }

    // Load category items
    LaunchedEffect(selectedTab, currentFolder) {
        currentItems.clear()
        val loaded = withContext(Dispatchers.IO) {
            when (selectedTab) {
                0 -> repo.getInstalledApps()
                1 -> repo.getPhotos()
                2 -> repo.getVideos()
                3 -> repo.getAudio()
                4 -> repo.getDocuments()
                5 -> loadFolderItems(currentFolder)
                else -> emptyList()
            }
        }
        currentItems.addAll(loaded)
    }

    // Filter by search query
    val filteredItems = remember(searchQuery, currentItems.toList()) {
        if (searchQuery.isBlank()) {
            currentItems
        } else {
            currentItems.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        (it.packageName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    val totalSelectedSize = remember(selectedItems.toList()) {
        selectedItems.sumOf { it.size }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                text = strings.selectedFilesToSend,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (selectedItems.isNotEmpty()) {
                Text(
                    text = "${selectedItems.size}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }
        }

        // Real-Time Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(strings.searchHint, fontSize = 12.sp, color = TextSecondary) },
            singleLine = true,
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Text(
                        "✕",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.clickable { searchQuery = "" }
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = SurfaceBorderDark,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(52.dp)
        )

        // 6 Tabs Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = BackgroundDark,
            contentColor = AccentCyan,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AccentCyan,
                    height = 3.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) AccentCyan else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Subheader: Breadcrumb (for Folders) & Select All Chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedTab == 5) {
                // Folder Breadcrumb
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val parent = currentFolder.parentFile
                    if (parent != null && parent.canRead()) {
                        Text(
                            text = "⬆ ..",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier
                                .clickable { currentFolder = parent }
                                .padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = currentFolder.name.ifBlank { "/" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "${filteredItems.size} items",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Select All / Deselect All Chip
            val areAllFilteredSelected = filteredItems.isNotEmpty() &&
                    filteredItems.all { item -> selectedItems.any { it.id == item.id } }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(8.dp))
                    .clickable {
                        if (areAllFilteredSelected) {
                            val ids = filteredItems.map { it.id }.toSet()
                            selectedItems.removeAll { ids.contains(it.id) }
                        } else {
                            filteredItems.forEach { item ->
                                if (selectedItems.none { it.id == item.id }) {
                                    selectedItems.add(item)
                                }
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (areAllFilteredSelected) strings.deselectAll else strings.selectAll,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentCyan
                )
            }
        }

        // Item List
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredItems, key = { it.id }) { item ->
                val isSelected = selectedItems.any { it.id == item.id }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) SurfaceDark.copy(alpha = 0.9f) else SurfaceCard)
                        .border(
                            1.dp,
                            if (isSelected) AccentCyan.copy(alpha = 0.6f) else SurfaceBorderDark,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (selectedTab == 5 && item.mimeType == "inode/directory") {
                                // Navigate down into folder
                                val nextDir = File(item.path)
                                if (nextDir.isDirectory && nextDir.canRead()) {
                                    currentFolder = nextDir
                                }
                            } else {
                                if (isSelected) {
                                    selectedItems.removeAll { it.id == item.id }
                                } else {
                                    selectedItems.add(item)
                                }
                            }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail or Icon
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedTab) {
                            0 -> {
                                val appBitmap = repo.loadAppIconBitmap(item.packageName, item.path)
                                if (appBitmap != null) {
                                    Image(bitmap = appBitmap, contentDescription = item.name, modifier = Modifier.size(38.dp))
                                } else {
                                    Text("📦", fontSize = 22.sp)
                                }
                            }
                            1 -> {
                                if (item.thumbnailUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(item.thumbnailUri).crossfade(true).build(),
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("🖼️", fontSize = 22.sp)
                                }
                            }
                            2 -> {
                                if (item.thumbnailUri != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(item.thumbnailUri).crossfade(true).build(),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        val durationMs = item.durationMs ?: 0L
                                        if (durationMs > 0) {
                                            val durSec = durationMs / 1000
                                            val durStr = "%02d:%02d".format(durSec / 60, durSec % 60)
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 2.dp, vertical = 1.dp)
                                            ) {
                                                Text(durStr, fontSize = 7.sp, color = Color.White)
                                            }
                                        }
                                    }
                                } else {
                                    Text("🎬", fontSize = 22.sp)
                                }
                            }
                            3 -> Text("🎵", fontSize = 22.sp)
                            4 -> Text("📄", fontSize = 22.sp)
                            5 -> {
                                if (item.mimeType == "inode/directory") Text("📁", fontSize = 24.sp)
                                else Text("📄", fontSize = 22.sp)
                            }
                            else -> Text("📄", fontSize = 22.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = NetworkUtils.formatBytes(item.size),
                                fontSize = 10.sp,
                                color = TextSecondary
                            )

                            // App Version & Split APK / OBB Badge
                            if (selectedTab == 0 && item.packageName != null) {
                                val pkgDetails = getAppPackageDetails(context, item.packageName)
                                if (pkgDetails.versionName.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "v${pkgDetails.versionName}",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                                if (pkgDetails.hasSplitsOrObb) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SuccessGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("BUNDLE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                    }
                                }
                            }
                        }
                    }

                    if (selectedTab != 5 || item.mimeType != "inode/directory") {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (selectedItems.none { it.id == item.id }) selectedItems.add(item)
                                } else {
                                    selectedItems.removeAll { it.id == item.id }
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentCyan,
                                checkmarkColor = BackgroundDark,
                                uncheckedColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Bottom Send Action Bar
        if (selectedItems.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorderDark)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedItems.size} selected",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Total: ${NetworkUtils.formatBytes(totalSelectedSize)}",
                            fontSize = 11.sp,
                            color = AccentCyan
                        )
                    }

                    Button(
                        onClick = { onFilesSelected(selectedItems) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Send Selected (${selectedItems.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class AppPackageDetails(val versionName: String, val hasSplitsOrObb: Boolean)

private fun getAppPackageDetails(context: Context, packageName: String): AppPackageDetails {
    return try {
        val pm = context.packageManager
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        val appInfo = pkgInfo.applicationInfo
        val hasSplits = appInfo?.splitPublicSourceDirs?.isNotEmpty() == true
        val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")
        val hasObb = obbDir.exists() && (obbDir.listFiles()?.isNotEmpty() == true)
        AppPackageDetails(pkgInfo.versionName ?: "", hasSplits || hasObb)
    } catch (_: Exception) {
        AppPackageDetails("", false)
    }
}

private fun loadFolderItems(dir: File): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val files = dir.listFiles() ?: return items
    val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

    for (file in sorted) {
        if (file.name.startsWith(".")) continue
        val isDir = file.isDirectory
        items.add(
            MediaItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                size = if (isDir) 0L else file.length(),
                mimeType = if (isDir) "inode/directory" else getMimeType(file.extension),
                category = if (isDir) "folders" else "docs"
            )
        )
    }
    return items
}

private fun getMimeType(extension: String): String {
    return when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}
