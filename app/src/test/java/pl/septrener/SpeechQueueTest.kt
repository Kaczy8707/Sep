package pl.septrener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.septrener.content.Group
import pl.septrener.playback.Segment
import pl.septrener.playback.SegmentKind
import pl.septrener.playback.SpeechQueue
import pl.septrener.playback.Track
import pl.septrener.playback.TrackKind

class SpeechQueueTest {
    private var now = 0L
    private val completed = mutableListOf<String>()
    private val started = mutableListOf<Pair<String, Int>>()
    private val engine = FakeSpeechEngine()
    private val queue = SpeechQueue(engine, { now }, object : SpeechQueue.Callback {
        override fun onSegmentStarted(track: Track, segmentIndex: Int) { started += track.itemId to segmentIndex }
        override fun onTrackCompleted(track: Track) { completed += track.itemId }
    })

    private fun track(id: String, n: Int) = Track(
        "lesson:$id", id, TrackKind.LESSON, Group.G1, "Lekcja $id",
        (1..n).map { Segment(SegmentKind.LESSON_TEXT, "$id fragment $it") },
    )

    @Test
    fun playQueuesWholeTrackAhead() {
        queue.setPlaylist(listOf(track("a", 3)))
        queue.play()
        assertEquals(listOf("a fragment 1", "a fragment 2", "a fragment 3"), engine.queued.map { it.text })
        assertTrue(queue.playing)
    }

    @Test
    fun pauseAndResumeFromStartOfCurrentFragment() {
        queue.setPlaylist(listOf(track("a", 4)))
        queue.play()
        engine.playNext()           // fragment 1 skończony
        engine.startNext()          // fragment 2 w trakcie
        assertEquals(1, queue.segmentIndex)
        queue.pause()
        assertFalse(queue.playing)
        assertTrue(engine.queued.isEmpty())
        assertEquals(1, queue.segmentIndex)
        queue.play()
        assertEquals(listOf("a fragment 2", "a fragment 3", "a fragment 4"), engine.queued.map { it.text })
    }

    @Test
    fun resumePointAdvancesWhenFragmentFinishesBeforeNextStarts() {
        queue.setPlaylist(listOf(track("a", 3)))
        queue.play()
        engine.playNext()
        queue.pause() // fragment 2 jeszcze nie wystartował
        queue.play()
        assertEquals("a fragment 2", engine.queued.first().text)
    }

    @Test
    fun staleCallbacksAfterSeekAreIgnored() {
        queue.setPlaylist(listOf(track("a", 5)))
        queue.play()
        val old = engine.queued.toList()
        queue.seekSegment(3)
        engine.listener!!.onUtteranceStart(old[0].id)
        engine.listener!!.onUtteranceDone(old[4].id)
        assertEquals(3, queue.segmentIndex)
        assertTrue(completed.isEmpty())
        assertEquals(listOf("a fragment 4", "a fragment 5"), engine.queued.map { it.text })
    }

    @Test
    fun autoAdvanceToNextTrackAndEnd() {
        queue.setPlaylist(listOf(track("a", 2), track("b", 1)))
        queue.play()
        engine.playNext(); engine.playNext()
        assertEquals(listOf("a"), completed)
        assertEquals(1, queue.trackIndex)
        assertEquals(listOf("b fragment 1"), engine.queued.map { it.text })
        engine.playNext()
        assertEquals(listOf("a", "b"), completed)
        assertTrue(queue.ended)
        assertFalse(queue.playing)
        queue.play() // ponowne odtworzenie po końcu zaczyna ścieżkę od początku
        assertEquals("b fragment 1", engine.queued.first().text)
    }

    @Test
    fun noAutoAdvanceStopsAfterTrack() {
        queue.autoAdvance = false
        queue.setPlaylist(listOf(track("a", 1), track("b", 1)))
        queue.play()
        engine.playNext()
        assertTrue(queue.ended)
        assertEquals(0, queue.trackIndex)
    }

    @Test
    fun waitsForEngineThenStarts() {
        val slow = FakeSpeechEngine(ready = false)
        val q = SpeechQueue(slow, { 0 }, object : SpeechQueue.Callback {})
        q.setPlaylist(listOf(track("a", 2)))
        q.play()
        assertTrue(q.waitingForEngine)
        assertTrue(slow.queued.isEmpty())
        slow.setReady(true)
        assertFalse(q.waitingForEngine)
        assertEquals(2, slow.queued.size)
    }

    @Test
    fun engineErrorStopsPlaybackWithMessage() {
        queue.setPlaylist(listOf(track("a", 2)))
        queue.play()
        engine.listener!!.onUtteranceError(engine.queued.first().id, -1)
        assertFalse(queue.playing)
        assertNotNull(queue.lastError)
        engine.failNext = true
        queue.play()
        assertFalse(queue.playing)
    }

    @Test
    fun segmentStartTimestampForCountdown() {
        queue.setPlaylist(listOf(track("a", 2)))
        queue.play()
        now = 5_000
        engine.startNext()
        assertEquals(5_000, queue.segmentStartedAt)
        assertEquals(listOf("a" to 0), started)
    }

    @Test
    fun doublePlayDoesNotDuplicateQueue() {
        queue.setPlaylist(listOf(track("a", 2)))
        queue.play(); queue.play()
        assertEquals(2, engine.queued.size)
    }
}
