package com.swiftshare.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.swiftshare.SwiftShareApp
import com.swiftshare.common.ApkInstaller
import com.swiftshare.common.ApkSecurityReport
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.AvatarUtils
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.NetworkUtils
import com.swiftshare.common.StorageDestinationManager
import com.swiftshare.common.StorageVolumeType
import com.swiftshare.data.entities.SessionEntity
import com.swiftshare.data.entities.TransferItemEntity
import com.swiftshare.explorer.MediaItem
import com.swiftshare.ui.SwiftShareViewModel
import com.swiftshare.ui.components.ApkSecurityDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun TransferHistoryScreen(
    viewModel: SwiftShareViewModel? = null,
    onNavigate: ((String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val isAr = currentLang == AppLanguage.AR
    val scope = rememberCoroutineScope()

    val sessionDao = remember { SwiftShareApp.database.sessionDao() }
    val sessions by sessionDao.getAllSessions().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sessions, 1: Received Files, 2: Me Profile & Settings
    var sessionFilter by remember { mutableIntStateOf(0) } // 0: All, 1: Received, 2: Sent

    val tabs = listOf(
        if (isAr) "الجلسات (${sessions.size})" else "Sessions (${sessions.size})",
        if (isAr) "الملفات المستلمة" else "Received Files",
        if (isAr) "ملفي والإعدادات" else "Me & Settings"
    )

    // APK Security inspection state
    var selectedApkReport by remember { mutableStateOf<ApkSecurityReport?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "←",
                    fontSize = 24.sp,
                    color = TextPrimary,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 16.dp)
                )
                Text(
                    text = strings.navHistory,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (selectedTab == 0 && sessions.isNotEmpty()) {
                Button(
                    onClick = {
                        scope.launch { sessionDao.clearAll() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isAr) "مسح السجل" else "Clear All", fontSize = 11.sp, color = DangerRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = AccentCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AccentCyan
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
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) AccentCyan else TextSecondary
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedTab) {
            0 -> {
                // ── Tab 0: Sessions View (Grouped by Transfer Session) ───────────────
                SessionsView(
                    sessions = sessions,
                    filter = sessionFilter,
                    onFilterChange = { sessionFilter = it },
                    isAr = isAr,
                    context = context,
                    viewModel = viewModel,
                    scope = scope,
                    onNavigate = onNavigate,
                    onInspectApk = { selectedApkReport = it },
                    onDeleteSession = { sId ->
                        scope.launch { sessionDao.deleteSessionAndItems(sId) }
                    }
                )
            }
            1 -> {
                // ── Tab 1: Received Files Browser ───────────────────────────────────
                ReceivedFilesBrowserView(
                    context = context,
                    isAr = isAr,
                    onInspectApk = { selectedApkReport = it }
                )
            }
            2 -> {
                // ── Tab 2: Me Profile & Settings ─────────────────────────────────────
                MeProfileSettingsView(
                    context = context,
                    isAr = isAr,
                    onNavigateToPermissions = { onNavigate?.invoke("permission_guide") }
                )
            }
        }
    }

    // APK Security Audit Dialog
    selectedApkReport?.let { report ->
        ApkSecurityDialog(
            report = report,
            onDismiss = { selectedApkReport = null },
            onProceedInstall = {
                ApkInstaller.installApk(context, report.apkFile)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SESSIONS VIEW (Grouped by Session)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SessionsView(
    sessions: List<SessionEntity>,
    filter: Int,
    onFilterChange: (Int) -> Unit,
    isAr: Boolean,
    context: Context,
    viewModel: SwiftShareViewModel?,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigate: ((String) -> Unit)?,
    onInspectApk: (ApkSecurityReport) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    val filteredSessions = remember(sessions, filter) {
        when (filter) {
            1 -> sessions.filter { it.isIncoming }
            2 -> sessions.filter { !it.isIncoming }
            else -> sessions
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                if (isAr) "الكل" else "All",
                if (isAr) "المستلمة 📥" else "Received 📥",
                if (isAr) "المرسلة 📤" else "Sent 📤"
            )
            filters.forEachIndexed { index, label ->
                val isSel = filter == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) PrimaryBlue else SurfaceCard)
                        .border(1.dp, if (isSel) AccentCyan else SurfaceBorderDark, RoundedCornerShape(20.dp))
                        .clickable { onFilterChange(index) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color.White else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredSessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isAr) "لا توجد جلسات سابقة" else "No transfer sessions recorded.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            val expandedState = remember { mutableStateMapOf<String, Boolean>() }

            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredSessions, key = { it.sessionId }) { session ->
                    val isExpanded = expandedState[session.sessionId] ?: false
                    SessionCard(
                        session = session,
                        isExpanded = isExpanded,
                        onToggleExpand = { expandedState[session.sessionId] = !isExpanded },
                        isAr = isAr,
                        context = context,
                        onReSend = {
                            if (viewModel != null && onNavigate != null) {
                                SwiftShareApp.database.sessionDao().let { dao ->
                                    scope.launch(Dispatchers.IO) {
                                        val items = dao.getItemsForSessionSync(session.sessionId)
                                        val mediaItems = items.mapNotNull { itm ->
                                            val f = File(itm.uriOrPath)
                                            if (f.exists()) {
                                                MediaItem(
                                                    id = itm.fileId,
                                                    name = itm.name,
                                                    path = itm.uriOrPath,
                                                    size = itm.size,
                                                    mimeType = itm.mimeType,
                                                    category = "files"
                                                )
                                            } else null
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (mediaItems.isNotEmpty()) {
                                                viewModel.setSelectedFiles(mediaItems)
                                                onNavigate("radar_send")
                                            } else {
                                                Toast.makeText(context, if (isAr) "الملفات الأصلية غير موجودة" else "Original files not found on disk", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onDelete = { onDeleteSession(session.sessionId) },
                        onInspectApk = onInspectApk
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isAr: Boolean,
    context: Context,
    onReSend: () -> Unit,
    onDelete: () -> Unit,
    onInspectApk: (ApkSecurityReport) -> Unit
) {
    val dateStr = remember(session.startTime) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(session.startTime))
    }

    val statusColor = when (session.status) {
        "COMPLETED" -> SuccessGreen
        "IN_PROGRESS" -> AccentCyan
        "CANCELLED" -> DangerRed
        else -> Color(0xFFFFA726)
    }

    val dao = remember { SwiftShareApp.database.sessionDao() }
    val itemsFlow = remember(session.sessionId) { dao.getItemsForSession(session.sessionId) }
    val sessionItems by itemsFlow.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Session Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (session.isIncoming) SuccessGreen.copy(alpha = 0.2f) else PrimaryBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (session.isIncoming) "📥" else "📤", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "${session.peerName} (${if (session.isIncoming) if (isAr) "مستلم" else "Received" else if (isAr) "مرسل" else "Sent"})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "$dateStr • ${session.totalFiles} ${if (isAr) "ملفات" else "files"} (${NetworkUtils.formatBytes(session.transferredBytes)})",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = session.status,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Session Actions (Expand / Re-send / Delete)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isExpanded) (if (isAr) "إخفاء التفاصيل ▲" else "Hide Details ▲") else (if (isAr) "عرض التفاصيل (${sessionItems.size}) ▼" else "View Files (${sessionItems.size}) ▼"),
                fontSize = 11.sp,
                color = AccentCyan,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onToggleExpand() }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Re-send button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .clickable { onReSend() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (isAr) "إعادة إرسال 📤" else "Re-send 📤", fontSize = 10.sp, color = AccentCyan)
                }

                // Delete button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .clickable { onDelete() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (isAr) "حذف 🗑️" else "Delete 🗑️", fontSize = 10.sp, color = DangerRed)
                }
            }
        }

        // Expandable List of Files in Session
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sessionItems.forEach { item ->
                    SessionItemRow(
                        item = item,
                        context = context,
                        isAr = isAr,
                        onInspectApk = onInspectApk
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionItemRow(
    item: TransferItemEntity,
    context: Context,
    isAr: Boolean,
    onInspectApk: (ApkSecurityReport) -> Unit
) {
    val file = remember(item.uriOrPath) { File(item.uriOrPath) }
    val exists = remember(item.uriOrPath) { file.exists() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = "${NetworkUtils.formatBytes(item.size)} • ${item.status}",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        if (exists) {
            if (item.name.endsWith(".apk", ignoreCase = true)) {
                Button(
                    onClick = {
                        val report = ApkInstaller.inspectApk(context, file)
                        onInspectApk(report)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (isAr) "فحص وتثبيت" else "Audit & Install", fontSize = 10.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        ApkInstaller.openFile(context, file, item.mimeType)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (isAr) "فتح" else "Open", fontSize = 10.sp, color = AccentCyan)
                }
            }
        } else {
            Text(
                text = if (isAr) "غير موجود" else "Missing",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RECEIVED FILES BROWSER VIEW (Disk File Manager)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ReceivedFilesBrowserView(
    context: Context,
    isAr: Boolean,
    onInspectApk: (ApkSecurityReport) -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val downloadDir = remember(refreshKey) {
        StorageDestinationManager.getDestinationDirectory(context)
    }

    val diskFiles = remember(downloadDir, refreshKey) {
        if (downloadDir.exists() && downloadDir.isDirectory) {
            downloadDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".meta") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Path Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAr) "مجلد الحفظ الحالي:" else "Current Storage Directory:",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Text(
                    text = downloadDir.absolutePath,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentCyan,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .clickable { refreshKey++ }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("🔄", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (diskFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isAr) "لا توجد ملفات محفوظة في هذا المجلد" else "No downloaded files found in this folder.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(diskFiles, key = { it.absolutePath }) { file ->
                    DiskFileRow(
                        file = file,
                        context = context,
                        isAr = isAr,
                        onInspectApk = onInspectApk,
                        onDeleted = { refreshKey++ }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiskFileRow(
    file: File,
    context: Context,
    isAr: Boolean,
    onInspectApk: (ApkSecurityReport) -> Unit,
    onDeleted: () -> Unit
) {
    val dateStr = remember(file.lastModified()) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(file.lastModified()))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        val isApk = file.name.endsWith(".apk", ignoreCase = true)
        val icon = when {
            isApk -> "📦"
            file.name.matches(Regex(".*\\.(jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE)) -> "🖼️"
            file.name.matches(Regex(".*\\.(mp4|mkv|mov|avi)$", RegexOption.IGNORE_CASE)) -> "🎬"
            file.name.matches(Regex(".*\\.(mp3|wav|m4a|aac)$", RegexOption.IGNORE_CASE)) -> "🎵"
            file.name.endsWith(".pdf", ignoreCase = true) -> "📄"
            else -> "📁"
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = "${NetworkUtils.formatBytes(file.length())} • $dateStr",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isApk) {
                Button(
                    onClick = {
                        val report = ApkInstaller.inspectApk(context, file)
                        onInspectApk(report)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (isAr) "تثبيت" else "Install", fontSize = 10.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        ApkInstaller.openFile(context, file)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (isAr) "فتح" else "Open", fontSize = 10.sp, color = AccentCyan)
                }
            }

            // Share Chooser
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .clickable {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🔗", fontSize = 12.sp)
            }

            // Delete File
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .clickable {
                        if (file.delete()) {
                            onDeleted()
                            Toast.makeText(context, if (isAr) "تم حذف الملف" else "File deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🗑️", fontSize = 12.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ME PROFILE & SETTINGS VIEW (C2: Avatar/Name + C3: Destination Volume)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MeProfileSettingsView(
    context: Context,
    isAr: Boolean,
    onNavigateToPermissions: () -> Unit
) {
    val deviceName by LocaleManager.deviceName.collectAsState()
    val avatarEmoji by LocaleManager.avatarEmoji.collectAsState()
    val customAvatarBase64 by LocaleManager.customAvatarBase64.collectAsState()
    val currentLang by LocaleManager.currentLanguage.collectAsState()
    val activeVolumeType by StorageDestinationManager.selectedVolume.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }

    val customBitmap = remember(customAvatarBase64) {
        customAvatarBase64?.let { AvatarUtils.decodeBase64ToBitmap(it) }
    }

    val volumes = remember(activeVolumeType) {
        StorageDestinationManager.getAvailableVolumes(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Device Profile Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue)
                    .border(2.dp, AccentCyan, CircleShape),
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
                    Text(avatarEmoji, fontSize = 28.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(deviceName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Text(
                    text = if (customBitmap != null) {
                        if (isAr) "صورة مخصصة (<= 32 KB)" else "Custom Photo (<= 32 KB)"
                    } else {
                        if (isAr) "صورة تعبيرية: $avatarEmoji" else "Emoji Avatar: $avatarEmoji"
                    },
                    fontSize = 11.sp,
                    color = AccentCyan
                )
            }

            Button(
                onClick = { showProfileDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isAr) "تعديل" else "Edit", fontSize = 11.sp, color = AccentCyan)
            }
        }

        // 2. Storage Volume Selection Card (Task C3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = if (isAr) "مكان حفظ الملفات المستلمة (التخزين)" else "Receive Storage Destination",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            volumes.forEach { vol ->
                val isSelected = activeVolumeType == vol.type
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PrimaryBlue.copy(alpha = 0.2f) else SurfaceDark)
                        .border(1.dp, if (isSelected) AccentCyan else SurfaceBorderDark, RoundedCornerShape(12.dp))
                        .clickable {
                            StorageDestinationManager.setVolumeType(context, vol.type)
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vol.type == StorageVolumeType.INTERNAL) "💾" else "💳", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (vol.type == StorageVolumeType.INTERNAL) (if (isAr) "التخزين الداخلي" else "Internal Storage") else (if (isAr) "بطاقة الذاكرة (SD Card)" else "SD Card"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Free: ${NetworkUtils.formatBytes(vol.freeBytes)} / Total: ${NetworkUtils.formatBytes(vol.totalBytes)}",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) AccentCyan else SurfaceCard)
                            .border(2.dp, if (isSelected) AccentCyan else TextSecondary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 3. Permission Guide & Language Controls Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(if (isAr) "دليل الأذونات" else "Permissions Guide", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Text(if (isAr) "مراجعة وإصلاح أذونات التطبيق" else "Review & configure required permissions", fontSize = 11.sp, color = TextSecondary)
            }
            Button(
                onClick = onNavigateToPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isAr) "مراجعة" else "Review", fontSize = 11.sp)
            }
        }

        // 4. Language Switcher Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(if (isAr) "اللغة / Language" else "Language / اللغة", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Text(if (currentLang == AppLanguage.EN) "English (Active)" else "العربية (مفعل)", fontSize = 11.sp, color = AccentCyan)
            }
            Button(
                onClick = {
                    val next = if (currentLang == AppLanguage.EN) AppLanguage.AR else AppLanguage.EN
                    LocaleManager.setLanguage(next)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (currentLang == AppLanguage.EN) "العربية" else "English", fontSize = 12.sp)
            }
        }
    }

    // Device Edit Dialog (with built-in emojis + custom photo <= 32 KB)
    if (showProfileDialog) {
        val emojis = listOf("⚡", "🚀", "🦊", "🦁", "🐼", "🤖", "💎", "🔥", "👑", "🦅")
        var chosenAvatar by remember { mutableStateOf(avatarEmoji) }
        var inputName by remember { mutableStateOf(deviceName) }
        var customPhotoBase64 by remember { mutableStateOf(LocaleManager.customAvatarBase64.value) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val b64 = AvatarUtils.compressUriToBase64(context, uri)
                if (b64 != null) {
                    customPhotoBase64 = b64
                    chosenAvatar = "🖼️"
                } else {
                    Toast.makeText(context, if (isAr) "تعذر ضغط الصورة أقل من 32 KB" else "Failed to compress photo under 32 KB", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                    text = if (isAr) "اسم الجهاز والصورة الرمزية" else "Device Name & Avatar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Built-in Emojis (Row 1)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    emojis.take(5).forEach { emoji ->
                        val isSel = chosenAvatar == emoji && customPhotoBase64 == null
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSel) PrimaryBlue else SurfaceCard)
                                .clickable {
                                    chosenAvatar = emoji
                                    customPhotoBase64 = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Built-in Emojis (Row 2)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    emojis.drop(5).forEach { emoji ->
                        val isSel = chosenAvatar == emoji && customPhotoBase64 == null
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSel) PrimaryBlue else SurfaceCard)
                                .clickable {
                                    chosenAvatar = emoji
                                    customPhotoBase64 = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Photo Picker Option (<= 32 KB)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (customPhotoBase64 != null) (if (isAr) "تم تعيين صورة مخصصة (<= 32 KB)" else "Custom Photo Set (<= 32 KB)") else (if (isAr) "صورة من المعرض (<= 32 KB)" else "Photo from Gallery (<= 32 KB)"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (customPhotoBase64 != null) AccentCyan else TextPrimary
                        )
                    }
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("📷 Choose", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text(if (isAr) "اسم الجهاز" else "Device Nickname") },
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
                        Text(if (isAr) "إلغاء" else "Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (inputName.isNotBlank()) {
                                LocaleManager.setDeviceProfile(inputName.trim(), chosenAvatar, customPhotoBase64)
                            }
                            showProfileDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isAr) "حفظ" else "Save")
                    }
                }
            }
        }
    }
}
