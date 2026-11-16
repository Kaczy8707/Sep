package pl.septrener.exam

import pl.septrener.content.ContentItem
import pl.septrener.content.Group
import pl.septrener.content.Scope
import pl.septrener.data.ItemProgress
import pl.septrener.data.Srs

enum class QuestionPool(val label: String) {
    ALL("Wszystkie"),
    DUE("Do powtórki (harmonogram)"),
    ERRORS("Pytania z błędów"),
}

data class ExamConfig(
    val group: Group?,
    /** null = wszystkie; E lub D – pytania oznaczone danym zakresem lub „E i D”. */
    val scope: Scope? = null,
    val pool: QuestionPool = QuestionPool.ALL,
    val shuffle: Boolean = true,
    val oral: Boolean = true,
    /** Tylko pytania sytuacyjne/awaryjne („Co zrobiłbyś…?”). */
    val scenarios: Boolean = false,
) {
    val mode: String get() = when { scenarios -> "scenario"; oral -> "oral"; else -> "quiz" }
}

object ExamSelection {
    /** Pytanie „z błędów”: ostatnia ocena „Powtórz” albo ocena „Częściowo” bez późniejszego opanowania. */
    fun isError(p: ItemProgress?): Boolean = p != null && (p.markedHard || (p.partialCount > 0 && p.box <= 1 && p.knownCount == 0))

    fun select(items: List<ContentItem>, progress: Map<String, ItemProgress>, config: ExamConfig, now: Long): List<ContentItem> =
        items.filter { it.isQuestion }
            .filter { config.group == null || it.group == config.group }
            .filter { it.matchesScope(config.scope) }
            .filter { !config.scenarios || it.isScenario }
            .filter {
                when (config.pool) {
                    QuestionPool.ALL -> true
                    QuestionPool.DUE -> Srs.isDue(progress[it.id], now)
                    QuestionPool.ERRORS -> isError(progress[it.id])
                }
            }
}
