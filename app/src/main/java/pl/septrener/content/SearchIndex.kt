package pl.septrener.content

import java.text.Normalizer

/** Proste wyszukiwanie pełnotekstowe w pamięci (ignoruje wielkość liter i polskie znaki diakrytyczne). */
object SearchIndex {
    fun normalize(text: String): String {
        val lower = text.lowercase().replace('ł', 'l')
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    data class Hit(val item: ContentItem, val score: Int, val snippet: String)

    fun search(items: List<ContentItem>, query: String, limit: Int = 100): List<Hit> {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.length >= 2 }
        if (terms.isEmpty()) return emptyList()
        return items.mapNotNull { item ->
            val title = normalize(item.title)
            val body = normalize(item.searchableText)
            if (!terms.all { body.contains(it) }) return@mapNotNull null
            val score = terms.sumOf { t -> (if (title.contains(t)) 10 else 0) + countOccurrences(body, t) }
            Hit(item, score, snippet(item.searchableText, body, terms.first()))
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun countOccurrences(text: String, term: String): Int {
        var count = 0; var i = text.indexOf(term)
        while (i >= 0) { count++; i = text.indexOf(term, i + term.length) }
        return count
    }

    /** Normalizacja zachowuje długość dla znaków polskich, więc indeksy w tekście znormalizowanym odpowiadają oryginałowi. */
    private fun snippet(original: String, normalized: String, term: String): String {
        val pos = normalized.indexOf(term)
        if (pos < 0 || normalized.length != original.length) return original.take(140).replace('\n', ' ')
        val start = (pos - 60).coerceAtLeast(0)
        val end = (pos + term.length + 80).coerceAtMost(original.length)
        return (if (start > 0) "…" else "") + original.substring(start, end).replace('\n', ' ') + (if (end < original.length) "…" else "")
    }
}
