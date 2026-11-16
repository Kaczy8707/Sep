package pl.septrener.ui

const val DISCLAIMER = "Materiał pomocniczy; obowiązujące przepisy, normy i zakres kwalifikacji sprawdź przed egzaminem."
const val NOT_OFFICIAL = "Pytania pochodzą z materiałów szkoleniowych (status: wymaga weryfikacji). To nie jest oficjalna baza pytań komisji egzaminacyjnej."
const val RESUME_NOTE = "Po pauzie odtwarzanie wznawia się od początku bieżącego fragmentu."

private val lessonTitle = Regex("^LEKCJA (\\d+)\\.\\s*(?:\\1\\.\\s*)?(.*?)\\.?$")

/** Czytelny tytuł do wyświetlania (treść lekcji pozostaje bez zmian). */
fun displayTitle(title: String): String {
    val m = lessonTitle.matchEntire(title.trim()) ?: return title.trim().removeSuffix(".")
    return "Lekcja ${m.groupValues[1]} · ${m.groupValues[2]}"
}
