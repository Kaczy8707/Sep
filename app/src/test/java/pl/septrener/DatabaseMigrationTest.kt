package pl.septrener

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.septrener.content.ContentItem
import pl.septrener.content.Group
import pl.septrener.content.ItemType
import pl.septrener.data.AppDatabase
import pl.septrener.data.Grade
import pl.septrener.data.ProgressBackup
import pl.septrener.data.ProgressRepository
import java.io.File

/** Migracja bazy v1 → v2 z zachowaniem postępów (schematy w app/schemas). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class DatabaseMigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsProgress() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO progress (itemId, groupCode, type, completed, bookmarked, lastSegment, knownCount, hardCount, repetitions, box, markedHard, lastReviewedAt, nextDueAt) " +
                    "VALUES ('g1-lesson-05', 'G1', 'lesson', 0, 1, 3, 0, 0, 0, 0, 0, NULL, NULL), " +
                    "('g1-question-001', 'G1', 'oral_question', 0, 0, 0, 2, 1, 3, 2, 0, 100, 200)"
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2).close()

        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        runBlocking {
            val dao = db.progressDao()
            val lesson = dao.get("g1-lesson-05")!!
            assertTrue(lesson.bookmarked); assertEquals(3, lesson.lastSegment); assertEquals(0, lesson.partialCount)
            val q = dao.get("g1-question-001")!!
            assertEquals(listOf(2, 1, 3, 2), listOf(q.knownCount, q.hardCount, q.repetitions, q.box))
            assertEquals(200L, q.nextDueAt)

            // po migracji działa historia odpowiedzi i kopia zapasowa z historią
            val repo = ProgressRepository(dao) { 5_000 }
            val item = ContentItem("g1-question-001", Group.G1, ItemType.ORAL_QUESTION, "t", question = "q", answer = "a")
            repo.recordAnswer(item, Grade.PARTIAL, "scenario")
            assertEquals(listOf(Grade.PARTIAL.code to "scenario"), repo.history(item.id).map { it.grade to it.mode })
            assertEquals(1, dao.get(item.id)!!.partialCount)
            val backup = ProgressBackup(repo, File(ctx.filesDir, "mig-backups-${System.nanoTime()}"))
            val b = backup.create("test")
            repo.resetAll()
            assertTrue(repo.history(item.id).isEmpty())
            backup.restore(b.file)
            assertEquals(1, repo.history(item.id).size)
            assertTrue(dao.get("g1-lesson-05")!!.bookmarked)
        }
        db.close()
    }
}
