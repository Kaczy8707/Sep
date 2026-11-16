package pl.septrener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.septrener.content.ContentParser
import pl.septrener.tts.TextChunker

class TextChunkerTest {
    private fun squash(s: String) = s.replace(Regex("\\s+"), "")

    private fun assertValid(text: String, max: Int, preferred: Int = TextChunker.DEFAULT_PREFERRED) {
        val chunks = TextChunker.chunk(text, max, preferred)
        assertTrue(chunks.isNotEmpty())
        chunks.forEach {
            assertTrue("chunk ${it.length} > $max", it.length <= max)
            assertTrue(it.isNotBlank())
        }
        assertEquals("content preserved", squash(text), squash(chunks.joinToString(" ")))
    }

    @Test
    fun allRealLessonsChunkWithinLimits() {
        listOf("G1_lessons.json", "G2_lessons.json", "G3_lessons.json").forEach { f ->
            ContentParser.parse(TestFiles.asset(f), f).items.forEach { lesson ->
                assertValid(lesson.content, 3984)
                assertValid(lesson.content, 120, 100)
                assertValid(lesson.content, 20, 20)
            }
        }
    }

    @Test
    fun shortParagraphsStayWhole() {
        val text = "Pierwszy akapit.\n\nDrugi akapit z nagłówkiem?.\nTreść drugiego."
        assertEquals(listOf("Pierwszy akapit.", "Drugi akapit z nagłówkiem?.\nTreść drugiego."), TextChunker.chunk(text, 4000))
    }

    @Test
    fun longParagraphSplitsAtSentenceBoundaries() {
        val s = "To jest zdanie numer jeden. "
        val text = (1..40).joinToString("") { s }.trim()
        val chunks = TextChunker.chunk(text, 4000, 100)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it, it.endsWith(".")) }
    }

    @Test
    fun abbreviationsFollowedByLowercaseDoNotSplit() {
        val sentences = TextChunker.splitSentences("Stosuje się m.in. zabezpieczenia, np. zawory. Kolejne zdanie.")
        assertEquals(listOf("Stosuje się m.in. zabezpieczenia, np. zawory.", "Kolejne zdanie."), sentences)
    }

    @Test
    fun hugeWordIsHardSplit() {
        val text = "a".repeat(250)
        val chunks = TextChunker.chunk(text, 100, 100)
        assertEquals(3, chunks.size)
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun blankTextGivesNoChunks() {
        assertTrue(TextChunker.chunk("  \n\n ", 100).isEmpty())
    }
}
