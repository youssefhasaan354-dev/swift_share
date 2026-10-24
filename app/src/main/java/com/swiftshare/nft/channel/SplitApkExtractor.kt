package com.swiftshare.nft.channel

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.swiftshare.nft.channel.models.FileManifestItem
import java.io.File
import java.util.UUID

data class AppPackageBundle(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val baseApk: File,
    val splitApks: List<File>,
    val obbFiles: List<File>,
    val totalSizeBytes: Long
)

object SplitApkExtractor {

    fun extractAppBundle(context: Context, packageName: String): AppPackageBundle? {
        return try {
            val pm = context.packageManager
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }

            val appInfo = pkgInfo.applicationInfo ?: return null
            val appName = pm.getApplicationLabel(appInfo).toString()
            val versionName = pkgInfo.versionName ?: "1.0"

            val baseApk = File(appInfo.sourceDir)
            val splitApks = mutableListOf<File>()

            appInfo.splitPublicSourceDirs?.forEach { path ->
                val f = File(path)
                if (f.exists()) splitApks.add(f)
            }

            // Check OBB directories
            val obbFiles = mutableListOf<File>()
            val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")
            if (obbDir.exists() && obbDir.isDirectory) {
                obbDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".obb")) {
                        obbFiles.add(file)
                    }
                }
            }

            var totalSize = baseApk.length()
            splitApks.forEach { totalSize += it.length() }
            obbFiles.forEach { totalSize += it.length() }

            AppPackageBundle(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                baseApk = baseApk,
                splitApks = splitApks,
                obbFiles = obbFiles,
                totalSizeBytes = totalSize
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun toManifestItem(bundle: AppPackageBundle): FileManifestItem {
        val splitPaths = bundle.splitApks.map { it.absolutePath }
        val obbPaths = bundle.obbFiles.map { it.absolutePath }

        return FileManifestItem(
            fileId = "apk_" + UUID.randomUUID().toString(),
            name = "${bundle.appName}.apk",
            relativePath = "apps/${bundle.packageName}",
            mimeType = "application/vnd.android.package-archive",
            size = bundle.totalSizeBytes,
            isApkBundle = bundle.splitApks.isNotEmpty() || bundle.obbFiles.isNotEmpty(),
            splitApkPaths = splitPaths,
            obbDirPaths = obbPaths
        )
    }
}
