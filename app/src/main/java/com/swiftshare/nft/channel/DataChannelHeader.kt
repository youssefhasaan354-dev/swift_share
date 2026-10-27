package com.swiftshare.nft.channel

import java.nio.ByteBuffer
import java.security.MessageDigest

data class DataChannelHeader(
    val token: String,
    val fileId: String,
    val offset: Long,
    val totalSize: Long,
    val fileName: String,
    val thumbnailBytes: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DataChannelHeader
        if (token != other.token) return false
        if (fileId != other.fileId) return false
        if (offset != other.offset) return false
        if (totalSize != other.totalSize) return false
        if (fileName != other.fileName) return false
        if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = token.hashCode()
        result = 31 * result + fileId.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + totalSize.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + thumbnailBytes.contentHashCode()
        return result
    }

    fun serialize(): ByteBuffer {
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        val nameBytes = fileName.toByteArray(Charsets.UTF_8)
        val paddedId = fileId.take(36).padEnd(36).toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(
            4 + 4 + tokenBytes.size + 36 + 8 + 8 + 4 + nameBytes.size + 4 + thumbnailBytes.size
        )
        buffer.put(MAGIC_BYTES)
        buffer.putInt(tokenBytes.size)
        buffer.put(tokenBytes)
        buffer.put(paddedId)
        buffer.putLong(offset)
        buffer.putLong(totalSize)
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        buffer.putInt(thumbnailBytes.size)
        buffer.put(thumbnailBytes)
        buffer.flip()
        return buffer
    }

    companion object {
        val MAGIC_BYTES = byteArrayOf('S'.code.toByte(), 'W'.code.toByte(), 'F'.code.toByte(), 'T'.code.toByte())

        fun isMagic(bytes: ByteArray): Boolean {
            if (bytes.size < 4) return false
            return bytes[0] == 'S'.code.toByte() &&
                    bytes[1] == 'W'.code.toByte() &&
                    bytes[2] == 'F'.code.toByte() &&
                    bytes[3] == 'T'.code.toByte()
        }

        fun validateToken(expectedToken: String?, receivedToken: String): Boolean {
            if (expectedToken.isNullOrBlank()) return true
            return MessageDigest.isEqual(
                expectedToken.toByteArray(Charsets.UTF_8),
                receivedToken.toByteArray(Charsets.UTF_8)
            )
        }

        fun parse(buffer: ByteBuffer): DataChannelHeader? {
            if (buffer.remaining() < 4) return null
            val magic = ByteArray(4)
            buffer.get(magic)
            if (!isMagic(magic)) return null

            if (buffer.remaining() < 4) return null
            val tokenLen = buffer.int
            if (tokenLen < 0 || buffer.remaining() < tokenLen) return null
            val tokenBytes = ByteArray(tokenLen)
            buffer.get(tokenBytes)
            val token = String(tokenBytes, Charsets.UTF_8).trim()

            if (buffer.remaining() < 36 + 8 + 8 + 4) return null
            val fileIdBytes = ByteArray(36)
            buffer.get(fileIdBytes)
            val fileId = String(fileIdBytes, Charsets.UTF_8).trim()
            val offset = buffer.long
            val totalSize = buffer.long
            val nameLen = buffer.int
            if (nameLen < 0 || buffer.remaining() < nameLen) return null
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val fileName = String(nameBytes, Charsets.UTF_8)

            val thumbBytes = if (buffer.remaining() >= 4) {
                val thumbLen = buffer.int
                if (thumbLen > 0 && buffer.remaining() >= thumbLen) {
                    val bytes = ByteArray(thumbLen)
                    buffer.get(bytes)
                    bytes
                } else ByteArray(0)
            } else ByteArray(0)

            return DataChannelHeader(
                token = token,
                fileId = fileId,
                offset = offset,
                totalSize = totalSize,
                fileName = fileName,
                thumbnailBytes = thumbBytes
            )
        }
    }
}

const val CHUNK_MAX_SIZE = 64 * 1024 // 64 KB

data class StreamAuthHeader(
    val token: String,
    val streamIndex: Byte
) {
    fun serialize(): ByteBuffer {
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + 4 + tokenBytes.size + 1)
        buffer.put(MAGIC_BYTES)
        buffer.putInt(tokenBytes.size)
        buffer.put(tokenBytes)
        buffer.put(streamIndex)
        buffer.flip()
        return buffer
    }

    companion object {
        val MAGIC_BYTES = byteArrayOf('S'.code.toByte(), 'W'.code.toByte(), 'S'.code.toByte(), 'A'.code.toByte())

        fun parse(buffer: ByteBuffer): StreamAuthHeader? {
            if (buffer.remaining() < 4) return null
            val magic = ByteArray(4)
            buffer.get(magic)
            if (!magic.contentEquals(MAGIC_BYTES)) return null
            if (buffer.remaining() < 4) return null
            val tokenLen = buffer.int
            if (tokenLen < 0 || buffer.remaining() < tokenLen + 1) return null
            val tokenBytes = ByteArray(tokenLen)
            buffer.get(tokenBytes)
            val streamIndex = buffer.get()
            return StreamAuthHeader(String(tokenBytes, Charsets.UTF_8).trim(), streamIndex)
        }
    }
}

data class ChunkHeader(
    val streamId: Byte,
    val fileId: String,
    val offset: Long,
    val length: Int,
    val crc32: Long,
    val isEof: Boolean
) {
    fun serialize(): ByteBuffer {
        val paddedId = fileId.take(36).padEnd(36).toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(HEADER_SIZE)
        buffer.put(MAGIC_BYTES)
        buffer.put(streamId)
        buffer.put(paddedId)
        buffer.putLong(offset)
        buffer.putInt(length)
        buffer.putLong(crc32)
        buffer.put(if (isEof) 1.toByte() else 0.toByte())
        buffer.flip()
        return buffer
    }

    companion object {
        const val HEADER_SIZE = 4 + 1 + 36 + 8 + 4 + 8 + 1 // 62 bytes
        val MAGIC_BYTES = byteArrayOf('S'.code.toByte(), 'W'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte())

        fun isMagic(bytes: ByteArray): Boolean {
            if (bytes.size < 4) return false
            return bytes[0] == 'S'.code.toByte() &&
                    bytes[1] == 'W'.code.toByte() &&
                    bytes[2] == 'C'.code.toByte() &&
                    bytes[3] == 'K'.code.toByte()
        }

        fun computeCrc32(data: ByteArray, offset: Int = 0, length: Int = data.size): Long {
            val crc = java.util.zip.CRC32()
            crc.update(data, offset, length)
            return crc.value
        }

        fun parse(buffer: ByteBuffer): ChunkHeader? {
            if (buffer.remaining() < HEADER_SIZE) return null
            val magic = ByteArray(4)
            buffer.get(magic)
            if (!isMagic(magic)) return null
            val streamId = buffer.get()
            val fileIdBytes = ByteArray(36)
            buffer.get(fileIdBytes)
            val fileId = String(fileIdBytes, Charsets.UTF_8).trim()
            val offset = buffer.long
            val length = buffer.int
            val crc32 = buffer.long
            val isEof = buffer.get() != 0.toByte()
            return ChunkHeader(streamId, fileId, offset, length, crc32, isEof)
        }

        sealed class ValidationResult {
            object Valid : ValidationResult()
            data class Invalid(val reason: String) : ValidationResult()
        }

        fun validate(
            header: ChunkHeader,
            fileSize: Long,
            validFileIds: Set<String>
        ): ValidationResult {
            if (header.fileId !in validFileIds) {
                return ValidationResult.Invalid("Unknown fileId: ${header.fileId}")
            }
            if (header.offset < 0) {
                return ValidationResult.Invalid("Negative offset: ${header.offset}")
            }
            if (header.length < 0 || header.length > CHUNK_MAX_SIZE) {
                return ValidationResult.Invalid("Chunk length out of bounds [0..$CHUNK_MAX_SIZE]: ${header.length}")
            }
            if (header.offset % CHUNK_MAX_SIZE != 0L) {
                return ValidationResult.Invalid("Offset is not a multiple of 64KB: ${header.offset}")
            }
            if (header.isEof) {
                return ValidationResult.Valid
            }
            if (header.offset >= fileSize) {
                return ValidationResult.Invalid("Offset >= fileSize: ${header.offset} >= $fileSize")
            }
            if (header.offset + header.length > fileSize) {
                return ValidationResult.Invalid("Offset + length exceeds fileSize: ${header.offset} + ${header.length} > $fileSize")
            }
            val expectedLength = minOf(CHUNK_MAX_SIZE.toLong(), fileSize - header.offset).toInt()
            if (header.length != expectedLength) {
                return ValidationResult.Invalid("Length ${header.length} does not match expected length $expectedLength")
            }
            return ValidationResult.Valid
        }
    }
}

