package com.swiftshare.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object AvatarUtils {
    const val MAX_AVATAR_BYTES = 32 * 1024 // 32 KB

    fun compressUriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream) ?: return null

            // Scale down to max 256x256 for avatar thumbnail
            val maxDim = 256
            val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else original

            var quality = 85
            var bytes: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                bytes = stream.toByteArray()
                quality -= 15
            } while (bytes.size > MAX_AVATAR_BYTES && quality > 10)

            if (bytes.size <= MAX_AVATAR_BYTES) {
                encodeBase64(bytes)
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AvatarUtils", "Failed to compress avatar: ${e.message}", e)
            null
        }
    }

    fun isUnderMaxLimit(base64Str: String): Boolean {
        // Approximate decoded byte length from base64
        val padding = when {
            base64Str.endsWith("==") -> 2
            base64Str.endsWith("=") -> 1
            else -> 0
        }
        val estimatedBytes = (base64Str.length * 3 / 4) - padding
        return estimatedBytes <= MAX_AVATAR_BYTES
    }

    fun encodeBase64(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            encodeJvm(bytes)
        }
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
    private fun encodeJvm(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    fun decodeBase64(base64: String): ByteArray {
        return try {
            android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            decodeJvm(base64)
        }
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
    private fun decodeJvm(base64: String): ByteArray {
        return java.util.Base64.getDecoder().decode(base64)
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = decodeBase64(base64)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Throwable) {
            null
        }
    }
}
