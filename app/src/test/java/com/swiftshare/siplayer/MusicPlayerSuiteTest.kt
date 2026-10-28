package com.swiftshare.siplayer

import com.swiftshare.siplayer.equalizer.EqualizerController
import com.swiftshare.siplayer.folder.FolderScanner
import com.swiftshare.siplayer.lyrics.LrcParser
import com.swiftshare.siplayer.playlist.PlaylistItem
import com.swiftshare.siplayer.playlist.PlaylistManager
import com.swiftshare.siplayer.timer.SleepTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlayerSuiteTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ── LRC Parser Tests ────────────────────────────────────────────────────

    @Test(timeout = 60000)
    fun testLrcParserMetadataAndTimestamps() {
        val lrcContent = """
            [ti:Desert Rose]
            [ar:Sting]
            [al:Brand New Day]
            [00:15.30]I dream of rain
            [00:22.500]I dream of gardens in the desert sand
            [00:35.00]I wake in vain
        """.trimIndent()

        val lyrics = LrcParser.parse(lrcContent)
        assertEquals("Desert Rose", lyrics.title)
        assertEquals("Sting", lyrics.artist)
        assertEquals("Brand New Day", lyrics.album)
        assertEquals(3, lyrics.lines.size)

        assertEquals(15300L, lyrics.lines[0].timestampMs)
        assertEquals("I dream of rain", lyrics.lines[0].text)

        assertEquals(22500L, lyrics.lines[1].timestampMs)
        assertEquals("I dream of gardens in the desert sand", lyrics.lines[1].text)

        assertEquals(35000L, lyrics.lines[2].timestampMs)
        assertEquals("I wake in vain", lyrics.lines[2].text)
    }

    @Test(timeout = 60000)
    fun testLrcParserMultiTimestampAndOutOfOrderSorting() {
        val lrcContent = """
            [01:00.00]Second verse
            [00:10.00][00:40.00]Repeated chorus
            [00:05.00]Intro
        """.trimIndent()

        val lyrics = LrcParser.parse(lrcContent)
        assertEquals(4, lyrics.lines.size)

        // Must be sorted chronologically: 5s, 10s, 40s, 60s
        assertEquals(5000L, lyrics.lines[0].timestampMs)
        assertEquals("Intro", lyrics.lines[0].text)

        assertEquals(10000L, lyrics.lines[1].timestampMs)
        assertEquals("Repeated chorus", lyrics.lines[1].text)

        assertEquals(40000L, lyrics.lines[2].timestampMs)
        assertEquals("Repeated chorus", lyrics.lines[2].text)

        assertEquals(60000L, lyrics.lines[3].timestampMs)
        assertEquals("Second verse", lyrics.lines[3].text)
    }

    @Test(timeout = 60000)
    fun testLrcLineSyncIndex() {
        val lrcContent = """
            [00:10.00]Line 1
            [00:20.00]Line 2
            [00:30.00]Line 3
        """.trimIndent()

        val lyrics = LrcParser.parse(lrcContent)

        // Before first line -> 0
        assertEquals(0, LrcParser.getLineIndexAt(lyrics, 5000L))

        // Exactly on line 1 -> 0
        assertEquals(0, LrcParser.getLineIndexAt(lyrics, 10000L))

        // Between line 1 and line 2 -> 0
        assertEquals(0, LrcParser.getLineIndexAt(lyrics, 15000L))

        // On line 2 -> 1
        assertEquals(1, LrcParser.getLineIndexAt(lyrics, 20000L))

        // Past last line -> 2
        assertEquals(2, LrcParser.getLineIndexAt(lyrics, 45000L))
    }

    @Test(timeout = 60000)
    fun testLrcFileDiscovery() {
        val root = tempFolder.newFolder("music_dir")
        val audioFile = File(root, "track01.mp3").apply { writeBytes(ByteArray(1024)) }
        val lrcFile = File(root, "track01.lrc").apply { writeText("[00:01.00]Test") }

        val found = LrcParser.findLrcFileForAudio(audioFile.absolutePath)
        assertNotNull(found)
        assertEquals(lrcFile.absolutePath, found?.absolutePath)

        // Subfolder discovery
        val subDir = tempFolder.newFolder("album")
        val audioSub = File(subDir, "song2.mp3").apply { writeBytes(ByteArray(1024)) }
        val lyricsFolder = File(subDir, "lyrics").apply { mkdirs() }
        val subLrc = File(lyricsFolder, "song2.lrc").apply { writeText("[00:01.00]Sub lyric") }

        val foundSub = LrcParser.findLrcFileForAudio(audioSub.absolutePath)
        assertNotNull(foundSub)
        assertEquals(subLrc.absolutePath, foundSub?.absolutePath)
    }

    // ── Sleep Timer Tests ───────────────────────────────────────────────────

    @Test(timeout = 60000)
    fun testSleepTimerCountdownAndFinish() = runTest {
        val timer = SleepTimer(this)

        var finished = false
        timer.start(1) { // 1 minute = 60s
            finished = true
        }

        assertTrue("Timer should be active", timer.isTimerRunning.value)
        assertEquals(60, timer.remainingSeconds.value)

        // Advance 60 seconds
        testScheduler.advanceTimeBy(60_001)
        assertEquals(0, timer.remainingSeconds.value)
        assertFalse("Timer should be finished", timer.isTimerRunning.value)
        assertTrue("Callback must fire", finished)
    }

    @Test(timeout = 60000)
    fun testSleepTimerCancel() = runTest {
        val timer = SleepTimer(this)

        var finished = false
        timer.start(10) { finished = true }
        assertTrue(timer.isTimerRunning.value)

        timer.cancel()
        assertFalse(timer.isTimerRunning.value)
        assertEquals(0, timer.remainingSeconds.value)

        testScheduler.advanceTimeBy(600_000)
        assertFalse("Cancelled timer must not trigger callback", finished)
    }

    @Test(timeout = 60000)
    fun testSleepTimerEndOfSong() {
        val timer = SleepTimer()
        var finished = false
        timer.startEndOfSong { finished = true }

        assertTrue(timer.isTimerRunning.value)
        assertTrue(timer.stopAtEndOfSong.value)

        timer.notifySongCompleted()
        assertTrue("End of song callback must fire", finished)
        assertFalse(timer.isTimerRunning.value)
    }

    // ── Playlist Manager Tests ──────────────────────────────────────────────

    @Test(timeout = 60000)
    fun testPlaylistCreateAddRemoveDeleteAndPersistence() {
        val file = File(tempFolder.root, "playlists.json")
        val manager = PlaylistManager(file)

        assertEquals(0, manager.getPlaylists().size)

        val pl = manager.createPlaylist("Chill Vibes")
        assertEquals(1, manager.getPlaylists().size)
        assertEquals("Chill Vibes", pl.name)

        val song1 = PlaylistItem(path = "/sdcard/Music/track1.mp3", title = "Track 1", durationMs = 180000L)
        val song2 = PlaylistItem(path = "/sdcard/Music/track2.mp3", title = "Track 2", durationMs = 210000L)

        assertTrue(manager.addSongToPlaylist(pl.id, song1))
        assertTrue(manager.addSongToPlaylist(pl.id, song2))
        assertFalse("Adding duplicate path must be rejected", manager.addSongToPlaylist(pl.id, song1))

        assertEquals(2, manager.getPlaylist(pl.id)?.items?.size)

        // Verify disk persistence by loading in new instance
        val manager2 = PlaylistManager(file)
        val loaded = manager2.getPlaylist(pl.id)
        assertNotNull(loaded)
        assertEquals("Chill Vibes", loaded?.name)
        assertEquals(2, loaded?.items?.size)
        assertEquals("Track 1", loaded?.items?.get(0)?.title)

        // Remove a song
        assertTrue(manager2.removeSongFromPlaylist(pl.id, song1.path))
        assertEquals(1, manager2.getPlaylist(pl.id)?.items?.size)

        // Delete playlist
        assertTrue(manager2.deletePlaylist(pl.id))
        assertEquals(0, manager2.getPlaylists().size)
    }

    // ── Folder Scanner Tests ────────────────────────────────────────────────

    @Test(timeout = 60000)
    fun testFolderScannerGroupsAudioFiles() {
        val root = tempFolder.newFolder("storage")
        val folderA = File(root, "Downloads").apply { mkdirs() }
        val folderB = File(root, "Podcasts").apply { mkdirs() }

        File(folderA, "song1.mp3").apply { writeBytes(ByteArray(2048)) }
        File(folderA, "song2.m4a").apply { writeBytes(ByteArray(4096)) }
        File(folderA, "notes.txt").apply { writeText("ignore me") } // non-audio

        File(folderB, "episode1.opus").apply { writeBytes(ByteArray(8192)) }

        val folders = FolderScanner.scanDirectory(root, minSizeBytes = 1024L)
        assertEquals(2, folders.size)

        val dFolder = folders.find { it.name == "Downloads" }
        assertNotNull(dFolder)
        assertEquals(2, dFolder?.songCount)

        val pFolder = folders.find { it.name == "Podcasts" }
        assertNotNull(pFolder)
        assertEquals(1, pFolder?.songCount)
    }

    // ── Equalizer Fallback Tests ────────────────────────────────────────────

    @Test(timeout = 60000)
    fun testEqualizerFallbackBandsAndPresets() {
        val eq = EqualizerController()
        val bands = eq.getBands()
        assertEquals(5, bands.size)
        assertEquals(60, bands[0].centerFreqHz)
        assertEquals(14000, bands[4].centerFreqHz)

        val presets = eq.getPresets()
        assertTrue(presets.size >= 10)
        assertEquals("Normal", presets[0].name)
        assertEquals("Rock", presets[9].name)
    }
}

