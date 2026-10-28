package com.swiftshare.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.swiftshare.SwiftShareApp
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaStoreRepository
import com.swiftshare.explorer.search.GlobalSearchEngine
import com.swiftshare.explorer.search.SearchCategory
import com.swiftshare.explorer.search.SearchResultItem
import com.swiftshare.ui.components.ZipViewerDialog
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val scope = rememberCoroutineScope()

    val searchEngine = remember {
        GlobalSearchEngine(
            mediaRepo = MediaStoreRepository(context),
            sessionDao = SwiftShareApp.database.sessionDao()
        )
    }

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SearchCategory.ALL) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Inspecting zip file
    var inspectingZipFile by remember { mutableStateOf<File?>(null) }

    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Debounced search trigger
    fun triggerSearch(q: String, cat: SearchCategory) {
        searchJob?.cancel()
        if (q.trim().isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return
        }
        searchJob = scope.launch {
            isSearching = true
            delay(200) // 200ms debounce
            val results = searchEngine.search(q, cat)
            searchResults = results
            isSearching = false
        }
    }

    LaunchedEffect(selectedCategory) {
        if (query.isNotBlank()) {
            triggerSearch(query, selectedCategory)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        // Top bar with back button and title
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
                text = strings.globalSearchTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input Box
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                triggerSearch(newQuery, selectedCategory)
            },
            placeholder = {
                Text(
                    text = strings.searchFilesAppsHistory,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Text("🔍", fontSize = 18.sp)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Text(
                        text = "✕",
                        fontSize = 16.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clickable {
                                query = ""
                                searchResults = emptyList()
                                isSearching = false
                            }
                            .padding(8.dp)
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = SurfaceBorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SearchCategory.values()) { cat ->
                val isSelected = cat == selectedCategory
                val title = if (currentLang == AppLanguage.AR) cat.titleAr else cat.titleEn
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PrimaryBlue else SurfaceDark)
                        .border(
                            1.dp,
                            if (isSelected) AccentCyan else SurfaceBorderDark,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            selectedCategory = cat
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.icon, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Status / Count Header
        if (query.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${searchResults.size} results",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentCyan
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentCyan
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Search Results List / Empty State
        if (query.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔎", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.searchHintSubtitle,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (searchResults.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📁", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.noResultsFound,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults, key = { "${it.category.name}_${it.id}_${it.pathOrUri}" }) { item ->
                    SearchResultCard(
                        item = item,
                        currentLang = currentLang,
                        onOpen = {
                            val f = File(item.pathOrUri)
                            if (f.exists() && item.name.endsWith(".zip", ignoreCase = true)) {
                                inspectingZipFile = f
                            } else if (item.category == SearchCategory.AUDIO) {
                                onNavigate("media_player")
                            } else if (item.category == SearchCategory.APPS && item.extraInfo != null) {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(item.extraInfo)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            } else {
                                try {
                                    val uri = if (item.pathOrUri.startsWith("content://")) {
                                        Uri.parse(item.pathOrUri)
                                    } else {
                                        Uri.fromFile(File(item.pathOrUri))
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, item.mimeType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // ZIP Viewer Dialog
    inspectingZipFile?.let { zipFile ->
        ZipViewerDialog(
            zipFile = zipFile,
            onDismiss = { inspectingZipFile = null }
        )
    }
}

@Composable
fun SearchResultCard(
    item: SearchResultItem,
    currentLang: AppLanguage,
    onOpen: () -> Unit
) {
    val categoryBadge = if (currentLang == AppLanguage.AR) item.category.titleAr else item.category.titleEn
    val isZip = item.name.endsWith(".zip", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(item.category.icon, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = categoryBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentCyan
                )
                if (item.size > 0) {
                    Text(
                        text = " • ${NetworkUtils.formatBytes(item.size)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                if (!item.extraInfo.isNullOrBlank()) {
                    Text(
                        text = " • ${item.extraInfo}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isZip) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryBlue.copy(alpha = 0.3f))
                    .border(1.dp, AccentCyan, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("ZIP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
            }
        }
    }
}

