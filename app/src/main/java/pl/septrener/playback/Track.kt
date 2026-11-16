package pl.septrener.playback

import pl.septrener.content.ContentItem
import pl.septrener.content.ContentState
import pl.septrener.content.Group
import pl.septrener.tts.TextChunker

enum class SegmentKind { LESSON_TEXT, QUESTION, PAUSE, ANSWER, GAP }

data class Segment(val kind: SegmentKind, val text: String = "", val durationMs: Long = 0) {
    val isSilence get() = kind == SegmentKind.PAUSE || kind == SegmentKind.GAP
}

enum class TrackKind { LESSON, EXAM_QUESTION }

data class Track(
    val mediaId: String,
    val itemId: String,
    val kind: TrackKind,
    val group: Group,
    val title: String,
    val segments: List<Segment>,
) {
    val answerStart: Int get() = segments.indexOfFirst { it.kind == SegmentKind.ANSWER }.let { if (it < 0) segments.size else it }
    val pauseIndex: Int get() = segments.indexOfFirst { it.kind == SegmentKind.PAUSE }
}

/** Identyfikatory mediów: „lesson:<id>” lub „exam:<id>”. */
object MediaIds {
    private const val LESSON = "lesson:"
    private const val EXAM = "exam:"
    fun lesson(id: String) = LESSON + id
    fun exam(id: String) = EXAM + id
    fun parse(mediaId: String): Pair<TrackKind, String>? = when {
        mediaId.startsWith(LESSON) -> TrackKind.LESSON to mediaId.removePrefix(LESSON)
        mediaId.startsWith(EXAM) -> TrackKind.EXAM_QUESTION to mediaId.removePrefix(EXAM)
        else -> null
    }
}

data class ExamTiming(
    /** Pauza na odpowiedź (domyślnie 8 s), konfigurowalna w ustawieniach. */
    val pauseSeconds: Int = DEFAULT_PAUSE_SECONDS,
    /** Gdy true, użyj pauseSeconds z paczki (jeśli podano) zamiast ustawienia globalnego. */
    val usePackPause: Boolean = false,
    /** Krótka cisza po odpowiedzi przed kolejnym pytaniem (tryb słuchania). */
    val gapMs: Long = 1200,
) {
    fun pauseMsFor(item: ContentItem): Long {
        val seconds = if (usePackPause && item.pauseSeconds != null) item.pauseSeconds else pauseSeconds
        return seconds.coerceIn(MIN_PAUSE_SECONDS, MAX_PAUSE_SECONDS) * 1000L
    }

    companion object {
        const val DEFAULT_PAUSE_SECONDS = 8
        const val MIN_PAUSE_SECONDS = 1
        const val MAX_PAUSE_SECONDS = 120
    }
}

/** Buduje ścieżki odtwarzania z pozycji treści. Ten sam podział jest używany do wyświetlania tekstu. */
class TrackFactory(private val maxInputLength: Int, private val preferredLength: Int = TextChunker.DEFAULT_PREFERRED) {

    fun lessonSegments(item: ContentItem): List<Segment> =
        TextChunker.chunk(item.content, maxInputLength, preferredLength).map { Segment(SegmentKind.LESSON_TEXT, it) }

    fun lesson(item: ContentItem) = Track(
        mediaId = MediaIds.lesson(item.id), itemId = item.id, kind = TrackKind.LESSON,
        group = item.group, title = item.title, segments = lessonSegments(item),
    )

    fun examQuestion(item: ContentItem, timing: ExamTiming): Track {
        val segments = buildList {
            TextChunker.chunk("Pytanie. ${item.question}", maxInputLength, preferredLength).forEach { add(Segment(SegmentKind.QUESTION, it)) }
            add(Segment(SegmentKind.PAUSE, durationMs = timing.pauseMsFor(item)))
            TextChunker.chunk("Odpowiedź. ${item.answer}", maxInputLength, preferredLength).forEach { add(Segment(SegmentKind.ANSWER, it)) }
            if (timing.gapMs > 0) add(Segment(SegmentKind.GAP, durationMs = timing.gapMs))
        }
        return Track(MediaIds.exam(item.id), item.id, TrackKind.EXAM_QUESTION, item.group, item.title, segments)
    }

    fun forMediaId(mediaId: String, content: ContentState, timing: ExamTiming): Track? {
        val (kind, id) = MediaIds.parse(mediaId) ?: return null
        val item = content.item(id) ?: return null
        return when (kind) {
            TrackKind.LESSON -> if (item.isLesson) lesson(item) else null
            TrackKind.EXAM_QUESTION -> if (item.isQuestion) examQuestion(item, timing) else null
        }
    }
}
