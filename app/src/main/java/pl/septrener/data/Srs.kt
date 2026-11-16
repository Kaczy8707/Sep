package pl.septrener.data

import java.util.concurrent.TimeUnit

/** Proste powtórki interwałowe (system Leitnera). */
object Srs {
    /** Odstęp w dniach dla pudełka 0..5. */
    val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 14, 30)
    const val MAX_BOX = 5
    private val DAY_MS = TimeUnit.DAYS.toMillis(1)

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
