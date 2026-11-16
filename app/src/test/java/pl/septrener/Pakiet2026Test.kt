package pl.septrener

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.septrener.content.ContentItem
import pl.septrener.content.ContentParser
import pl.septrener.content.ContentRepository
import pl.septrener.content.Group
import pl.septrener.content.Scope
import pl.septrener.content.ScenarioList
import pl.septrener.content.SourcesInfo
import pl.septrener.playback.ExamTiming
import pl.septrener.playback.SegmentKind
import pl.septrener.playback.TrackFactory
import java.io.File

/** Walidacja pakietu 2026 względem manifestu i plików porównawczych „rozszerzone”. */
class Pakiet2026Test {
    private val pkg = File(TestFiles.materials, "pakiet_2026")
    private fun pkgItems(name: String) = ContentParser.parse(File(pkg, name).readText(), name).items
    private val builtIn: List<ContentItem> = ContentRepository.BUILT_IN.flatMap { ContentParser.parse(TestFiles.asset(it), it).items }
    private fun strip(l: List<ContentItem>) = l.map { it.copy(origin = "") }

    @Test
    fun allBuiltInIdsAreUnique() {
        val ids = builtIn.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(207, ids.size)
    }

    @Test
    fun countsMatchManifest() {
        val manifest = Json.parseToJsonElement(File(pkg, "manifest.json").readText()).jsonObject
        val lessons = manifest["lessons"]!!.jsonObject
        val questions = manifest["questions"]!!.jsonObject
        for (g in Group.entries) {
            assertEquals(g.code, lessons[g.code]!!.jsonPrimitive.int, builtIn.count { it.isLesson && it.group == g })
        }
        // manifest G1 liczy wyłącznie nowe pytania (24); 32 dotychczasowe pytania G1 pozostają w aplikacji
        assertEquals(questions["G1"]!!.jsonPrimitive.int + 32, builtIn.count { it.isQuestion && it.group == Group.G1 })
        assertEquals(questions["G2"]!!.jsonPrimitive.int, builtIn.count { it.isQuestion && it.group == Group.G2 })
        assertEquals(questions["G3"]!!.jsonPrimitive.int, builtIn.count { it.isQuestion && it.group == Group.G3 })
        assertTrue(Json.parseToJsonElement(File(pkg, "manifest.json").readText()).jsonObject["verified"]!!.jsonPrimitive.content == "false")
    }

    @Test
    fun builtInEqualsComparisonFiles() {
        for (g in Group.entries) {
            assertEquals(strip(pkgItems("${g.code}_lessons_pelne_rozszerzenie.json")), strip(builtIn.filter { it.isLesson && it.group == g }))
        }
        assertEquals(strip(pkgItems("G2_exam_rozszerzone.json")), strip(builtIn.filter { it.isQuestion && it.group == Group.G2 }))
        assertEquals(strip(pkgItems("G3_exam_rozszerzone.json")), strip(builtIn.filter { it.isQuestion && it.group == Group.G3 }))
        val g1q = builtIn.filter { it.isQuestion && it.group == Group.G1 }
        assertEquals(strip(pkgItems("G1_exam_rozszerzone.json")), strip(g1q.filter { it.id.startsWith("g1-question-ext-") }))
    }

    @Test
    fun previousRecordsAreUnchanged() {
        val g1Lessons21 = ContentParser.parse(File(TestFiles.materials, "rozbudowa_G1/G1_lessons_rozszerzone.json").readText(), "x").items
        assertEquals(strip(g1Lessons21), strip(builtIn.filter { it.isLesson && it.group == Group.G1 }.take(21)))
        val g1Questions = ContentParser.parse(TestFiles.asset("G1_exam.json"), "x").items
        assertEquals(32, g1Questions.size)
        assertEquals(22, g1Questions.count { it.id.startsWith("g1-question-0") })
        assertEquals(10, g1Questions.count { it.id.startsWith("g1-situation-") })
        val original = ContentParser.parse(File(TestFiles.materials, "content_manifest.json").readText(), "x").items
        val byId = builtIn.associateBy { it.id }
        original.forEach { o -> assertEquals(o.id, o.copy(origin = ""), byId.getValue(o.id).copy(origin = "")) }
    }

    @Test
    fun metadataIsConsistentAndNothingIsVerified() {
        assertTrue(builtIn.none { it.verified })
        val lessonIds = builtIn.filter { it.isLesson }.map { it.id }.toSet()
        val ext = builtIn.filter { it.id.contains("-question-ext-") }
        assertEquals(72, ext.size)
        ext.forEach { q ->
            assertTrue(q.id, q.sourceLessonId in lessonIds)
            assertTrue(q.id, q.scope != Scope.UNKNOWN)
            assertEquals(8, q.pauseSeconds)
        }
        for (g in Group.entries) {
            val e = ext.filter { it.group == g }
            assertEquals(listOf(6, 6, 12), listOf(e.count { it.scope == Scope.E }, e.count { it.scope == Scope.D }, e.count { it.scope == Scope.ED }))
        }
    }

    @Test
    fun scenarioListAndSourcesAreValid() {
        val ids = ScenarioList.parse(TestFiles.asset("scenarios.json"))
        val byId = builtIn.associateBy { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it, byId[it]?.isQuestion == true) }
        assertTrue(Group.entries.all { g -> ids.any { byId.getValue(it).group == g } })
        val src = SourcesInfo.parse(TestFiles.asset("sources.json"))
        assertTrue(src.officialSources.any { it.url.contains("2022/1392") })
        val listed = src.packages.flatMap { it.files }.joinToString(" ")
        ContentRepository.BUILT_IN.forEach { assertTrue(it, listed.contains(it)) }
    }

    /** Kontrola egzaminu ustnego dla każdego pytania: pytanie → pauza 8 s → odpowiedź, tekst mowy mieści się w limicie. */
    @Test
    fun everyQuestionBuildsValidOralExamTrack() {
        val factory = TrackFactory(3984)
        builtIn.filter { it.isQuestion }.forEach { q ->
            val t = factory.examQuestion(q, ExamTiming())
            val kinds = t.segments.map { it.kind }.distinct()
            assertEquals(q.id, listOf(SegmentKind.QUESTION, SegmentKind.PAUSE, SegmentKind.ANSWER, SegmentKind.GAP), kinds)
            assertEquals(8_000, t.segments[t.pauseIndex].durationMs)
            assertTrue(t.segments.filterNot { it.isSilence }.all { it.spoken.length <= 3984 && it.spoken.isNotBlank() })
        }
    }

    @Test
    fun combinedImportPackContainsExactlyNewRecords() {
        val r = ContentParser.parse(File(pkg, "SEP_2026_import_tylko_nowe.json").readText(), "imp")
        assertTrue(r.issues.isEmpty())
        assertEquals(90, r.items.size)
        assertEquals(strip(builtIn.filter { it.origin.contains("_2026.json") }).associateBy { it.id }, strip(r.items).associateBy { it.id })
    }
}
