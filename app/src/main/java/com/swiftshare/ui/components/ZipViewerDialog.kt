package com.swiftshare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.zip.ExtractionResult
import com.swiftshare.explorer.zip.ZipContent
import com.swiftshare.explorer.zip.ZipEntryItem
import com.swiftshare.explorer.zip.ZipExplorer
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ZipViewerDialog(
    zipFile: File,
    onDismiss: () -> Unit,
    onExtractionComplete: ((ExtractionResult) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var zipContent by remember { mutableStateOf<ZipContent?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    var extractionResult by remember { mutableStateOf<ExtractionResult?>(null) }

    // Previews
    var previewTextContent by remember { mutableStateOf<Pair<String, String>?>(null) } // (filename, content)
    var previewImageBytes by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    LaunchedEffect(zipFile) {
        val content = withContext(Dispatchers.IO) {
            ZipExplorer.browse(zipFile)
        }
        zipContent = content
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📦", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zipFile.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (zipContent != null) {
                            Text(
                                text = "${zipContent!!.totalEntries} items • ${NetworkUtils.formatBytes(zipContent!!.totalUncompressedBytes)} uncompressed",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Text(
                        text = "✕",
                        fontSize = 20.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search inside zip
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter entries inside archive…", color = TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = SurfaceBorderDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Entries List
                if (zipContent == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                } else {
                    val filteredEntries = remember(zipContent, searchQuery) {
                        val all = zipContent!!.entries
                        if (searchQuery.isBlank()) all else all.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    if (filteredEntries.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No matching files in archive", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredEntries) { entry ->
                                ZipEntryRow(
                                    entry = entry,
                                    onPreview = {
                                        val ext = entry.name.substringAfterLast('.', "").lowercase()
                                        scope.launch(Dispatchers.IO) {
                                            if (ext in setOf("txt", "json", "xml", "csv", "md", "html", "kt", "java", "py", "sh", "properties")) {
                                                val text = ZipExplorer.previewText(zipFile, entry.name)
                                                if (text != null) {
                                                    previewTextContent = Pair(entry.name, text)
                                                }
                                            } else if (ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")) {
                                                val bytes = ZipExplorer.previewBytes(zipFile, entry.name)
                                                if (bytes != null) {
                                                    previewImageBytes = Pair(entry.name, bytes)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions: Extract All Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", color = TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (!isExtracting && zipContent != null) {
                                isExtracting = true
                                scope.launch(Dispatchers.IO) {
                                    val baseName = zipFile.nameWithoutExtension
                                    val targetDir = File(zipFile.parentFile ?: File("."), "${baseName}_extracted")
                                    val result = ZipExplorer.extract(zipFile, targetDir)
                                    withContext(Dispatchers.Main) {
                                        isExtracting = false
                                        extractionResult = result
                                        onExtractionComplete?.invoke(result)
                                    }
                                }
                            }
                        },
                        enabled = !isExtracting && zipContent != null,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extracting…", fontSize = 12.sp)
                        } else {
                            Text("Extract All", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // ── Preview & Result Dialogs ────────────────────────────────────────────

    // Text Preview Dialog
    if (previewTextContent != null) {
        val (name, text) = previewTextContent!!
        AlertDialog(
            onDismissRequest = { previewTextContent = null },
            title = { Text("📄 $name", fontSize = 14.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(SurfaceDark)
                        .padding(10.dp)
                ) {
                    LazyColumn {
                        item {
                            Text(text, fontSize = 11.sp, color = TextSecondary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { previewTextContent = null }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                    Text("Done")
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Extraction Result Dialog
    if (extractionResult != null) {
        val res = extractionResult!!
        AlertDialog(
            onDismissRequest = { extractionResult = null },
            title = { Text("Extraction Complete", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✅ Extracted ${res.extractedFiles.size} files (${NetworkUtils.formatBytes(res.totalBytesExtracted)})", color = AccentCyan, fontSize = 13.sp)
                    if (res.skippedSuspiciousFiles.isNotEmpty()) {
                        Text(
                            "⚠️ Blocked ${res.skippedSuspiciousFiles.size} suspicious Zip-Slip path traversal attempts for system security.",
                            color = androidx.compose.ui.graphics.Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { extractionResult = null }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                    Text("OK")
                }
            },
            containerColor = SurfaceCard
        )
    }
}

@Composable
private fun ZipEntryRow(
    entry: ZipEntryItem,
    onPreview: () -> Unit
) {
    val icon = when {
        entry.isDirectory -> "📁"
        entry.name.endsWith(".txt", true) || entry.name.endsWith(".json", true) || entry.name.endsWith(".xml", true) -> "📄"
        entry.name.endsWith(".jpg", true) || entry.name.endsWith(".png", true) || entry.name.endsWith(".webp", true) -> "🖼️"
        entry.name.endsWith(".mp3", true) || entry.name.endsWith(".m4a", true) || entry.name.endsWith(".wav", true) -> "🎵"
        entry.name.endsWith(".mp4", true) || entry.name.endsWith(".mkv", true) -> "🎬"
        entry.name.endsWith(".apk", true) -> "📦"
        else -> "📄"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .clickable(enabled = !entry.isDirectory, onClick = onPreview)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                fontSize = 12.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDirectory) {
                Text(
                    text = "${NetworkUtils.formatBytes(entry.uncompressedSize)} (comp: ${NetworkUtils.formatBytes(entry.compressedSize)})",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
        if (!entry.isDirectory) {
            Text("Preview", fontSize = 10.sp, color = AccentCyan)
        }
    }
}

