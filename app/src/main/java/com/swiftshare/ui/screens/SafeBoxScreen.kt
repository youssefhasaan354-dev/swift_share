package com.swiftshare.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.swiftshare.SwiftShareApp
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.NetworkUtils
import com.swiftshare.safebox.SafeBoxAuthManager
import com.swiftshare.safebox.SafeBoxVault
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.DangerRed
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SafeBoxScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val dao = remember { SwiftShareApp.database.safeBoxDao() }
    val vault = remember { SafeBoxVault(context, dao) }
    val vaultItems by vault.allVaultItems.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var isUnlocked by remember { mutableStateOf(false) }
    var isSettingUpPin by remember { mutableStateOf(!SafeBoxAuthManager.isPinSet(context)) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Real File Picker for encryption
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var encryptedCount = 0
                for (uri in uris) {
                    val mime = context.contentResolver.getType(uri) ?: "*/*"
                    val success = vault.encryptUriToVault(uri, mime)
                    if (success) encryptedCount++
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Encrypted $encryptedCount file(s) into vault", Toast.LENGTH_SHORT).show()
                }
            }
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
                text = strings.safeBoxTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (!isUnlocked) {
            // PIN Entry Lock Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Text("🔒", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isSettingUpPin) "Set Master Vault PIN" else "Encrypted SafeBox Vault",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isSettingUpPin) "Create a secure 4-digit PIN for first-time setup" else "Hardware-backed AndroidKeyStore master key",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8) pinInput = it },
                        label = { Text(if (isSettingUpPin) "Enter New PIN (4-8 digits)" else "Enter Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = SurfaceBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isSettingUpPin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { if (it.length <= 8) confirmPinInput = it },
                            label = { Text("Confirm Master PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = SurfaceBorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorMessage!!, color = DangerRed, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (isSettingUpPin) {
                                if (pinInput.length < 4) {
                                    errorMessage = "PIN must be at least 4 digits"
                                } else if (pinInput != confirmPinInput) {
                                    errorMessage = "PINs do not match"
                                } else {
                                    SafeBoxAuthManager.setPin(context, pinInput)
                                    isSettingUpPin = false
                                    isUnlocked = true
                                }
                            } else {
                                if (SafeBoxAuthManager.verifyPin(context, pinInput)) {
                                    isUnlocked = true
                                    errorMessage = null
                                } else {
                                    errorMessage = "Incorrect PIN. Try again."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (isSettingUpPin) "Save & Open Vault" else strings.safeBoxUnlock, fontWeight = FontWeight.Bold)
                    }

                    // Biometrics Button
                    if (!isSettingUpPin && SafeBoxAuthManager.canAuthenticateBiometrics(context)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                (context as? FragmentActivity)?.let { activity ->
                                    SafeBoxAuthManager.promptBiometrics(
                                        activity = activity,
                                        onSuccess = { isUnlocked = true },
                                        onError = { err -> errorMessage = err }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("👆 " + strings.safeBoxBiometric, fontSize = 13.sp, color = AccentCyan)
                        }
                    }
                }
            }
        } else {
            // Unlocked Vault Files View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${vaultItems.size} Encrypted File(s)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Button(
                    onClick = {
                        // Real file picker launcher for Photos, Videos, Docs, APKs
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+ Encrypt File", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (vaultItems.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.safeBoxEmpty,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vaultItems) { item ->
                        val icon = when {
                            item.mimeType.startsWith("image/") -> "🖼️"
                            item.mimeType.startsWith("video/") -> "🎬"
                            item.mimeType.startsWith("audio/") -> "🎵"
                            item.originalName.endsWith(".apk") -> "📦"
                            else -> "📄"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCard)
                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(icon, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.originalName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    NetworkUtils.formatBytes(item.fileSize),
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }

                            // 1-Click Preview
                            Button(
                                onClick = {
                                    scope.launch {
                                        val temp = vault.decryptToCache(item)
                                        if (temp != null) {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    temp
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, item.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Preview ${item.originalName}"))
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "No app found to preview this file", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp).padding(end = 6.dp)
                            ) {
                                Text("Preview", fontSize = 10.sp, color = AccentCyan)
                            }

                            // Decrypt & Restore
                            Button(
                                onClick = {
                                    scope.launch {
                                        @Suppress("DEPRECATION")
                                        val restoreDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        val success = vault.decryptAndRestore(item, restoreDir)
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                Toast.makeText(context, "Restored to Downloads", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Restore failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Restore", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
