package pl.septrener.data

import java.util.concurrent.TimeUnit

/** Proste powtórki interwałowe (system Leitnera). */
object Srs {
    /** Odstęp w dniach dla pudełka 0..5. */
    val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 14, 30)
    const val MAX_BOX = 5
    private val DAY_MS = TimeUnit.DAYS.toMillis(1)

    /**
     * Ocena trzystopniowa. „Częściowo”: pudełko bez awansu (najwyżej 1), powtórka po 1 dniu,
     * pytanie nie jest oznaczane jako trudne.
     */
    fun grade(p: ItemProgress, grade: Grade, now: Long): ItemProgress = when (grade) {
        Grade.KNOWN -> grade(p, true, now)
        Grade.HARD -> grade(p, false, now)
        Grade.PARTIAL -> {
            val box = p.box.coerceIn(1, 1)
            p.copy(
                box = box, partialCount = p.partialCount + 1, repetitions = p.repetitions + 1,
                markedHard = false, lastReviewedAt = now, nextDueAt = now + INTERVAL_DAYS[box] * DAY_MS,
            )
        }
    }

    fun grade(p: ItemProgress, known: Boolean, now: Long): ItemProgress = if (known) {
        val box = (p.box + 1).coerceAtMost(MAX_BOX)
        p.copy(
            box = box, knownCount = p.knownCount + 1, repetitions = p.repetitions + 1,
            markedHard = false, lastReviewedAt = now, nextDueAt = now + INTERVAL_DAYS[box] * DAY_MS,
        )
    } else {
        p.copy(
            box = 0, hardCount = p.hardCount + 1, repetitions = p.repetitions + 1,
            markedHard = true, lastReviewedAt = now, nextDueAt = now,
        )
    }

    fun isDue(p: ItemProgress?, now: Long): Boolean = p?.nextDueAt == null || p.nextDueAt <= now
}
