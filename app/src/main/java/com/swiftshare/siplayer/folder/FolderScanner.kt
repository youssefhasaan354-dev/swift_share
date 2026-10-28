package com.swiftshare.siplayer.folder

import com.swiftshare.explorer.MediaItem
import java.io.File

data class AudioFile(
    val path: String,
    val name: String,
    val title: String,
    val artist: String = "",
    val size: Long = 0L,
    val durationMs: Long = 0L
)

data class AudioFolder(
    val path: String,
    val name: String,
    val songCount: Int,
    val songs: List<AudioFile>
)

object FolderScanner {

    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus")

    fun groupMediaItemsByFolder(items: List<MediaItem>): List<AudioFolder> {
        val folderMap = mutableMapOf<String, MutableList<AudioFile>>()

        for (item in items) {
            val file = File(item.path)
            val parentDir = file.parentFile ?: continue
            val parentPath = parentDir.absolutePath

            val audio = AudioFile(
                path = item.path,
                name = item.name,
                title = item.name.substringBeforeLast('.'),
                artist = "",
                size = item.size,
                durationMs = item.durationMs ?: 0L
            )
            folderMap.getOrPut(parentPath) { mutableListOf() }.add(audio)
        }

        return folderMap.map { (path, songs) ->
            val folderFile = File(path)
            AudioFolder(
                path = path,
                name = folderFile.name.ifEmpty { "Audio" },
                songCount = songs.size,
                songs = songs.sortedBy { it.name.lowercase() }
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun scanDirectory(rootDir: File, minSizeBytes: Long = 1024L): List<AudioFolder> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val folderMap = mutableMapOf<String, MutableList<AudioFile>>()
        scanRecursive(rootDir, folderMap, minSizeBytes)

        return folderMap.map { (path, songs) ->
            val folderFile = File(path)
            AudioFolder(
                path = path,
                name = folderFile.name.ifEmpty { "Audio" },
                songCount = songs.size,
                songs = songs.sortedBy { it.name.lowercase() }
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun scanRecursive(
        dir: File,
        folderMap: MutableMap<String, MutableList<AudioFile>>,
        minSizeBytes: Long
    ) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory && !f.name.startsWith(".")) {
                scanRecursive(f, folderMap, minSizeBytes)
            } else if (f.isFile && f.length() >= minSizeBytes) {
                val ext = f.extension.lowercase()
                if (AUDIO_EXTENSIONS.contains(ext)) {
                    val parentPath = dir.absolutePath
                    val audio = AudioFile(
                        path = f.absolutePath,
                        name = f.name,
                        title = f.nameWithoutExtension,
                        artist = "",
                        size = f.length(),
                        durationMs = 0L
                    )
                    folderMap.getOrPut(parentPath) { mutableListOf() }.add(audio)
                }
            }
        }
    }
}

