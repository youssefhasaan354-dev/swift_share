package com.swiftshare.safebox

import android.content.Context
import com.swiftshare.data.db.SafeBoxDao
import com.swiftshare.data.entities.SafeBoxEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class SafeBoxVault(
    private val context: Context,
    private val dao: SafeBoxDao
) {

    private val vaultDir = File(context.filesDir, "vault").apply {
        if (!exists()) mkdirs()
    }

    val allVaultItems: Flow<List<SafeBoxEntity>> = dao.getAllItems()

    suspend fun encryptAndMoveToVault(sourceFile: File, mimeType: String = "*/*"): Boolean {
        if (!sourceFile.exists() || !sourceFile.canRead()) return false

        val id = UUID.randomUUID().toString()
        val encryptedFile = File(vaultDir, "$id.enc")

        return try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(encryptedFile).use { output ->
                    CryptoManager.encryptStream(input, output)
                }
            }

            val entity = SafeBoxEntity(
                id = id,
                originalName = sourceFile.name,
                originalPath = sourceFile.absolutePath,
                encryptedPath = encryptedFile.absolutePath,
                fileSize = sourceFile.length(),
                mimeType = mimeType
            )
            dao.insert(entity)

            // Delete original file to hide from MediaStore
            sourceFile.delete()
            true
        } catch (e: Exception) {
            encryptedFile.delete()
            com.swiftshare.common.AppLogger.e("SafeBoxVault", "Failed to encrypt to vault: ${e.message}", e, notifyUser = true)
            false
        }
    }

    suspend fun decryptAndRestore(entity: SafeBoxEntity, restoreTargetDir: File): Boolean {
        val encFile = File(entity.encryptedPath)
        if (!encFile.exists()) return false

        val restoredFile = File(restoreTargetDir, entity.originalName)
        return try {
            FileInputStream(encFile).use { input ->
                FileOutputStream(restoredFile).use { output ->
                    CryptoManager.decryptStream(input, output)
                }
            }
            encFile.delete()
            dao.delete(entity)
            true
        } catch (e: Exception) {
            restoredFile.delete()
            com.swiftshare.common.AppLogger.e("SafeBoxVault", "Failed to restore from vault: ${e.message}", e, notifyUser = true)
            false
        }
    }

    suspend fun encryptUriToVault(uri: android.net.Uri, mimeType: String = "*/*"): Boolean {
        val id = UUID.randomUUID().toString()
        val encryptedFile = File(vaultDir, "$id.enc")

        var originalName = "encrypted_file"
        var fileSize = 0L

        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx != -1) originalName = cursor.getString(nameIdx) ?: "file"
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(encryptedFile).use { output ->
                    CryptoManager.encryptStream(input, output)
                }
            } ?: return false

            if (fileSize == 0L) fileSize = encryptedFile.length()

            val entity = SafeBoxEntity(
                id = id,
                originalName = originalName,
                originalPath = uri.toString(),
                encryptedPath = encryptedFile.absolutePath,
                fileSize = fileSize,
                mimeType = mimeType
            )
            dao.insert(entity)
            true
        } catch (e: Exception) {
            encryptedFile.delete()
            com.swiftshare.common.AppLogger.e("SafeBoxVault", "Failed to encrypt URI to vault: ${e.message}", e, notifyUser = true)
            false
        }
    }

    suspend fun decryptToCache(entity: SafeBoxEntity): File? {
        val encFile = File(entity.encryptedPath)
        if (!encFile.exists()) return null
        val tempFile = File(context.cacheDir, "preview_${entity.originalName}")
        return try {
            FileInputStream(encFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    CryptoManager.decryptStream(input, output)
                }
            }
            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    }
}
