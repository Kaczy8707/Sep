package pl.septrener

import pl.septrener.tts.SpeechEngine

/** Silnik testowy: zapisuje zakolejkowane wypowiedzi; zdarzenia wywołuje test. */
class FakeSpeechEngine(ready: Boolean = true) : SpeechEngine {
    data class Utterance(val id: String, val text: String?, val silenceMs: Long?)

    override val maxInputLength = 3984
    override var isReady: Boolean = ready
        private set
    private var current: SpeechEngine.Listener? = null
    val listener: SpeechEngine.Listener? get() = current
    val queued = mutableListOf<Utterance>()
    var stopCount = 0
    var failNext = false

    override fun setListener(listener: SpeechEngine.Listener?) { current = listener }
    override fun speak(text: String, utteranceId: String): Boolean {
        if (failNext) return false
        queued += Utterance(utteranceId, text, null); return true
    }
    override fun silence(durationMs: Long, utteranceId: String): Boolean {
        queued += Utterance(utteranceId, null, durationMs); return true
    }
    override fun stop() { stopCount++; queued.clear() }

    fun setReady(ready: Boolean) { isReady = ready; listener?.onReadyChanged(ready) }

    /** Symuluje odtworzenie całej pierwszej wypowiedzi w kolejce. */
    fun playNext(): Utterance {
        val u = queued.removeAt(0)
        listener?.onUtteranceStart(u.id)
        listener?.onUtteranceDone(u.id)
        return u
    }
    fun startNext(): Utterance = queued.first().also { listener?.onUtteranceStart(it.id) }
}
