package com.swiftshare.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

enum class ApkRiskLevel {
    SAFE,
    MEDIUM_RISK,
    HIGH_RISK
}

data class ApkSecurityReport(
    val apkFile: File,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val certSha256: String,
    val dangerousPermissions: List<String>,
    val riskLevel: ApkRiskLevel
)

object ApkInstaller {

    private val HIGH_RISK_PERMISSIONS = setOf(
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.INSTALL_PACKAGES"
    )

    private val MEDIUM_RISK_PERMISSIONS = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    )

    /**
     * Inspects an APK package file, verifying its signing certificate SHA-256
     * and auditing dangerous permissions before prompting for installation.
     */
    fun inspectApk(context: Context, apkFile: File): ApkSecurityReport {
        var pkgName = apkFile.nameWithoutExtension
        var label = apkFile.nameWithoutExtension
        var vName = "1.0"
        var vCode = 1L
        var certSha256 = "N/A"
        val dangerousPerms = mutableListOf<String>()

        try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
            }

            val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            if (pkgInfo != null) {
                pkgName = pkgInfo.packageName ?: pkgName
                pkgInfo.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = apkFile.absolutePath
                    appInfo.publicSourceDir = apkFile.absolutePath
                    val rawLabel = pm.getApplicationLabel(appInfo)
                    if (!rawLabel.isNullOrBlank()) {
                        label = rawLabel.toString()
                    }
                }
                vName = pkgInfo.versionName ?: "1.0"
                vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }

                // Extract signatures
                val sigBytes = extractSignatureBytes(pkgInfo)
                if (sigBytes != null) {
                    certSha256 = computeCertSha256(sigBytes)
                }

                // Audit permissions
                pkgInfo.requestedPermissions?.forEach { perm ->
                    if (HIGH_RISK_PERMISSIONS.contains(perm) || MEDIUM_RISK_PERMISSIONS.contains(perm)) {
                        dangerousPerms.add(perm)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ApkInstaller", "Failed to inspect APK ${apkFile.name}: ${e.message}", e)
        }

        val riskLevel = when {
            dangerousPerms.any { HIGH_RISK_PERMISSIONS.contains(it) } -> ApkRiskLevel.HIGH_RISK
            dangerousPerms.isNotEmpty() -> ApkRiskLevel.MEDIUM_RISK
            else -> ApkRiskLevel.SAFE
        }

        return ApkSecurityReport(
            apkFile = apkFile,
            packageName = pkgName,
            appName = label,
            versionName = vName,
            versionCode = vCode,
            certSha256 = certSha256,
            dangerousPermissions = dangerousPerms,
            riskLevel = riskLevel
        )
    }

    private fun extractSignatureBytes(pkgInfo: android.content.pm.PackageInfo): ByteArray? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pkgInfo.signingInfo
                if (signingInfo != null) {
                    val signers = if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                    signers?.firstOrNull()?.toByteArray()
                } else null
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun computeCertSha256(bytes: ByteArray): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Throwable) {
            "UNKNOWN"
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Installs split APK bundles via Android PackageInstaller session.
     */
    fun installSplitApks(context: Context, apkFiles: List<File>) {
        val validFiles = apkFiles.filter { it.exists() && it.isFile }
        if (validFiles.isEmpty()) return

        if (validFiles.size == 1) {
            installApk(context, validFiles[0])
            return
        }

        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { activeSession ->
                for (file in validFiles) {
                    file.inputStream().use { input ->
                        activeSession.openWrite(file.name, 0, file.length()).use { output ->
                            input.copyTo(output)
                            activeSession.fsync(output)
                        }
                    }
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(context.packageName)
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    sessionId,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE else 0)
                )
                activeSession.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            AppLogger.e("ApkInstaller", "Failed to install split APK bundle: ${e.message}", e, notifyUser = true)
        }
    }

    fun openFile(context: Context, file: File, mimeType: String = "*/*") {
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("ApkInstaller", "Failed to start install activity: ${e.message}", e, notifyUser = true)
        }
    }

    fun openUriOrFile(context: Context, uriOrPath: String, mimeType: String = "*/*") {
        try {
            val uri = if (uriOrPath.startsWith("content://")) {
                Uri.parse(uriOrPath)
            } else {
                val f = File(uriOrPath.removePrefix("file://"))
                if (!f.exists()) return
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("ApkInstaller", "Failed to open $uriOrPath: ${e.message}", e, notifyUser = true)
        }
    }
}
