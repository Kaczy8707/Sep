package pl.septrener

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.septrener.content.ContentImporter
import pl.septrener.content.ContentRepository
import pl.septrener.content.Group
import pl.septrener.data.AppDatabase
import pl.septrener.data.Grade
import pl.septrener.data.ProgressBackup
import pl.septrener.data.ProgressRepository
import java.io.File

/** Aktualizacja treści 2026: przez nowe APK (wbudowane pliki) i przez import na poprzedniej wersji. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class ContentUpdateTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    private val progress = ProgressRepository(db.progressDao())
    private val previousBuiltIn = listOf("G1_lessons.json", "G1_exam.json", "G2_lessons.json", "G2_exam.json", "G3_lessons.json", "G3_exam.json")

    @After fun tearDown() { db.close(); File(ctx.filesDir, "packs").deleteRecursively() }

    private suspend fun seedProgress(repo: ContentRepository) {
        val s = repo.ensureLoaded()
        progress.toggleBookmark(s.item("g1-lesson-21")!!)
        progress.setLastSegment(s.item("g2-lesson-04")!!, 2)
        progress.recordAnswer(s.item("g1-situation-003")!!, Grade.HARD, "oral")
        progress.recordAnswer(s.item("g3-question-010")!!, Grade.KNOWN, "quiz")
    }

    private suspend fun assertProgressKept(repo: ContentRepository) {
        val s = repo.state.value
        listOf("g1-lesson-21", "g2-lesson-04", "g1-situation-003", "g3-question-010").forEach { assertTrue(it, s.item(it) != null) }
        assertTrue(progress.get("g1-lesson-21")!!.bookmarked)
        assertEquals(2, progress.get("g2-lesson-04")!!.lastSegment)
        assertTrue(progress.get("g1-situation-003")!!.markedHard)
        assertEquals(1, progress.get("g3-question-010")!!.knownCount)
        assertEquals(1, progress.history("g1-situation-003").size)
    }

    @Test
    fun newApkKeepsOldRecordsAndProgress() = runBlocking {
        File(ctx.filesDir, "packs").deleteRecursively()
        val old = ContentRepository(ctx, previousBuiltIn)
        seedProgress(old)
        val oldItems = old.state.value.items.associateBy { it.id }

        val updated = ContentRepository(ctx)
        val s = updated.ensureLoaded()
        assertTrue(s.loadIssues.toString(), s.loadIssues.isEmpty())
        oldItems.forEach { (id, item) -> assertEquals(id, item.copy(origin = "", isScenario = false), s.item(id)!!.copy(origin = "", isScenario = false)) }
        assertEquals(207, s.items.size)
        assertEquals(90, s.items.size - oldItems.size)
        assertEquals((1..27).map { "g1-lesson-%02d".format(it) }, s.lessons(Group.G1).map { it.id })
        assertEquals(24, s.items.count { it.isScenario })
        assertProgressKept(updated)
    }

    @Test
    fun importOnPreviousVersionAddsOnlyNewRecords() = runBlocking {
        File(ctx.filesDir, "packs").deleteRecursively()
        val old = ContentRepository(ctx, previousBuiltIn)
        seedProgress(old)
        val backupDir = File(ctx.filesDir, "upd-backups-${System.nanoTime()}")
        val importer = ContentImporter(old, progress, ProgressBackup(progress, backupDir))
        val text = File(TestFiles.materials, "pakiet_2026/SEP_2026_import_tylko_nowe.json").readText()
        val r = importer.importText(text, "SEP_2026_import_tylko_nowe.json")
        assertNull(r.error)
        assertEquals(90, r.added.size); assertEquals(0, r.updated.size); assertEquals(0, r.unchanged)
        assertTrue(File(backupDir, r.backupFile!!).exists())
        assertEquals(27, old.state.value.lessons(Group.G1).size)
        assertEquals(148, old.state.value.questions(null).size)
        assertProgressKept(old)
        // powtórny import: bez duplikatów
        val again = importer.importText(text, "SEP_2026_import_tylko_nowe.json")
        assertEquals(0, again.added.size); assertEquals(90, again.unchanged)
        assertEquals(148, old.state.value.questions(null).size)
        backupDir.deleteRecursively()
        Unit
    }
}
