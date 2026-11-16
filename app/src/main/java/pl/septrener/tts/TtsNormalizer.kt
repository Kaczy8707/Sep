package pl.septrener.tts

/**
 * Zamienia symbole, wzory i skróty na tekst do odczytu przez polski syntezator.
 * Działa WYŁĄCZNIE na tekście wysyłanym do TTS – tekst wyświetlany pozostaje bez zmian.
 *
 * Reguły są deterministyczne i konserwatywne: nie zmieniają znaczenia, a jedynie zapis
 * (np. „Ω” → „om”, „≤” → „mniejsze lub równe”, „RCD” → „er ce de”).
 */
object TtsNormalizer {

    private data class Forms(val one: String, val few: String, val many: String, val fraction: String)

    private val unitForms = linkedMapOf(
        "MΩ" to Forms("megaom", "megaomy", "megaomów", "megaoma"),
        "kΩ" to Forms("kiloom", "kiloomy", "kiloomów", "kilooma"),
        "mΩ" to Forms("miliom", "miliomy", "miliomów", "milioma"),
        "Ω" to Forms("om", "omy", "omów", "oma"),
        "mm²" to Forms("milimetr kwadratowy", "milimetry kwadratowe", "milimetrów kwadratowych", "milimetra kwadratowego"),
    )

    /** Jednostki w nawiasach kwadratowych, np. „U [V]”. */
    private val bracketUnits = mapOf(
        "[V]" to "w woltach", "[A]" to "w amperach", "[Ω]" to "w omach", "[W]" to "w watach",
        "[VA]" to "w woltoamperach", "[var]" to "w warach", "[Hz]" to "w hercach", "[s]" to "w sekundach",
    )

    /** Skróty literowane po polsku. Kolejność: dłuższe przed krótszymi. */
    val ACRONYMS: Map<String, String> = linkedMapOf(
        "TN-C-S" to "te en ce es", "TN-C" to "te en ce", "TN-S" to "te en es",
        "RCBO" to "er ce be o", "RCD" to "er ce de", "PEN" to "pe e en", "PE" to "pe e",
        "UPS" to "u pe es", "PV" to "pe fau", "SPD" to "es pe de", "EMC" to "e em ce",
        "PLC" to "pe el ce", "PID" to "pe i de", "LPG" to "el pe gie", "RKO" to "er ka o",
        "AED" to "a e de", "BHP" to "be ha pe", "UDT" to "u de te", "DC" to "de ce", "AC" to "a ce",
        "TT" to "te te", "TN" to "te en", "IT" to "i te", "IP" to "i pe", "CO" to "ce o",
    )

    /** Oznaczenia z indeksami we wzorach. */
    private val symbols = linkedMapOf(
        "U0" to "U zero", "Zs" to "Z es", "Ia" to "I a", "Ib" to "I be", "In" to "I en", "Iz" to "I zet",
        "Pstrat" to "P strat", "m_dot" to "m z kropką", "c_p" to "c pe",
    )

    private val capsWords = mapOf("LEKCJA" to "Lekcja", "TEST" to "test", "LOTO" to "loto", "EGZAMINATOR" to "egzaminator", "LEKTOR" to "lektor")

    private val numberUnit = Regex("(\\d+(?:[.,]\\d+)?)\\s*(MΩ|kΩ|mΩ|Ω|mm²)")
    private val lessonHeader = Regex("LEKCJA (\\d+)\\.\\s*\\1\\.")
    private val whitespace = Regex("[ \\t]+")

    fun normalize(input: String): String {
        var s = input
        // „LEKCJA 1. 1. Tytuł” – podwójna numeracja czytana raz
        s = lessonHeader.replace(s) { "Lekcja ${it.groupValues[1]}." }
        // słowa pisane wersalikami czytamy jak zwykłe wyrazy (bez literowania)
        capsWords.forEach { (k, v) -> s = Regex("\\b$k\\b").replace(s, v) }
        s = s.replace("E/D", "E i D")
        // jednostki mocy pozornej, czynnej i biernej w zapisie „w VA”, „w W”, „w var”
        s = Regex("\\bw VA\\b").replace(s, "w woltoamperach")
        s = Regex("\\bw W\\b").replace(s, "w watach")
        s = Regex("\\bw var\\b").replace(s, "w warach")
        s = Regex("\\b(G[123])/(\\d+)").replace(s) { "${it.groupValues[1]} łamane przez ${it.groupValues[2]}" }
        bracketUnits.forEach { (k, v) -> s = s.replace(k, v) }
        s = numberUnit.replace(s) { m ->
            val forms = unitForms.getValue(m.groupValues[2])
            "${m.groupValues[1]} ${pluralize(m.groupValues[1], forms)}"
        }
        s = s.replace("IΔn", "I delta en").replace("Δ", "delta ")
        s = s.replace("√3", "pierwiastek z trzech").replace("√", "pierwiastek z ")
        s = s.replace("cos φ", "kosinus fi").replace("φ", "fi")
        s = Regex("([A-Za-z])²").replace(s) { "${it.groupValues[1]} do kwadratu " }.replace("²", " do kwadratu")
        s = s.replace("≤", " mniejsze lub równe ").replace("≥", " większe lub równe ")
            .replace("≠", " różne od ").replace("≈", " w przybliżeniu ")
            .replace(" < ", " mniejsze od ").replace(" > ", " większe od ")
            .replace("×", " razy ").replace(" = ", " równa się ").replace("±", " plus minus ")
        s = Regex("(\\d)/(\\d)").replace(s) { "${it.groupValues[1]} na ${it.groupValues[2]}" }
        // jednostki złożone (kg/s, kJ/(kg K)) – „na”; ukośnik między słowami/skrótami (UPS/PV) – „lub”
        s = Regex("\\b([a-z][A-Za-z]{0,2})/([a-z(][A-Za-z]{0,2})").replace(s) { "${it.groupValues[1]} na ${it.groupValues[2]}" }
        s = Regex("([\\p{L}\\d])/(?=[\\p{L}])").replace(s) { "${it.groupValues[1]} lub " }
        s = Regex("\\b([A-Z]+)–([A-Z]+)\\b").replace(s) { "${letter(it.groupValues[1])} ${letter(it.groupValues[2])}" }
        symbols.forEach { (k, v) -> s = Regex("(?<![\\p{L}\\d_])${Regex.escape(k)}(?![\\p{L}\\d_])").replace(s, v) }
        ACRONYMS.forEach { (k, v) -> s = Regex("(?<![\\p{L}\\d-])${Regex.escape(k)}(?![\\p{L}\\d]|-[A-Z])").replace(s, v) }
        s = s.replace("_", " ")
        return s.lines().joinToString("\n") { whitespace.replace(it, " ").trim() }
    }

    /** Pojedyncze oznaczenia przewodów w zapisie typu „N–PE”. */
    private fun letter(token: String) = when (token) { "N" -> "en"; "L" -> "el"; else -> token }

    private fun pluralize(number: String, f: Forms): String {
        if (number.contains(',') || number.contains('.')) return f.fraction
        val n = number.toLongOrNull() ?: return f.many
        return when {
            n == 1L -> f.one
            n % 10 in 2..4 && n % 100 !in 12..14 -> f.few
            else -> f.many
        }
    }

    /** Próbka do odsłuchu w ustawieniach – zawiera symbole i skróty z lekcji. */
    const val PRONUNCIATION_SAMPLE =
        "Warunek Ib ≤ In ≤ Iz. Rezystancja 2,3 Ω, izolacja 1 MΩ, przekrój 2,5 mm². " +
            "RCD o IΔn 30 mA. Moc trójfazowa P = √3 × U × I × cos φ. Straty Pstrat = I²R. " +
            "Układ TN-C-S, przewody PE i PEN, zasilanie z UPS i instalacji PV, ograniczniki SPD. Warunek Zs × Ia ≤ U0."
}
