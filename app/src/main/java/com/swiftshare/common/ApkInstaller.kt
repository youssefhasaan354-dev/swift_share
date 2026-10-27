package com.swiftshare.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {

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
            com.swiftshare.common.AppLogger.e("ApkInstaller", "Failed to start install activity: ${e.message}", e, notifyUser = true)
        }
    }
}
