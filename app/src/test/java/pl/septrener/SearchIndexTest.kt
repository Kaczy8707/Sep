package pl.septrener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.septrener.content.ContentParser
import pl.septrener.content.SearchIndex

class SearchIndexTest {
    private val items = listOf("G1_lessons.json", "G1_exam.json", "G3_exam.json")
        .flatMap { ContentParser.parse(TestFiles.asset(it), it).items }

    @Test
    fun ignoresCaseAndPolishDiacritics() {
        val a = SearchIndex.search(items, "PĘTLI zwarcia").map { it.item.id }
        val b = SearchIndex.search(items, "petli zwarcia").map { it.item.id }
        assertTrue(a.isNotEmpty())
        assertEquals(a, b)
        assertEquals(SearchIndex.search(items, "łuk").map { it.item.id }, SearchIndex.search(items, "LUK").map { it.item.id })
        assertTrue(SearchIndex.search(items, "luk").isNotEmpty())
        assertEquals(SearchIndex.normalize("Łódź ŻÓŁĆ"), "lodz zolc")
    }

    @Test
    fun allTermsMustMatchAndTitleRanksHigher() {
        val hits = SearchIndex.search(items, "RCD")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { SearchIndex.normalize(it.item.searchableText).contains("rcd") })
        assertTrue(SearchIndex.search(items, "rcd xyzabc").isEmpty())
        assertTrue(SearchIndex.search(items, "a").isEmpty())
        assertTrue(hits.first().snippet.lowercase().contains("rcd"))
    }
}
