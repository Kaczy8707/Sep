package pl.septrener.playback

import pl.septrener.tts.SpeechEngine

/**
 * Kolejka odtwarzania mowy: lista ścieżek, każda podzielona na fragmenty.
 *
 * - Wznowienie po pauzie zaczyna się od POCZĄTKU fragmentu, który był odtwarzany (bez śledzenia słów).
 * - Fragmenty bieżącej ścieżki są kolejkowane w silniku z góry (QUEUE_ADD), aby uniknąć przerw przy zablokowanym ekranie.
 * - Wywołania zwrotne z poprzednich „generacji” (po stop/seek) są ignorowane.
 *
 * Klasa nie jest wątkowo bezpieczna – używaj wyłącznie na wątku głównym.
 */
class SpeechQueue(
    private val engine: SpeechEngine,
    private val clock: () -> Long,
    private val callback: Callback,
) : SpeechEngine.Listener {

    interface Callback {
        fun onQueueStateChanged() {}
        fun onSegmentStarted(track: Track, segmentIndex: Int) {}
        fun onTrackCompleted(track: Track) {}
        fun onPlaybackError(message: String) {}
    }

    var tracks: List<Track> = emptyList(); private set
    var trackIndex = 0; private set
    var segmentIndex = 0; private set
    /** Użytkownik chce odtwarzać (playWhenReady). */
    var playing = false; private set
    /** Czekamy na gotowość silnika TTS. */
    var waitingForEngine = false; private set
    var ended = false; private set
    var segmentStartedAt: Long = 0; private set
    var autoAdvance = true
    var lastError: String? = null; private set

    private var generation = 0
    val currentTrack: Track? get() = tracks.getOrNull(trackIndex)

    init { engine.setListener(this) }

    fun setPlaylist(newTracks: List<Track>, startTrack: Int = 0, startSegment: Int = 0) {
        cancelEngine()
        tracks = newTracks.filter { it.segments.isNotEmpty() }
        trackIndex = startTrack.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        segmentIndex = clampSegment(startSegment)
        ended = false
        lastError = null
        if (tracks.isEmpty()) playing = false
        if (playing) enqueueFromCurrent()
        callback.onQueueStateChanged()
    }

    fun play() {
        if (tracks.isEmpty()) return
        if (playing) return
        if (ended) { ended = false; segmentIndex = 0 }
        playing = true
        lastError = null
        enqueueFromCurrent()
        callback.onQueueStateChanged()
    }

    fun pause() {
        if (!playing && !waitingForEngine) return
        playing = false
        cancelEngine()
        callback.onQueueStateChanged()
    }

    fun seek(track: Int, segment: Int) {
        if (tracks.isEmpty()) return
        cancelEngine()
        trackIndex = track.coerceIn(0, tracks.size - 1)
        segmentIndex = clampSegment(segment)
        ended = false
        if (playing) enqueueFromCurrent()
        callback.onQueueStateChanged()
    }

    fun seekSegment(segment: Int) = seek(trackIndex, segment)

    fun release() {
        cancelEngine()
        playing = false
        engine.setListener(null)
    }

    private fun clampSegment(segment: Int): Int {
        val size = currentTrack?.segments?.size ?: return 0
        return segment.coerceIn(0, (size - 1).coerceAtLeast(0))
    }

    private fun cancelEngine() {
        generation++
        waitingForEngine = false
        engine.stop()
    }

    private fun enqueueFromCurrent() {
        val track = currentTrack ?: return
        if (!engine.isReady) { waitingForEngine = true; return }
        waitingForEngine = false
        generation++
        segmentStartedAt = clock()
        for (i in segmentIndex until track.segments.size) {
            val seg = track.segments[i]
            val id = "$generation:$trackIndex:$i"
            val ok = if (seg.isSilence) engine.silence(seg.durationMs, id) else engine.speak(seg.spoken, id)
            if (!ok) { fail("Syntezator odrzucił fragment ${i + 1}."); return }
        }
    }

    private fun fail(message: String) {
        cancelEngine()
        playing = false
        lastError = message
        callback.onPlaybackError(message)
        callback.onQueueStateChanged()
    }

    private data class Uid(val generation: Int, val track: Int, val segment: Int)

    private fun parse(id: String): Uid? {
        val parts = id.split(':')
        if (parts.size != 3) return null
        val g = parts[0].toIntOrNull() ?: return null
        val t = parts[1].toIntOrNull() ?: return null
        val s = parts[2].toIntOrNull() ?: return null
        return Uid(g, t, s).takeIf { g == generation && t == trackIndex }
    }

    override fun onReadyChanged(ready: Boolean) {
        if (ready && playing && waitingForEngine) {
            enqueueFromCurrent()
            callback.onQueueStateChanged()
        }
    }

    override fun onUtteranceStart(utteranceId: String) {
        val uid = parse(utteranceId) ?: return
        segmentIndex = uid.segment
        segmentStartedAt = clock()
        currentTrack?.let { callback.onSegmentStarted(it, uid.segment) }
        callback.onQueueStateChanged()
    }

    override fun onUtteranceDone(utteranceId: String) {
        val uid = parse(utteranceId) ?: return
        val track = currentTrack ?: return
        if (uid.segment < track.segments.size - 1) {
            // Następny fragment jest już w kolejce silnika; zapamiętujemy go jako punkt wznowienia.
            segmentIndex = uid.segment + 1
            return
        }
        callback.onTrackCompleted(track)
        if (autoAdvance && trackIndex < tracks.size - 1) {
            trackIndex++
            segmentIndex = 0
            if (playing) enqueueFromCurrent()
        } else {
            generation++
            playing = false
            ended = true
        }
        callback.onQueueStateChanged()
    }

    override fun onUtteranceError(utteranceId: String, errorCode: Int) {
        parse(utteranceId) ?: return
        fail("Błąd syntezatora mowy (kod $errorCode). Sprawdź głos polski w ustawieniach.")
    }
}
