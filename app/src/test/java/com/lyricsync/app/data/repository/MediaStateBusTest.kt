package com.lyricsync.app.data.repository

import com.lyricsync.app.domain.LoopRange
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaStateBusTest {

    @Before
    fun setUp() {
        MediaStateBus.resetForTesting()
    }

    @Test
    fun `updatePosition updates playbackPositionMs and activeLineIndex without copying trackState when isPlaying unchanged`() {
        val initialTrack = MediaTrackState(
            trackName = "Instant Crush",
            artistName = "Daft Punk",
            durationMs = 200_000L,
            positionMs = 0L,
            isPlaying = true
        )
        MediaStateBus.updateTrackState(initialTrack)
        MediaStateBus.updateLyricsState(
            LyricsState(
                trackId = initialTrack.trackId,
                isSynced = true,
                lines = listOf(
                    LyricLine(1_000L, "Line 1"),
                    LyricLine(5_000L, "Line 2"),
                    LyricLine(10_000L, "Line 3")
                )
            )
        )

        MediaStateBus.updatePosition(positionMs = 5_200L, isPlaying = true)

        assertEquals(5_200L, MediaStateBus.playbackPositionMs.value)
        assertEquals(1, MediaStateBus.activeLineIndex.value)
        assertSame(initialTrack, MediaStateBus.trackState.value)

        MediaStateBus.updatePosition(positionMs = 5_400L, isPlaying = false)
        assertFalse(MediaStateBus.trackState.value.isPlaying)
    }

    @Test
    fun `setSyncOffsetMs clamps range and recalculates activeLineIndex`() {
        val track = MediaTrackState(trackName = "Song", artistName = "Artist", positionMs = 4_600L)
        MediaStateBus.updateTrackState(track)
        MediaStateBus.updateLyricsState(
            LyricsState(
                trackId = track.trackId,
                isSynced = true,
                lines = listOf(
                    LyricLine(1_000L, "One"),
                    LyricLine(5_000L, "Two")
                )
            )
        )
        assertEquals(0, MediaStateBus.activeLineIndex.value)

        MediaStateBus.setSyncOffsetMs(500L)
        assertEquals(500L, MediaStateBus.lyricsState.value.syncOffsetMs)
        assertEquals(1, MediaStateBus.activeLineIndex.value)

        MediaStateBus.setSyncOffsetMs(50_000L)
        assertEquals(15_000L, MediaStateBus.lyricsState.value.syncOffsetMs)

        MediaStateBus.setSyncOffsetMs(-50_000L)
        assertEquals(-15_000L, MediaStateBus.lyricsState.value.syncOffsetMs)
    }

    @Test
    fun `updateTrackState preserves loopRange for same track and clears on track switch`() {
        val track = MediaTrackState(trackName = "Track A", artistName = "Artist")
        MediaStateBus.updateTrackState(track)
        MediaStateBus.setLoopRange(LoopRange(0, 3, 1_000L, 9_000L))

        MediaStateBus.updateTrackState(track.copy(accentColorArgb = 0x123456))
        assertNotNull(MediaStateBus.loopRange.value)

        MediaStateBus.updateTrackState(MediaTrackState(trackName = "Track B", artistName = "Artist"))
        assertNull(MediaStateBus.loopRange.value)
    }

    @Test
    fun `clearTrackIfSessionEnded clears live track but respects manual override`() {
        val liveTrack = MediaTrackState(trackName = "Live", artistName = "Artist", isManualOverride = false)
        MediaStateBus.updateTrackState(liveTrack)
        MediaStateBus.updatePosition(12_000L, isPlaying = true)
        MediaStateBus.clearTrackIfSessionEnded()

        assertFalse(MediaStateBus.trackState.value.hasActiveTrack)
        assertEquals(0L, MediaStateBus.playbackPositionMs.value)
        assertEquals(-1, MediaStateBus.activeLineIndex.value)

        val manualTrack = MediaTrackState(trackName = "Manual", artistName = "Artist", isManualOverride = true)
        MediaStateBus.updateTrackState(manualTrack)
        MediaStateBus.clearTrackIfSessionEnded()
        assertTrue(MediaStateBus.trackState.value.hasActiveTrack)
    }
}
