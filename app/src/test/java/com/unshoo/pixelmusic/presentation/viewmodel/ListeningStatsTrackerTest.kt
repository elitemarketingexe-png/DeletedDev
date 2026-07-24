package com.unshoo.pixelmusic.presentation.viewmodel

import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import com.unshoo.pixelmusic.data.DailyMixManager
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.TimeUnit
import android.content.Context
import com.unshoo.pixelmusic.data.database.EngagementDao
import com.unshoo.pixelmusic.data.database.MusicDao
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository

class ListeningStatsTrackerTest {

    private val context: Context = mockk(relaxed = true)
    private val dailyMixManager: DailyMixManager = mockk(relaxed = true)
    private val playbackStatsRepository: PlaybackStatsRepository = mockk(relaxed = true)
    private val engagementDao: EngagementDao = mockk(relaxed = true)
    private val musicDao: MusicDao = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(SystemClock::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `completed play is capped to track duration`() {
        val tracker = ListeningStatsTracker(
            context = context,
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository,
            engagementDao = engagementDao,
            musicDao = musicDao,
            userPreferencesRepository = userPreferencesRepository
        )
        val song = song(
            songId = "looped-song",
            durationMs = TimeUnit.MINUTES.toMillis(3)
        )
        val listenedMs = TimeUnit.MINUTES.toMillis(12)

        every { SystemClock.elapsedRealtime() } returnsMany listOf(
            1_000L,
            1_000L + listenedMs,
            1_000L + listenedMs
        )

        tracker.onSongChanged(
            song = song,
            positionMs = 0L,
            durationMs = song.duration,
            isPlaying = true
        )
        tracker.onProgress(positionMs = song.duration, isPlaying = true)
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(timeout = 2_000) {
            dailyMixManager.recordPlay(song.id, song.duration, any())
        }
        coVerify(timeout = 2_000) {
            playbackStatsRepository.recordPlayback(
                songId = song.id,
                durationMs = song.duration,
                timestamp = any(),
                title = song.title,
                artist = song.displayArtist,
                thumbnail = any(),
                genre = any(),
                album = any()
            )
        }
    }

    @Test
    fun `onProgress accumulates incremental listening time`() {
        val tracker = ListeningStatsTracker(
            context = context,
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository,
            engagementDao = engagementDao,
            musicDao = musicDao,
            userPreferencesRepository = userPreferencesRepository
        )
        val firstChunkMs = 7_000L
        val secondChunkMs = 8_000L
        val expectedDurationMs = firstChunkMs + secondChunkMs
        val song = song(songId = "song-1", durationMs = expectedDurationMs)

        every { SystemClock.elapsedRealtime() } returnsMany listOf(
            5_000L,
            5_000L + firstChunkMs,
            5_000L + expectedDurationMs,
            5_000L + expectedDurationMs
        )

        tracker.onSongChanged(
            song = song,
            positionMs = 0L,
            durationMs = song.duration,
            isPlaying = true
        )
        tracker.onProgress(positionMs = firstChunkMs, isPlaying = true)
        tracker.onProgress(positionMs = expectedDurationMs, isPlaying = true)
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(timeout = 2_000) {
            playbackStatsRepository.recordPlayback(
                songId = song.id,
                durationMs = expectedDurationMs,
                timestamp = any(),
                title = song.title,
                artist = song.displayArtist,
                thumbnail = any(),
                genre = any(),
                album = any()
            )
        }
        assertThat(expectedDurationMs).isGreaterThan(TimeUnit.SECONDS.toMillis(5))
    }

    @Test
    fun `partial playback does not create stats or engagement`() {
        val tracker = ListeningStatsTracker(
            context = context,
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository,
            engagementDao = engagementDao,
            musicDao = musicDao,
            userPreferencesRepository = userPreferencesRepository
        )
        val song = song(songId = "skipped-song", durationMs = 180_000L)

        every { SystemClock.elapsedRealtime() } returnsMany listOf(1_000L, 31_000L, 31_000L)

        tracker.onSongChanged(song, 0L, song.duration, true)
        tracker.onProgress(positionMs = 30_000L, isPlaying = true)
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(exactly = 0) { dailyMixManager.recordPlay(any(), any(), any()) }
        coVerify(exactly = 0) {
            playbackStatsRepository.recordPlayback(
                songId = any(),
                durationMs = any(),
                timestamp = any(),
                title = any(),
                artist = any(),
                thumbnail = any(),
                genre = any(),
                album = any()
            )
        }
    }

    @Test
    fun `seeking to end without listening does not count as completed`() {
        val tracker = ListeningStatsTracker(
            context = context,
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository,
            engagementDao = engagementDao,
            musicDao = musicDao,
            userPreferencesRepository = userPreferencesRepository
        )
        val song = song(songId = "seeked-song", durationMs = 180_000L)

        every { SystemClock.elapsedRealtime() } returnsMany listOf(1_000L, 3_000L, 3_000L)

        tracker.onSongChanged(song, 0L, song.duration, true)
        tracker.onProgress(positionMs = song.duration, isPlaying = true)
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(exactly = 0) { dailyMixManager.recordPlay(any(), any(), any()) }
        coVerify(exactly = 0) {
            playbackStatsRepository.recordPlayback(
                songId = any(),
                durationMs = any(),
                timestamp = any(),
                title = any(),
                artist = any(),
                thumbnail = any(),
                genre = any(),
                album = any()
            )
        }
    }

    private fun song(songId: String, durationMs: Long = 5 * 60 * 1000L): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = durationMs,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
