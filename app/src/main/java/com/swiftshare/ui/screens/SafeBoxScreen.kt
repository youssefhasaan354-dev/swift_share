package com.swiftshare.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.swiftshare.SwiftShareApp
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.NetworkUtils
import com.swiftshare.data.entities.SafeBoxEntity
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
import java.io.File

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
    var recoveryQuestionInput by remember { mutableStateOf("What is your favorite secret city?") }
    var recoveryAnswerInput by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog States
    var showForgotPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var viewingPhotoEntity by remember { mutableStateOf<SafeBoxEntity?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // File Picker for encryption
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
                text = strings.safeBoxTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isUnlocked) {
                // Change PIN button
                Text(
                    text = "⚙️ Change PIN",
                    fontSize = 12.sp,
                    color = AccentCyan,
                    modifier = Modifier
                        .clickable { showChangePinDialog = true }
                        .padding(end = 12.dp)
                )
                // Lock button
                Text(
                    text = "🔒 Lock",
                    fontSize = 12.sp,
                    color = DangerRed,
                    modifier = Modifier.clickable {
                        isUnlocked = false
                        pinInput = ""
                    }
                )
            }
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
                        text = if (isSettingUpPin) "Create a secure PIN and recovery question" else "Hardware-backed AES-256 AndroidKeyStore master key",
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

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = recoveryQuestionInput,
                            onValueChange = { recoveryQuestionInput = it },
                            label = { Text("Security Recovery Question") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = SurfaceBorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = recoveryAnswerInput,
                            onValueChange = { recoveryAnswerInput = it },
                            label = { Text("Secret Recovery Answer") },
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                                } else if (recoveryAnswerInput.trim().isEmpty()) {
                                    errorMessage = "Please enter a secret recovery answer"
                                } else {
                                    SafeBoxAuthManager.setPin(context, pinInput)
                                    SafeBoxAuthManager.setRecoveryQuestion(context, recoveryQuestionInput, recoveryAnswerInput)
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

                    // Biometrics Button & Forgot PIN
                    if (!isSettingUpPin) {
                        if (SafeBoxAuthManager.canAuthenticateBiometrics(context)) {
                            Spacer(modifier = Modifier.height(10.dp))
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

                        if (SafeBoxAuthManager.isRecoveryConfigured(context)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Forgot PIN?",
                                fontSize = 12.sp,
                                color = AccentCyan,
                                modifier = Modifier
                                    .clickable { showForgotPinDialog = true }
                                    .padding(4.dp)
                            )
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
                    items(vaultItems, key = { it.id }) { item ->
                        val isImage = item.mimeType.startsWith("image/") ||
                            item.originalName.endsWith(".jpg", ignoreCase = true) ||
                            item.originalName.endsWith(".png", ignoreCase = true) ||
                            item.originalName.endsWith(".webp", ignoreCase = true)

                        val icon = when {
                            isImage -> "🖼️"
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

                            // In-App Decrypted Photo Viewer (if Image) or Preview
                            if (isImage) {
                                Button(
                                    onClick = {
                                        viewingPhotoEntity = item
                                        isLoadingPreview = true
                                        scope.launch {
                                            val temp = vault.decryptToCache(item)
                                            if (temp != null) {
                                                val bmp = withContext(Dispatchers.IO) {
                                                    BitmapFactory.decodeFile(temp.absolutePath)
                                                }
                                                previewBitmap = bmp
                                                SafeBoxAuthManager.secureShred(temp)
                                            }
                                            isLoadingPreview = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp).padding(end = 6.dp)
                                ) {
                                    Text("View", fontSize = 10.sp, color = AccentCyan)
                                }
                            } else {
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
                                                        Toast.makeText(context, "No app found to preview", Toast.LENGTH_SHORT).show()
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

    // ── Forgot PIN Recovery Dialog ──────────────────────────────────────────
    if (showForgotPinDialog) {
        val question = SafeBoxAuthManager.getRecoveryQuestion(context) ?: "Secret Question"
        var answer by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var forgotError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showForgotPinDialog = false },
            title = { Text("Reset Vault PIN", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text("Security Question:", fontSize = 12.sp, color = TextSecondary)
                    Text(question, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Your Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8) newPin = it },
                        label = { Text("Enter New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (forgotError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(forgotError!!, color = DangerRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length < 4) {
                            forgotError = "PIN must be at least 4 digits"
                        } else if (SafeBoxAuthManager.resetPinWithRecovery(context, answer, newPin)) {
                            showForgotPinDialog = false
                            isUnlocked = true
                            Toast.makeText(context, "PIN successfully reset!", Toast.LENGTH_SHORT).show()
                        } else {
                            forgotError = "Incorrect answer. Try again."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Reset PIN & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── Change PIN Dialog ───────────────────────────────────────────────────
    if (showChangePinDialog) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var changeError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Change Vault PIN", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { if (it.length <= 8) oldPin = it },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8) newPin = it },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 8) confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (changeError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(changeError!!, color = DangerRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length < 4) {
                            changeError = "PIN must be at least 4 digits"
                        } else if (newPin != confirmPin) {
                            changeError = "New PINs do not match"
                        } else if (SafeBoxAuthManager.changePin(context, oldPin, newPin)) {
                            showChangePinDialog = false
                            Toast.makeText(context, "PIN updated successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            changeError = "Incorrect current PIN"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save New PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── In-Memory Decrypted Photo Viewer Dialog ─────────────────────────────
    viewingPhotoEntity?.let { entity ->
        Dialog(onDismissRequest = {
            viewingPhotoEntity = null
            previewBitmap = null
        }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BackgroundDark)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entity.originalName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            fontSize = 18.sp,
                            color = TextSecondary,
                            modifier = Modifier
                                .clickable {
                                    viewingPhotoEntity = null
                                    previewBitmap = null
                                }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLoadingPreview) {
                        CircularProgressIndicator(color = AccentCyan)
                    } else if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = entity.originalName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text("Could not preview photo", color = TextSecondary, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "🔒 Zero-trace view: Decrypted in volatile memory and wiped",
                        fontSize = 11.sp,
                        color = AccentCyan
                    )
                }
            }
        }
    }
}
