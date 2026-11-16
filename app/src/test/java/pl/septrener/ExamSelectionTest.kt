package pl.septrener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.septrener.content.ContentItem
import pl.septrener.content.Group
import pl.septrener.content.ItemType
import pl.septrener.content.Scope
import pl.septrener.data.Grade
import pl.septrener.data.ItemProgress
import pl.septrener.data.Srs
import pl.septrener.exam.ExamConfig
import pl.septrener.exam.ExamSelection
import pl.septrener.exam.ExamSession
import pl.septrener.exam.QuestionPool

class ExamSelectionTest {
    private fun q(id: String, g: Group, s: Scope, scenario: Boolean = false) =
        ContentItem(id, g, ItemType.ORAL_QUESTION, id, question = "q", answer = "a", scope = s, isScenario = scenario)
    private val items = listOf(
        q("e1", Group.G1, Scope.E), q("d1", Group.G1, Scope.D), q("ed1", Group.G1, Scope.ED),
        q("u1", Group.G1, Scope.UNKNOWN, scenario = true), q("e2", Group.G2, Scope.E),
        ContentItem("l1", Group.G1, ItemType.LESSON, "l", content = "x"),
    )
    private fun ids(c: ExamConfig, p: Map<String, ItemProgress> = emptyMap()) = ExamSelection.select(items, p, c, 1000).map { it.id }

    @Test
    fun scopeFilterIncludesSharedButNotUnmarked() {
        assertEquals(listOf("e1", "ed1"), ids(ExamConfig(Group.G1, Scope.E)))
        assertEquals(listOf("d1", "ed1"), ids(ExamConfig(Group.G1, Scope.D)))
        assertEquals(listOf("e1", "d1", "ed1", "u1"), ids(ExamConfig(Group.G1)))
        assertEquals(listOf("e1", "ed1", "e2"), ids(ExamConfig(null, Scope.E)))
    }

    @Test
    fun scenarioAndErrorPools() {
        assertEquals(listOf("u1"), ids(ExamConfig(null, scenarios = true)))
        val p = mapOf(
            "e1" to Srs.grade(ItemProgress("e1", "G1", "oral_question"), Grade.HARD, 0),
            "d1" to Srs.grade(ItemProgress("d1", "G1", "oral_question"), Grade.PARTIAL, 0),
            "ed1" to Srs.grade(ItemProgress("ed1", "G1", "oral_question"), Grade.KNOWN, 0),
        )
        assertEquals(listOf("e1", "d1"), ids(ExamConfig(null, pool = QuestionPool.ERRORS), p))
        // po „Umiem” pytanie wychodzi z puli błędów
        val fixed = p + ("e1" to Srs.grade(p.getValue("e1"), Grade.KNOWN, 10))
        assertEquals(listOf("d1"), ids(ExamConfig(null, pool = QuestionPool.ERRORS), fixed))
        // DUE: nieocenione + e1 (ocena teraz) – ed1 i d1 zaplanowane na później
        assertEquals(listOf("e1", "u1", "e2"), ids(ExamConfig(null, pool = QuestionPool.DUE), p))
    }

    @Test
    fun partialGradeSchedulesNextDayAndIsNotRequeued() {
        val day = 24 * 3600 * 1000L
        val p = Srs.grade(ItemProgress("x", "G1", "oral_question", box = 4), Grade.PARTIAL, 0)
        assertEquals(1, p.box); assertEquals(day, p.nextDueAt); assertEquals(1, p.partialCount); assertTrue(!p.markedHard)
        val s = ExamSession(listOf("a", "b"), shuffle = false)
        s.grade(Grade.PARTIAL); s.grade(Grade.HARD)
        assertEquals("b", s.current)
        s.grade(Grade.KNOWN)
        assertTrue(s.finished)
        assertEquals(listOf(1, 1, 1), listOf(s.partialCount, s.hardCount, s.knownCount))
    }
}
