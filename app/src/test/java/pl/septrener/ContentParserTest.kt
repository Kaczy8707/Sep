package pl.septrener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pl.septrener.content.ContentFormatException
import pl.septrener.content.ContentParser
import pl.septrener.content.Group
import pl.septrener.content.ItemType
import pl.septrener.content.Scope
import java.io.File

class ContentParserTest {

    @Test
    fun builtInAssetsParseWithoutIssues() {
        val expected = mapOf(
            "G1_lessons.json" to 11, "G2_lessons.json" to 10, "G3_lessons.json" to 10,
            "G1_exam.json" to 32, "G2_exam.json" to 22, "G3_exam.json" to 22,
        )
        for ((file, count) in expected) {
            val r = ContentParser.parse(TestFiles.asset(file), file)
            assertEquals(file, count, r.items.size)
            assertTrue("$file: ${r.issues}", r.issues.isEmpty())
            assertEquals("legacy array is migrated", 0, r.migratedFrom)
            assertTrue(r.items.all { !it.verified })
        }
    }

    @Test
    fun manifestV1MatchesSplitFiles() {
        val r = ContentParser.parse(File(TestFiles.materials, "content_manifest.json").readText(), "manifest")
        assertNull(r.migratedFrom)
        assertEquals(75, r.items.size)
        assertEquals(31, r.items.count { it.type == ItemType.LESSON })
        val g2 = ContentParser.parse(TestFiles.asset("G2_exam.json"), "g2").items
        val fromManifest = r.items.filter { it.group == Group.G2 && it.isQuestion }
        assertEquals(g2.map { it.id to it.answer }, fromManifest.map { it.id to it.answer })
    }

    @Test
    fun g1ExamWasConvertedVerbatimFromTxt() {
        val txt = File(TestFiles.materials, "G1_Audiobook_Dialogi_Egzaminacyjne.txt").readText()
        val items = ContentParser.parse(TestFiles.asset("G1_exam.json"), "g1").items
        items.forEach { q ->
            assertTrue(q.id, txt.contains(q.question))
            assertTrue(q.id, txt.contains("LEKTOR: ${q.answer}"))
        }
        val situations = items.filter { it.id.startsWith("g1-situation") }
        assertEquals(10, situations.size)
        assertTrue(situations.all { it.scope == Scope.D && it.pauseSeconds == 10 })
        assertTrue(items.filterNot { it.id.startsWith("g1-situation") }.all { it.scope == Scope.UNKNOWN && it.pauseSeconds == 8 })
    }

    @Test
    fun missingAnswerIsRejectedNotInvented() {
        val json = """[{"id":"x1","group":"G1","type":"oral_question","title":"t","question":"Pytanie?"},
            {"id":"x2","group":"G1","type":"oral_question","question":"Q","answer":"A"}]"""
        val r = ContentParser.parse(json, "t")
        assertEquals(listOf("x2"), r.items.map { it.id })
        assertEquals(1, r.issues.size)
        assertTrue(r.issues[0].message.contains("answer"))
        assertFalse(r.items[0].verified)
        assertEquals(Scope.UNKNOWN, r.items[0].scope)
    }

    @Test
    fun invalidItemsAreReported() {
        val json = """{"schemaVersion":1,"items":[
            {"id":"a","group":"G4","type":"lesson","content":"x"},
            {"id":"b","group":"G1","type":"video","content":"x"},
            {"id":"c","group":"G1","type":"lesson"},
            {"id":"d","group":"G1","type":"oral_question","question":"q","answer":"a","pauseSeconds":0},
            {"group":"G1","type":"lesson","content":"x"},
            {"id":"ok","group":"g2","type":"lesson","content":"Treść","scope":"E"},
            {"id":"ok","group":"G2","type":"lesson","content":"Duplikat"}
        ]}"""
        val r = ContentParser.parse(json, "t")
        assertEquals(listOf("ok"), r.items.map { it.id })
        assertEquals(Group.G2, r.items[0].group)
        assertEquals(Scope.E, r.items[0].scope)
        assertEquals(6, r.issues.size)
    }

    @Test
    fun newerSchemaIsRefused() {
        try {
            ContentParser.parse("""{"schemaVersion":99,"items":[]}""", "t"); fail()
        } catch (e: ContentFormatException) {
            assertTrue(e.message!!.contains("99"))
        }
    }

    @Test(expected = ContentFormatException::class)
    fun brokenJsonThrows() {
        ContentParser.parse("{ to nie jest json", "t")
    }

    @Test(expected = ContentFormatException::class)
    fun wrongShapeThrows() {
        ContentParser.parse("\"tekst\"", "t")
    }
}
