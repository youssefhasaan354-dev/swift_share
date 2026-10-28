package com.swiftshare.common

import com.swiftshare.nft.channel.models.FileManifestItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeduplicationTest {

    private class MockPublisher(
        private val existingEntries: MutableMap<String, ExistingFileEntry> = mutableMapOf()
    ) : ReceivedFilePublisher {
        val queriedNames = mutableListOf<String>()

        fun addExisting(entry: ExistingFileEntry) {
            existingEntries[entry.name] = entry
        }

        override fun findExisting(name: String): ExistingFileEntry? {
            queriedNames.add(name)
            return existingEntries[name]
        }

        override suspend fun publish(
            partFile: File,
            finalName: String,
            mimeType: String,
            category: String
        ): PublishedFile {
            return PublishedFile("content://mock/files/$finalName")
        }
    }

    @Test
    fun testSameNameSameSizeSameHash_isSkippedAsDuplicate() {
        val publisher = MockPublisher().apply {
            addExisting(
                ExistingFileEntry(
                    name = "photo.jpg",
                    size = 1048576L,
                    uriString = "content://media/external/images/media/42",
                    sha256 = "a1b2c3d4e5f67890abcdef"
                )
            )
        }

        val incomingItem = FileManifestItem(
            fileId = "f1",
            name = "photo.jpg",
            size = 1048576L,
            sha256 = "a1b2c3d4e5f67890abcdef",
            mimeType = "image/jpeg"
        )

        val result = FileDeduplicator.evaluate(incomingItem, publisher)

        assertTrue("Identical file (name + size + hash match) MUST be marked duplicate and skipped", result.isDuplicate)
        assertEquals("Final name must remain original name", "photo.jpg", result.finalName)
        assertNotNull("Existing entry must be returned for linking", result.existingEntry)
        assertEquals("content://media/external/images/media/42", result.existingEntry?.uriString)
        assertTrue("Publisher findExisting query must be used", publisher.queriedNames.contains("photo.jpg"))
    }

    @Test
    fun testSameNameDifferentSize_isSavedAsUniqueNameWithSuffix() {
        val publisher = MockPublisher().apply {
            addExisting(
                ExistingFileEntry(
                    name = "photo.jpg",
                    size = 1048576L,
                    uriString = "content://media/external/images/media/42",
                    sha256 = "a1b2c3d4e5f67890abcdef"
                )
            )
        }

        val incomingItem = FileManifestItem(
            fileId = "f2",
            name = "photo.jpg",
            size = 2097152L, // Different size: 2 MB vs 1 MB
            sha256 = "different_hash_content",
            mimeType = "image/jpeg"
        )

        val result = FileDeduplicator.evaluate(incomingItem, publisher)

        assertFalse("Different file with same name MUST NOT be marked duplicate", result.isDuplicate)
        assertEquals("Disambiguated name must be 'name (1).ext'", "photo (1).jpg", result.finalName)
    }

    @Test
    fun testSameNameSameSizeDifferentHash_isSavedAsUniqueNameWithSuffix() {
        val publisher = MockPublisher().apply {
            addExisting(
                ExistingFileEntry(
                    name = "document.pdf",
                    size = 50000L,
                    uriString = "content://media/external/files/99",
                    sha256 = "hash_alpha_original"
                )
            )
        }

        val incomingItem = FileManifestItem(
            fileId = "f3",
            name = "document.pdf",
            size = 50000L, // Same size: 50,000 bytes
            sha256 = "hash_beta_modified", // But different content hash!
            mimeType = "application/pdf"
        )

        val result = FileDeduplicator.evaluate(incomingItem, publisher)

        assertFalse("File with different hash MUST NOT be marked duplicate, even if size matches", result.isDuplicate)
        assertEquals("Disambiguated name must be 'name (1).ext'", "document (1).pdf", result.finalName)
    }

    @Test
    fun testNonExistentFile_isNotDuplicateAndKeepsOriginalName() {
        val publisher = MockPublisher()

        val incomingItem = FileManifestItem(
            fileId = "f4",
            name = "presentation.pptx",
            size = 123456L,
            sha256 = "hash_new_file",
            mimeType = "application/vnd.ms-powerpoint"
        )

        val result = FileDeduplicator.evaluate(incomingItem, publisher)

        assertFalse("Non-existent file must not be duplicate", result.isDuplicate)
        assertEquals("presentation.pptx", result.finalName)
    }

    @Test
    fun testMultipleCollisions_incrementsCounterToNextAvailableSuffix() {
        val publisher = MockPublisher().apply {
            addExisting(ExistingFileEntry("video.mp4", 1000L, "content://1"))
            addExisting(ExistingFileEntry("video (1).mp4", 1000L, "content://2"))
        }

        val incomingItem = FileManifestItem(
            fileId = "f5",
            name = "video.mp4",
            size = 5000L, // Different size
            mimeType = "video/mp4"
        )

        val result = FileDeduplicator.evaluate(incomingItem, publisher)

        assertFalse(result.isDuplicate)
        assertEquals("video (2).mp4", result.finalName)
    }

    @Test
    fun testQueryAbstractionUsedWithoutFileList() {
        val publisher = MockPublisher().apply {
            addExisting(ExistingFileEntry("contract.pdf", 500L, "content://contract"))
        }

        val incomingItem = FileManifestItem(
            fileId = "f6",
            name = "contract.pdf",
            size = 500L,
            mimeType = "application/pdf"
        )

        val result = FileDeduplicator.evaluate(incomingItem, publisher)

        assertTrue(result.isDuplicate)
        // Verify publisher abstraction was invoked
        assertTrue("findExisting must have been queried directly via publisher abstraction", publisher.queriedNames.contains("contract.pdf"))
    }
}
