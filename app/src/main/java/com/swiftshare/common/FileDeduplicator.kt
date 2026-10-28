package com.swiftshare.common

import com.swiftshare.nft.channel.models.FileManifestItem

data class DeduplicationResult(
    val finalName: String,
    val isDuplicate: Boolean,
    val existingEntry: ExistingFileEntry? = null
)

object FileDeduplicator {

    /**
     * Evaluates an incoming manifest item against existing files discovered via
     * the [ReceivedFilePublisher] query abstraction (e.g. MediaStore or target volume).
     *
     * Invariants strictly enforced:
     * 1. Same name AND same size AND (matching sha256 or sha256 omitted by either) -> duplicate (skip).
     * 2. Same name BUT different size -> NOT duplicate; received under unique name "name (1).ext".
     * 3. Same name AND same size BUT different sha256 -> NOT duplicate; received under unique name "name (1).ext".
     * 4. File does not exist at destination -> NOT duplicate; received under original sanitized name.
     */
    fun evaluate(
        item: FileManifestItem,
        publisher: ReceivedFilePublisher
    ): DeduplicationResult {
        val sanitizedOriginal = SafeFileName.sanitize(item.name)
        val existing = publisher.findExisting(sanitizedOriginal)

        if (existing == null) {
            return DeduplicationResult(
                finalName = sanitizedOriginal,
                isDuplicate = false,
                existingEntry = null
            )
        }

        val sizeMatches = (existing.size == item.size)
        val hashMatches = if (!item.sha256.isNullOrBlank() && !existing.sha256.isNullOrBlank()) {
            existing.sha256.equals(item.sha256, ignoreCase = true)
        } else {
            true // If SHA-256 is not provided by either offer or destination, size match determines duplicate
        }

        if (sizeMatches && hashMatches) {
            // Identical file: skip transfer
            return DeduplicationResult(
                finalName = sanitizedOriginal,
                isDuplicate = true,
                existingEntry = existing
            )
        }

        // Different content with identical file name: disambiguate safely
        val uniqueName = SafeFileName.resolveUniqueName(sanitizedOriginal) { candidate ->
            publisher.findExisting(candidate) != null
        }

        return DeduplicationResult(
            finalName = uniqueName,
            isDuplicate = false,
            existingEntry = existing
        )
    }
}
