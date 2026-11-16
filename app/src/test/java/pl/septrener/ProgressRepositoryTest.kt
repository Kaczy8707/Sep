package pl.septrener

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.septrener.content.ContentItem
import pl.septrener.content.Group
import pl.septrener.content.ItemType
import pl.septrener.data.AppDatabase
import pl.septrener.data.ProgressRepository

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class ProgressRepositoryTest {
    private var now = 1_000L
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = ProgressRepository(db.progressDao()) { now }
    private val lesson = ContentItem("g1-lesson-01", Group.G1, ItemType.LESSON, "L", content = "x")
    private val question = ContentItem("g1-question-001", Group.G1, ItemType.ORAL_QUESTION, "Q", question = "q", answer = "a")

    @After fun close() = db.close()

    @Test
    fun lessonProgressIsPersisted() = runBlocking {
        repo.setLastSegment(lesson, 3)
        repo.toggleBookmark(lesson)
        var p = repo.get(lesson.id)!!
        assertEquals(3, p.lastSegment); assertTrue(p.bookmarked); assertFalse(p.completed)
        repo.lessonFinished(lesson)
        p = repo.get(lesson.id)!!
        assertTrue(p.completed); assertEquals(0, p.lastSegment); assertTrue(p.bookmarked)
        assertEquals(1, p.repetitions); assertEquals(now, p.lastReviewedAt)
        repo.setCompleted(lesson, false)
        assertFalse(repo.get(lesson.id)!!.completed)
    }

    @Test
    fun answersUpdateCountersAndSchedule() = runBlocking {
        repo.recordAnswer(question, known = false)
        now = 2_000
        repo.recordAnswer(question, known = true)
        val p = repo.all.first()[question.id]!!
        assertEquals(1, p.knownCount); assertEquals(1, p.hardCount); assertEquals(2, p.repetitions)
        assertFalse(p.markedHard); assertEquals(1, p.box); assertEquals(2_000L, p.lastReviewedAt)
        assertEquals("G1", p.groupCode)
    }

    @Test
    fun resetClearsEverything() = runBlocking {
        repo.recordAnswer(question, true); repo.toggleBookmark(lesson)
        repo.resetAll()
        assertTrue(repo.all.first().isEmpty())
    }
}
