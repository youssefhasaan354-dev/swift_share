package com.swiftshare.nft.channel

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object ChunkManager {

    private val resumeOffsets = ConcurrentHashMap<String, Long>()

    fun getResumeOffset(fileId: String): Long {
        return resumeOffsets[fileId] ?: 0L
    }

    fun saveResumeOffset(fileId: String, offset: Long) {
        resumeOffsets[fileId] = offset
    }

    fun clearResumeOffset(fileId: String) {
        resumeOffsets.remove(fileId)
    }

    fun calculateSha256(file: File, bufferSize: Int = 128 * 1024): String {
        if (!file.exists() || !file.canRead()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(bufferSize)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return true // Skip if sender didn't compute hash
        val actualHash = calculateSha256(file)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
}
