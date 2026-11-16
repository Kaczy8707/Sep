package pl.septrener.tts

/**
 * Abstrakcja nad silnikiem mowy, aby logikę kolejki dało się testować bez Androida.
 * Wywołania zwrotne MUSZĄ być dostarczane na wątku głównym.
 */
interface SpeechEngine {
    /** Maksymalna długość jednego wywołania speak() (z marginesem). */
    val maxInputLength: Int
    val isReady: Boolean

    fun setListener(listener: Listener?)
    /** Kolejkuje tekst (QUEUE_ADD). Zwraca false przy błędzie. */
    fun speak(text: String, utteranceId: String): Boolean
    /** Kolejkuje ciszę o zadanej długości (QUEUE_ADD). */
    fun silence(durationMs: Long, utteranceId: String): Boolean
    /** Natychmiast przerywa i czyści kolejkę. */
    fun stop()

    interface Listener {
        fun onReadyChanged(ready: Boolean) {}
        fun onUtteranceStart(utteranceId: String)
        fun onUtteranceDone(utteranceId: String)
        fun onUtteranceError(utteranceId: String, errorCode: Int)
    }
}
