package pl.septrener

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.septrener.content.ContentImporter
import pl.septrener.content.ContentRepository
import pl.septrener.content.Group
import pl.septrener.data.AppDatabase
import pl.septrener.data.ProgressBackup
import pl.septrener.data.ProgressRepository
import java.io.File

/**
 * Scenariusz z telefonu z poprzednią wersją (11 lekcji G1 + postępy) i import G1_lessons_rozszerzone.json.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class G1ExtensionImportTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    private val progress = ProgressRepository(db.progressDao())
    private val backupDir = File(ctx.filesDir, "backups-test-${System.nanoTime()}")
    private val backup = ProgressBackup(progress, backupDir)
    private val extDir = File(TestFiles.materials, "rozbudowa_G1")
    private fun ext(name: String) = File(extDir, name).readText()
    private val oldG1 = File(TestFiles.materials, "G1_lessons.json").readText()

    @After fun tearDown() {
        db.close(); backupDir.deleteRecursively(); File(ctx.filesDir, "packs").deleteRecursively()
    }

    /** Repozytorium „starej” wersji: bez wbudowanych lekcji G1, które dostarczamy jako 11 lekcji z pierwszego pakietu. */
    private suspend fun oldApp(): Pair<ContentRepository, ContentImporter> {
        File(ctx.filesDir, "packs").deleteRecursively()
        val repo = ContentRepository(ctx, builtIn = listOf("G1_exam.json"))
        repo.importText(oldG1, "a_stare_G1.json")
        assertEquals(11, repo.state.value.lessons(Group.G1).size)
        return repo to ContentImporter(repo, progress, backup)
    }

    @Test
    fun extensionAddsTenLessonsAndKeepsProgress() = runBlocking {
        val (repo, importer) = oldApp()
        val s0 = repo.state.value
        progress.toggleBookmark(s0.item("g1-lesson-03")!!)
        progress.setLastSegment(s0.item("g1-lesson-05")!!, 2)
        progress.setCompleted(s0.item("g1-lesson-01")!!, true)
        progress.recordAnswer(s0.item("g1-question-001")!!, known = true)

        val report = importer.importText(ext("G1_lessons_rozszerzone.json"), "G1_lessons_rozszerzone.json")
        assertNull(report.error)
        assertEquals(0, report.migratedFrom)
        assertEquals(21, report.imported)
        assertEquals((12..21).map { "g1-lesson-%02d".format(it) }, report.added.map { it.id })
        assertTrue(report.updated.isEmpty())
        assertEquals(11, report.unchanged)
        assertTrue(report.issues.isEmpty())
        assertEquals(3, report.progressKept)
        assertNotNull(report.backupFile)
        assertTrue(File(backupDir, report.backupFile!!).exists())

        val lessons = repo.state.value.lessons(Group.G1)
        assertEquals((1..21).map { "g1-lesson-%02d".format(it) }, lessons.map { it.id })
        assertTrue(lessons.all { !it.verified })
        assertTrue(progress.get("g1-lesson-03")!!.bookmarked)
        assertEquals(2, progress.get("g1-lesson-05")!!.lastSegment)
        assertTrue(progress.get("g1-lesson-01")!!.completed)
        assertEquals(1, progress.get("g1-question-001")!!.knownCount)

        // ponowny import „tylko nowe” nie tworzy duplikatów
        val again = importer.importText(ext("G1_lessons_tylko_nowe.json"), "G1_lessons_tylko_nowe.json")
        assertEquals(0, again.added.size); assertEquals(10, again.unchanged)
        assertEquals(21, repo.state.value.lessons(Group.G1).size)
    }

    @Test
    fun changedLessonResetsOnlyResumePoint() = runBlocking {
        val (repo, importer) = oldApp()
        val l2 = repo.state.value.item("g1-lesson-02")!!
        progress.setLastSegment(l2, 4); progress.toggleBookmark(l2)
        val changed = """[{"id":"g1-lesson-02","group":"G1","type":"lesson","title":"LEKCJA 2. Nowa wersja.","content":"Nowa treść lekcji.","verified":false}]"""
        val r = importer.importText(changed, "poprawka.json")
        assertEquals(listOf("g1-lesson-02"), r.updated.map { it.id })
        assertEquals(0, progress.get("g1-lesson-02")!!.lastSegment)
        assertTrue(progress.get("g1-lesson-02")!!.bookmarked)
        assertEquals("Nowa treść lekcji.", repo.state.value.item("g1-lesson-02")!!.content)
    }

    @Test
    fun invalidPackChangesNothingAndCreatesNoBackup() = runBlocking {
        val (repo, importer) = oldApp()
        val r = importer.importText("""[{"id":"x","group":"G1","type":"oral_question","question":"bez odpowiedzi"}]""", "zla.json")
        assertNotNull(r.error)
        assertNull(r.backupFile)
        assertTrue(backup.list().isEmpty())
        assertEquals(11, repo.state.value.lessons(Group.G1).size)
    }

    @Test
    fun backupCanBeRestored() = runBlocking {
        val (repo, _) = oldApp()
        val l = repo.state.value.item("g1-lesson-07")!!
        progress.toggleBookmark(l)
        val b = backup.create("test")
        progress.resetAll()
        assertNull(progress.get(l.id))
        assertEquals(1, backup.restore(b.file))
        assertTrue(progress.get(l.id)!!.bookmarked)
        assertEquals(listOf("test"), backup.list().map { it.reason })
    }
}
