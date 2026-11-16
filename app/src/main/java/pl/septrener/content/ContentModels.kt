package pl.septrener.content

import kotlinx.serialization.Serializable

/** Grupy kwalifikacyjne obsługiwane przez aplikację. */
enum class Group(val code: String, val shortName: String) {
    G1("G1", "elektroenergetyczne"),
    G2("G2", "cieplne"),
    G3("G3", "gazowe");

    companion object {
        fun fromCode(code: String): Group? = entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
    }
}

enum class ItemType(val code: String) {
    LESSON("lesson"),
    ORAL_QUESTION("oral_question");

    companion object {
        fun fromCode(code: String): ItemType? = entries.firstOrNull { it.code == code.trim() }
    }
}

/** Zakres świadectwa. Brak jednoznacznego oznaczenia => [UNKNOWN] ("E/D – do weryfikacji"). */
enum class Scope(val label: String) {
    E("E – eksploatacja"),
    D("D – dozór"),
    ED("E i D"),
    UNKNOWN("E/D – do weryfikacji");

    companion object {
        fun fromCode(code: String?): Scope = when (code?.trim()?.uppercase()) {
            "E" -> E
            "D" -> D
            "E/D", "ED", "E+D" -> ED
            else -> UNKNOWN
        }
    }
}

/** Surowy rekord w formacie paczki JSON (schemat v1). Pola opcjonalne zależnie od typu. */
@Serializable
data class RawItem(
    val id: String? = null,
    val group: String? = null,
    val type: String? = null,
    val title: String? = null,
    val content: String? = null,
    val question: String? = null,
    val answer: String? = null,
    val pauseSeconds: Int? = null,
    val verified: Boolean? = null,
    val scope: String? = null,
    val source: String? = null,
    /** Lekcja, z której wywodzi się pytanie (opcjonalnie). */
    val sourceLessonId: String? = null,
    /** Pytanie sytuacyjne/awaryjne (tryb „Co zrobiłbyś…?”). */
    val scenario: Boolean? = null,
)

@Serializable
data class RawPack(
    val schemaVersion: Int? = null,
    val language: String? = null,
    val source: String? = null,
    val items: List<RawItem> = emptyList(),
)

/** Zwalidowana pozycja treści używana w aplikacji. */
data class ContentItem(
    val id: String,
    val group: Group,
    val type: ItemType,
    val title: String,
    /** Treść lekcji (dla [ItemType.LESSON]). */
    val content: String = "",
    /** Pytanie i odpowiedź (dla [ItemType.ORAL_QUESTION]). */
    val question: String = "",
    val answer: String = "",
    val pauseSeconds: Int? = null,
    val verified: Boolean = false,
    val scope: Scope = Scope.UNKNOWN,
    /** Nazwa paczki/pliku, z której pochodzi pozycja. */
    val origin: String = "",
    /** Opis źródła z rekordu (pole „source”), jeśli podano. */
    val source: String? = null,
    val sourceLessonId: String? = null,
    /** Pytanie sytuacyjne/awaryjne – z pola „scenario” paczki lub z listy assets/content/scenarios.json. */
    val isScenario: Boolean = false,
) {
    val isLesson get() = type == ItemType.LESSON
    val isQuestion get() = type == ItemType.ORAL_QUESTION

    /** Czy pozycja dotyczy zakresu [filter] (E lub D). Pozycje „E i D” pasują do obu; nieoznaczone – do żadnego. */
    fun matchesScope(filter: Scope?): Boolean = when (filter) {
        null, Scope.UNKNOWN -> true
        Scope.ED -> scope == Scope.ED
        else -> scope == filter || scope == Scope.ED
    }

    /** Tekst do wyszukiwania pełnotekstowego. */
    val searchableText: String get() = listOf(title, content, question, answer).joinToString("\n")
}
