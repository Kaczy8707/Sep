package pl.septrener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.septrener.content.ContentItem
import pl.septrener.content.ContentParser
import pl.septrener.content.ContentState
import pl.septrener.content.Group
import pl.septrener.content.ItemType
import pl.septrener.data.ItemProgress
import pl.septrener.data.Srs
import pl.septrener.exam.ExamSession
import pl.septrener.playback.ExamTiming
import pl.septrener.playback.MediaIds
import pl.septrener.playback.SegmentKind
import pl.septrener.playback.SpeechQueue
import pl.septrener.playback.TrackFactory
import kotlin.random.Random

class ExamLogicTest {
    private val factory = TrackFactory(3984)
    private val q = ContentItem("q1", Group.G1, ItemType.ORAL_QUESTION, "t", question = "Podaj prawo Ohma.", answer = "U = I × R.", pauseSeconds = 10)

    @Test
    fun examTrackIsQuestionPauseAnswerWithDefault8s() {
        val t = factory.examQuestion(q, ExamTiming())
        assertEquals(listOf(SegmentKind.QUESTION, SegmentKind.PAUSE, SegmentKind.ANSWER, SegmentKind.GAP), t.segments.map { it.kind })
        assertEquals(8_000, t.segments[1].durationMs)
        assertEquals(2, t.answerStart)
        assertEquals("Pytanie. Podaj prawo Ohma.", t.segments[0].text)
        assertEquals("Odpowiedź. U = I × R.", t.segments[2].text)
    }

    @Test
    fun pauseIsConfigurableAndClamped() {
        assertEquals(15_000, factory.examQuestion(q, ExamTiming(pauseSeconds = 15)).segments[1].durationMs)
        assertEquals(10_000, factory.examQuestion(q, ExamTiming(pauseSeconds = 15, usePackPause = true)).segments[1].durationMs)
        assertEquals(120_000, factory.examQuestion(q, ExamTiming(pauseSeconds = 999)).segments[1].durationMs)
        assertEquals(1_000, factory.examQuestion(q, ExamTiming(pauseSeconds = 0)).segments[1].durationMs)
    }

    @Test
    fun engineReceivesSilenceForPause() {
        val engine = FakeSpeechEngine()
        val queue = SpeechQueue(engine, { 0 }, object : SpeechQueue.Callback {})
        queue.setPlaylist(listOf(factory.examQuestion(q, ExamTiming())))
        queue.play()
        assertEquals(listOf(null, 8_000L, null, 1_200L), engine.queued.map { it.silenceMs })
        // „Pokaż odpowiedź” = przeskok do odpowiedzi z pominięciem pauzy
        queue.seekSegment(queue.currentTrack!!.answerStart)
        assertEquals("Odpowiedź. U = I × R.", engine.queued.first().text)
    }

    @Test
    fun mediaIdsResolveAgainstContent() {
        val items = ContentParser.parse(TestFiles.asset("G1_exam.json"), "g1").items +
            ContentParser.parse(TestFiles.asset("G1_lessons.json"), "g1l").items
        val state = ContentState(true, items)
        assertEquals(SegmentKind.QUESTION, factory.forMediaId(MediaIds.exam("g1-question-003"), state, ExamTiming())!!.segments[0].kind)
        assertTrue(factory.forMediaId(MediaIds.lesson("g1-lesson-01"), state, ExamTiming())!!.segments.isNotEmpty())
        assertNull(factory.forMediaId(MediaIds.lesson("g1-question-003"), state, ExamTiming()))
        assertNull(factory.forMediaId("lesson:brak", state, ExamTiming()))
        assertNull(factory.forMediaId("bzdura", state, ExamTiming()))
    }

    @Test
    fun sessionRequeuesHardQuestions() {
        val s = ExamSession(listOf("a", "b", "c"), shuffle = false, maxRepeats = 1)
        assertEquals("a", s.current)
        s.grade(false)            // a -> na koniec
        assertEquals("b", s.current)
        s.grade(true); s.grade(true)
        assertEquals("a", s.current)
        s.grade(false)            // limit powtórek osiągnięty
        assertTrue(s.finished)
        assertEquals(2, s.knownCount); assertEquals(2, s.hardCount); assertEquals(4, s.answered)
    }

    @Test
    fun sessionShuffleIsDeterministicWithSeed() {
        val ids = (1..20).map { "q$it" }
        val a = ExamSession(ids, random = Random(42)); val b = ExamSession(ids, random = Random(42))
        assertEquals(a.current, b.current)
        assertEquals(20, a.total)
    }

    @Test
    fun leitnerScheduling() {
        val day = 24 * 3600 * 1000L
        var p = ItemProgress("q", "G1", "oral_question")
        assertTrue(Srs.isDue(null, 0)); assertTrue(Srs.isDue(p, 0))
        p = Srs.grade(p, true, 0)
        assertEquals(1, p.box); assertEquals(day, p.nextDueAt)
        assertTrue(!Srs.isDue(p, day - 1) && Srs.isDue(p, day))
        p = Srs.grade(p, true, day)
        assertEquals(2, p.box); assertEquals(day + 3 * day, p.nextDueAt)
        p = Srs.grade(p, false, 5 * day)
        assertEquals(0, p.box); assertTrue(p.markedHard); assertEquals(5 * day, p.nextDueAt)
        assertEquals(3, p.repetitions); assertEquals(2, p.knownCount); assertEquals(1, p.hardCount)
        repeat(10) { p = Srs.grade(p, true, 0) }
        assertEquals(Srs.MAX_BOX, p.box)
    }
}
