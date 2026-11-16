package pl.septrener.exam

import pl.septrener.data.Grade

import kotlin.random.Random

/**
 * Sesja egzaminu/quizu: kolejka pytań z możliwością powtórki.
 * „Umiem” usuwa pytanie z sesji; „Powtórz” odkłada je na koniec kolejki (maksymalnie [maxRepeats] razy).
 */
class ExamSession(
    questionIds: List<String>,
    shuffle: Boolean = true,
    random: Random = Random.Default,
    private val maxRepeats: Int = 2,
) {
    private val queue = ArrayDeque(if (shuffle) questionIds.shuffled(random) else questionIds)
    private val repeats = HashMap<String, Int>()
    val total = queue.size
    var knownCount = 0; private set
    var hardCount = 0; private set
    var partialCount = 0; private set
    var answered = 0; private set

    val current: String? get() = queue.firstOrNull()
    val remaining: Int get() = queue.size
    val finished: Boolean get() = queue.isEmpty()

    /** Ocena trzystopniowa: „częściowo” liczy się osobno i nie wraca w tej sesji. */
    fun grade(grade: Grade): String? = when (grade) {
        Grade.KNOWN -> grade(true)
        Grade.HARD -> grade(false)
        Grade.PARTIAL -> {
            if (queue.removeFirstOrNull() != null) { answered++; partialCount++ }
            current
        }
    }

    /** Zwraca identyfikator następnego pytania lub null, gdy sesja się skończyła. */
    fun grade(known: Boolean): String? {
        val id = queue.removeFirstOrNull() ?: return null
        answered++
        if (known) knownCount++ else {
            hardCount++
            val n = repeats.getOrDefault(id, 0)
            if (n < maxRepeats) {
                repeats[id] = n + 1
                queue.addLast(id)
            }
        }
        return current
    }

    fun skip(): String? {
        val id = queue.removeFirstOrNull() ?: return null
        queue.addLast(id)
        return current
    }
}
