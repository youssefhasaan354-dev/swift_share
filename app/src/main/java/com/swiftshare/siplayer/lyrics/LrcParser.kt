package com.swiftshare.siplayer.lyrics

import java.io.File
import java.util.regex.Pattern

data class LrcLine(
    val timestampMs: Long,
    val text: String
)

data class LrcLyrics(
    val lines: List<LrcLine>,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)

object LrcParser {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?]")
    private val META_TAG_PATTERN = Pattern.compile("\\[([a-zA-Z]+):([^]]*)]")

    fun parse(content: String): LrcLyrics {
        val parsedLines = mutableListOf<LrcLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        val rawLines = content.lines()
        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // Check for metadata tags
            val metaMatcher = META_TAG_PATTERN.matcher(trimmed)
            if (metaMatcher.matches()) {
                val tag = metaMatcher.group(1)?.lowercase()
                val value = metaMatcher.group(2)?.trim()
                when (tag) {
                    "ti" -> title = value
                    "ar" -> artist = value
                    "al" -> album = value
                }
                continue
            }

            // Extract all timestamps from the line
            val timeMatcher = TIME_TAG_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (timeMatcher.find()) {
                val minutes = timeMatcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = timeMatcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = timeMatcher.group(3) ?: "0"
                val fractionMs = if (fractionStr.length == 2) {
                    (fractionStr.toLongOrNull() ?: 0L) * 10
                } else {
                    fractionStr.toLongOrNull() ?: 0L
                }
                val totalMs = (minutes * 60 + seconds) * 1000 + fractionMs
                timestamps.add(totalMs)
                lastMatchEnd = timeMatcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val text = trimmed.substring(lastMatchEnd).trim()
                for (ts in timestamps) {
                    parsedLines.add(LrcLine(ts, text))
                }
            }
        }

        // Sort chronologically
        parsedLines.sortBy { it.timestampMs }

        return LrcLyrics(
            lines = parsedLines,
            title = title,
            artist = artist,
            album = album
        )
    }

    fun getLineIndexAt(lyrics: LrcLyrics, positionMs: Long): Int {
        if (lyrics.lines.isEmpty()) return -1
        if (positionMs < lyrics.lines.first().timestampMs) return 0

        var low = 0
        var high = lyrics.lines.size - 1
        var result = 0

        while (low <= high) {
            val mid = (low + high) / 2
            if (lyrics.lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun findLrcFileForAudio(audioPath: String): File? {
        val audioFile = File(audioPath)
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        // 1. Same directory: <baseName>.lrc
        val directLrc = File(parent, "$baseName.lrc")
        if (directLrc.exists() && directLrc.isFile) return directLrc

        // 2. Case-insensitive check in same directory
        val matchedInParent = parent.listFiles { f ->
            f.isFile && f.name.equals("$baseName.lrc", ignoreCase = true)
        }
        if (!matchedInParent.isNullOrEmpty()) return matchedInParent.first()

        // 3. Subdirectory "lyrics": lyrics/<baseName>.lrc
        val lyricsDir = File(parent, "lyrics")
        if (lyricsDir.exists() && lyricsDir.isDirectory) {
            val subLrc = File(lyricsDir, "$baseName.lrc")
            if (subLrc.exists() && subLrc.isFile) return subLrc
        }

        return null
    }
}

