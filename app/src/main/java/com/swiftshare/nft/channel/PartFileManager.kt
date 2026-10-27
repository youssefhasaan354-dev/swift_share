package com.swiftshare.nft.channel

import com.swiftshare.common.SafeFileName
import com.swiftshare.nft.channel.models.PartFileMeta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.BitSet

object PartFileManager {

    private val json = Json { ignoreUnknownKeys = true }

    fun getMetaFile(partFile: File): File = File(partFile.parentFile, "${partFile.name}.meta")

    fun readMeta(metaFile: File): PartFileMeta? {
        if (!metaFile.exists() || metaFile.length() == 0L) return null
        return try {
            json.decodeFromString<PartFileMeta>(metaFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun writeMeta(metaFile: File, meta: PartFileMeta) {
        val text = json.encodeToString(meta)
        val parent = metaFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        metaFile.writeText(text, Charsets.UTF_8)
    }

    fun bitSetToBase64(bitSet: BitSet): String {
        val bytes = bitSet.toByteArray()
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            encodeJvm(bytes)
        }
    }

    fun base64ToBitSet(base64: String, totalChunks: Int): BitSet {
        if (base64.isBlank()) return BitSet(totalChunks)
        return try {
            val bytes = try {
                android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            } catch (_: Throwable) {
                decodeJvm(base64)
            }
            BitSet.valueOf(bytes)
        } catch (_: Exception) {
            BitSet(totalChunks)
        }
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
    private fun encodeJvm(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
    private fun decodeJvm(base64: String): ByteArray {
        return java.util.Base64.getDecoder().decode(base64)
    }

    /**
     * Resolves the part file and meta file for a given transfer item.
     *
     * Invariants enforced:
     * 1. Same fileId & same size: Resume existing .part using .part.meta.
     * 2. Same fileId & size changed: Discard stale .part and .part.meta safely.
     * 3. Different fileId with identical fileName: Disambiguate with collision-free suffix
     *    (e.g. photo (1).jpg.part) so the two files never overwrite or corrupt each other.
     */
    fun resolvePartFileAndMeta(
        downloadDir: File,
        fileId: String,
        fileName: String,
        totalSize: Long
    ): Pair<File, File> {
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val sanitized = SafeFileName.sanitize(fileName)
        var candidatePart = File(downloadDir, "$sanitized.part")
        var candidateMeta = getMetaFile(candidatePart)

        if (candidatePart.exists()) {
            val existingMeta = readMeta(candidateMeta)
            if (existingMeta != null) {
                if (existingMeta.fileId == fileId) {
                    if (existingMeta.size == totalSize) {
                        // Invariant 1: Matching fileId and size -> resume
                        return Pair(candidatePart, candidateMeta)
                    } else {
                        // Invariant 2: Stale meta (file size changed) -> discard safely
                        candidatePart.delete()
                        candidateMeta.delete()
                        return Pair(candidatePart, candidateMeta)
                    }
                } else {
                    // Invariant 3: Different fileId with identical filename!
                    // Disambiguate to prevent collision with other files
                    val dot = sanitized.lastIndexOf('.')
                    val nameWithoutExt = if (dot > 0) sanitized.substring(0, dot) else sanitized
                    val ext = if (dot > 0) sanitized.substring(dot) else ""

                    var counter = 1
                    while (candidatePart.exists()) {
                        val meta = readMeta(getMetaFile(candidatePart))
                        if (meta != null && meta.fileId == fileId) {
                            if (meta.size == totalSize) {
                                return Pair(candidatePart, getMetaFile(candidatePart))
                            } else {
                                candidatePart.delete()
                                getMetaFile(candidatePart).delete()
                                return Pair(candidatePart, getMetaFile(candidatePart))
                            }
                        }
                        candidatePart = File(downloadDir, "$nameWithoutExt ($counter)$ext.part")
                        counter++
                    }
                    candidateMeta = getMetaFile(candidatePart)
                    return Pair(candidatePart, candidateMeta)
                }
            } else {
                // No valid meta on disk for existing .part file -> discard corrupted/stale part
                candidatePart.delete()
                candidateMeta.delete()
                return Pair(candidatePart, candidateMeta)
            }
        }

        return Pair(candidatePart, candidateMeta)
    }
}
