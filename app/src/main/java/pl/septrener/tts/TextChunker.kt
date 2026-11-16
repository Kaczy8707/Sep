package pl.septrener.tts

/**
 * Dzieli długi tekst na fragmenty do syntezy mowy.
 *
 * Gwarancje:
 * - każdy fragment ma długość ≤ [maxLength] (limit `TextToSpeech.getMaxSpeechInputLength()` minus margines),
 * - fragmenty nie są puste,
 * - kolejność i treść słów są zachowane (zmienia się jedynie białe znaki na granicach fragmentów).
 *
 * Preferowane cięcie: akapit → zdanie → przecinek/średnik → spacja → twarde cięcie.
 */
object TextChunker {
    const val DEFAULT_PREFERRED = 400

    fun chunk(text: String, maxLength: Int, preferredLength: Int = DEFAULT_PREFERRED): List<String> {
        require(maxLength >= 20) { "maxLength musi wynosić co najmniej 20" }
        val preferred = preferredLength.coerceIn(20, maxLength)
        val paragraphs = text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        for (p in paragraphs) {
            if (p.length <= preferred) { result += p; continue }
            val buffer = StringBuilder()
            for (sentence in splitSentences(p).flatMap { splitLong(it, maxLength) }) {
                if (buffer.isNotEmpty() && buffer.length + 1 + sentence.length > preferred) {
                    result += buffer.toString(); buffer.clear()
                }
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(sentence)
            }
            if (buffer.isNotEmpty()) result += buffer.toString()
        }
        return result
    }

    /** Granica zdania: [.!?…] (opcjonalnie cudzysłów/nawias) + biały znak + wielka litera/cyfra/cudzysłów; lub nowa linia. */
    private val sentenceBoundary = Regex("(?<=[.!?…][\"”)»]?)\\s+(?=[\\p{Lu}0-9„\"(«])|\\s*\\n\\s*")

    internal fun splitSentences(paragraph: String): List<String> =
        paragraph.split(sentenceBoundary).map { it.trim() }.filter { it.isNotEmpty() }

    internal fun splitLong(sentence: String, maxLength: Int): List<String> {
        if (sentence.length <= maxLength) return listOf(sentence)
        val out = mutableListOf<String>()
        var rest = sentence
        while (rest.length > maxLength) {
            val window = rest.substring(0, maxLength)
            var cut = maxOf(window.lastIndexOf(", "), window.lastIndexOf("; "), window.lastIndexOf(": "))
            cut = if (cut > maxLength / 3) cut + 1 else window.lastIndexOf(' ')
            if (cut <= 0) cut = maxLength
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out.filter { it.isNotEmpty() }
    }
}
