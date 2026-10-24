package com.swiftshare.common

import java.io.File
import java.nio.charset.StandardCharsets

object SafeFileName {

    private const val MAX_BYTES = 200

    /**
     * Sanitizes a remote or user-provided file name:
     * - Keeps only the last path segment
     * - Strips control characters (0x00..0x1F, 0x7F) and NUL
     * - Eliminates directory traversal sequences (..)
     * - Limits name length to max 200 bytes UTF-8 while preserving extension
     * - Handles Arabic and Unicode characters cleanly
     */
    fun sanitize(rawName: String?, fallbackExtension: String = ""): String {
        if (rawName.isNullOrBlank()) {
            val ext = fallbackExtension.trim().trimStart('.').takeIf { it.isNotEmpty() }
            return if (ext != null) "file_${System.currentTimeMillis()}.$ext" else "file_${System.currentTimeMillis()}"
        }

        // 1. Normalize slashes and take only the last path segment
        var clean = rawName.replace('\\', '/')
        val lastSlash = clean.lastIndexOf('/')
        if (lastSlash != -1) {
            clean = clean.substring(lastSlash + 1)
        }

        // 2. Remove NUL, control chars (0x00..0x1F, 0x7F), and path separators
        clean = clean.filter { c ->
            c.code > 31 && c.code != 127 && c != '/' && c != '\\' && c != '\u0000'
        }

        // 3. Remove traversal ".." sequences repeatedly
        while (clean.contains("..")) {
            clean = clean.replace("..", "")
        }

        // 4. Strip leading/trailing dots and spaces
        clean = clean.trim().trim('.')

        // 5. If empty after stripping, generate fallback
        if (clean.isBlank()) {
            val ext = fallbackExtension.trim().trimStart('.').takeIf { it.isNotEmpty() }
            return if (ext != null) "file_${System.currentTimeMillis()}.$ext" else "file_${System.currentTimeMillis()}"
        }

        // 6. Enforce UTF-8 byte limit (max 200 bytes) while preserving extension
        return truncateToBytes(clean, MAX_BYTES)
    }

    /**
     * Resolves a destination file safely:
     * - Sanitizes the name
     * - De-duplicates if file exists: "name (1).ext", "name (2).ext"
     * - Verifies that the canonical path stays strictly inside destinationDir
     */
    fun resolveUniqueFile(destinationDir: File, rawName: String?, fallbackExtension: String = ""): File {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val sanitized = sanitize(rawName, fallbackExtension)
        val lastDot = sanitized.lastIndexOf('.')
        val baseName = if (lastDot > 0) sanitized.substring(0, lastDot) else sanitized
        val ext = if (lastDot > 0) sanitized.substring(lastDot) else ""

        var candidate = File(destinationDir, sanitized)
        var count = 1
        while (candidate.exists()) {
            candidate = File(destinationDir, "$baseName ($count)$ext")
            count++
        }

        // Canonical directory traversal verification
        val destCanonical = destinationDir.canonicalFile
        val candidateCanonical = candidate.canonicalFile
        val destPathWithSep = if (destCanonical.path.endsWith(File.separator)) {
            destCanonical.path
        } else {
            destCanonical.path + File.separator
        }

        if (!candidateCanonical.path.startsWith(destPathWithSep) && candidateCanonical != destCanonical) {
            throw SecurityException("Path traversal attempt detected: $rawName")
        }

        return candidate
    }

    private fun truncateToBytes(name: String, maxBytes: Int): String {
        val bytes = name.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= maxBytes) return name

        val lastDot = name.lastIndexOf('.')
        val ext = if (lastDot > 0 && lastDot < name.length - 1) name.substring(lastDot) else ""
        val extBytes = ext.toByteArray(StandardCharsets.UTF_8)

        val allowedBaseBytes = (maxBytes - extBytes.size).coerceAtLeast(1)
        val basePart = if (lastDot > 0) name.substring(0, lastDot) else name

        val sb = StringBuilder()
        var currentBytes = 0
        for (char in basePart) {
            val charBytes = char.toString().toByteArray(StandardCharsets.UTF_8).size
            if (currentBytes + charBytes > allowedBaseBytes) break
            sb.append(char)
            currentBytes += charBytes
        }

        val resultBase = sb.toString().trimEnd('.', ' ')
        return if (resultBase.isEmpty()) "file$ext" else "$resultBase$ext"
    }
}
