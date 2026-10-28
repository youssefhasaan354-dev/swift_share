package com.swiftshare.siplayer.playlist

import android.content.Context
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

data class PlaylistItem(
    val path: String,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0L
)

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<PlaylistItem> = emptyList()
)

class PlaylistManager(
    private val storageFile: File
) {
    constructor(context: Context) : this(File(context.filesDir, "playlists.dat"))

    private val playlists = mutableListOf<Playlist>()

    init {
        loadFromDisk()
    }

    @Synchronized
    fun getPlaylists(): List<Playlist> {
        return playlists.toList()
    }

    @Synchronized
    fun getPlaylist(id: String): Playlist? {
        return playlists.find { it.id == id }
    }

    @Synchronized
    fun createPlaylist(name: String): Playlist {
        val trimmed = name.trim().ifEmpty { "New Playlist" }
        val playlist = Playlist(name = trimmed)
        playlists.add(playlist)
        saveToDisk()
        return playlist
    }

    @Synchronized
    fun deletePlaylist(id: String): Boolean {
        val removed = playlists.removeAll { it.id == id }
        if (removed) saveToDisk()
        return removed
    }

    @Synchronized
    fun renamePlaylist(id: String, newName: String): Boolean {
        val index = playlists.indexOfFirst { it.id == id }
        if (index == -1) return false
        val current = playlists[index]
        playlists[index] = current.copy(name = newName.trim().ifEmpty { current.name })
        saveToDisk()
        return true
    }

    @Synchronized
    fun addSongToPlaylist(playlistId: String, song: PlaylistItem): Boolean {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index == -1) return false
        val current = playlists[index]
        if (current.items.any { it.path == song.path }) return false // Avoid duplicates
        val updatedItems = current.items + song
        playlists[index] = current.copy(items = updatedItems)
        saveToDisk()
        return true
    }

    @Synchronized
    fun removeSongFromPlaylist(playlistId: String, path: String): Boolean {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index == -1) return false
        val current = playlists[index]
        val updatedItems = current.items.filterNot { it.path == path }
        playlists[index] = current.copy(items = updatedItems)
        saveToDisk()
        return true
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        try {
            playlists.clear()
            var currentPlaylist: Playlist? = null
            val currentItems = mutableListOf<PlaylistItem>()

            storageFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("PL\t")) {
                    currentPlaylist?.let {
                        playlists.add(it.copy(items = currentItems.toList()))
                    }
                    currentItems.clear()
                    val parts = trimmed.split("\t")
                    if (parts.size >= 4) {
                        currentPlaylist = Playlist(
                            id = parts[1],
                            createdAt = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                            name = decode(parts[3]),
                            items = emptyList()
                        )
                    }
                } else if (trimmed.startsWith("ITEM\t")) {
                    val parts = trimmed.split("\t")
                    if (parts.size >= 5) {
                        currentItems.add(
                            PlaylistItem(
                                path = decode(parts[1]),
                                title = decode(parts[2]),
                                artist = decode(parts[3]),
                                durationMs = parts[4].toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
            }
            currentPlaylist?.let {
                playlists.add(it.copy(items = currentItems.toList()))
            }
        } catch (_: Exception) {}
    }

    private fun saveToDisk() {
        try {
            val parent = storageFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            val sb = StringBuilder()
            for (playlist in playlists) {
                sb.append("PL\t")
                    .append(playlist.id).append("\t")
                    .append(playlist.createdAt).append("\t")
                    .append(encode(playlist.name)).append("\n")

                for (item in playlist.items) {
                    sb.append("ITEM\t")
                        .append(encode(item.path)).append("\t")
                        .append(encode(item.title)).append("\t")
                        .append(encode(item.artist)).append("\t")
                        .append(item.durationMs).append("\n")
                }
            }
            storageFile.writeText(sb.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")
}
