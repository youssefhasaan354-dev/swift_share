package com.swiftshare.ui.screens

import android.os.Environment
import android.widget.Toast
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.clone.CloneCategory
import com.swiftshare.clone.PhoneCloneEngine
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.explorer.MediaItem
import com.swiftshare.explorer.MediaStoreRepository
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
import java.util.UUID

@Composable
fun PhoneCloneScreen(
    onStartClone: (List<MediaItem>, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val mediaRepo = remember { MediaStoreRepository(context) }
    val cloneEngine = remember { PhoneCloneEngine(context, mediaRepo) }
    val scope = rememberCoroutineScope()

    var isOldPhoneRole by remember { mutableStateOf(true) }
    val manifestCounts = remember { mutableStateMapOf<CloneCategory, Int>() }
    val selectedCategories = remember { mutableStateMapOf<CloneCategory, Boolean>() }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val counts = cloneEngine.prepareCloneManifest()
        counts.forEach { (cat, count) ->
            manifestCounts[cat] = count
            selectedCategories[cat] = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(18.dp)
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
                text = strings.phoneCloneTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Role Selector: Old Phone vs New Phone
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isOldPhoneRole) PrimaryBlue else SurfaceDark)
                    .clickable { isOldPhoneRole = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.phoneCloneOld,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isOldPhoneRole) AccentCyan else SurfaceDark)
                    .clickable { isOldPhoneRole = false }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.phoneCloneNew,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (!isOldPhoneRole) BackgroundDark else TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (isOldPhoneRole) {
            Text(
                text = "Select Categories to Migrate",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Categories List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(CloneCategory.values()) { category ->
                    val count = manifestCounts[category] ?: 0
                    val isChecked = selectedCategories[category] ?: false

                    val icon = when (category) {
                        CloneCategory.CONTACTS -> "📇"
                        CloneCategory.CALL_LOGS -> "📞"
                        CloneCategory.SMS -> "💬"
                        CloneCategory.PHOTOS -> "🖼️"
                        CloneCategory.VIDEOS -> "🎬"
                        CloneCategory.MUSIC -> "🎵"
                        CloneCategory.DOCUMENTS -> "📄"
                        CloneCategory.APPS -> "📦"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp))
                            .clickable { selectedCategories[category] = !isChecked }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(icon, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name.replace("_", " "),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "$count item(s) found",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { selectedCategories[category] = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentCyan,
                                checkmarkColor = BackgroundDark,
                                uncheckedColor = TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    isLoading = true
                    scope.launch(Dispatchers.IO) {
                        val itemsToSend = mutableListOf<MediaItem>()

                        if (selectedCategories[CloneCategory.CONTACTS] == true) {
                            val vcf = cloneEngine.exportContactsVcf()
                            itemsToSend.add(
                                MediaItem(
                                    id = UUID.randomUUID().toString(),
                                    name = "contacts_clone.vcf",
                                    path = vcf.absolutePath,
                                    size = vcf.length(),
                                    mimeType = "text/x-vcard",
                                    category = "docs"
                                )
                            )
                        }

                        if (selectedCategories[CloneCategory.CALL_LOGS] == true) {
                            val logs = cloneEngine.exportCallLogs()
                            itemsToSend.add(
                                MediaItem(
                                    id = UUID.randomUUID().toString(),
                                    name = "call_logs.json",
                                    path = logs.absolutePath,
                                    size = logs.length(),
                                    mimeType = "application/json",
                                    category = "docs"
                                )
                            )
                        }

                        if (selectedCategories[CloneCategory.PHOTOS] == true) {
                            itemsToSend.addAll(mediaRepo.getPhotos())
                        }
                        if (selectedCategories[CloneCategory.VIDEOS] == true) {
                            itemsToSend.addAll(mediaRepo.getVideos())
                        }
                        if (selectedCategories[CloneCategory.MUSIC] == true) {
                            itemsToSend.addAll(mediaRepo.getAudio())
                        }
                        if (selectedCategories[CloneCategory.DOCUMENTS] == true) {
                            itemsToSend.addAll(mediaRepo.getDocuments())
                        }
                        if (selectedCategories[CloneCategory.APPS] == true) {
                            itemsToSend.addAll(mediaRepo.getInstalledApps())
                        }

                        withContext(Dispatchers.Main) {
                            isLoading = false
                            onStartClone(itemsToSend, true)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !isLoading
            ) {
                Text(
                    text = if (isLoading) "Packaging Data…" else "Start Clone Migration (Send)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // New Phone Receive Mode View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📲", fontSize = 54.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "New Phone Receiver Ready",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When connected to your Old Phone, incoming contacts (VCF) and call logs will be restored automatically, and APKs will be queued for installation.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            onStartClone(emptyList(), false)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Open Radar Receiver", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                @Suppress("DEPRECATION")
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                cloneEngine.autoRestoreReceivedCloneFiles(downloadsDir)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Scanned & restored Contacts/Call Logs!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark)
                    ) {
                        Text("Restore Received Clone Files Now", fontSize = 12.sp, color = AccentCyan)
                    }
                }
            }
        }
    }
}
