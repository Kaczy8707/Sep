package pl.septrener

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.septrener.content.ContentRepository
import pl.septrener.content.Group

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class OfflineAndImportTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun appDoesNotRequestInternet() {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val perms = info.requestedPermissions.orEmpty().toList()
        assertFalse(perms.contains(Manifest.permission.INTERNET))
        assertTrue(perms.contains(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK))
        assertTrue(perms.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun builtInContentLoadsFromAssetsOffline() = runBlocking {
        val repo = ContentRepository(ctx)
        val s = repo.ensureLoaded()
        assertTrue(s.loadIssues.toString(), s.loadIssues.isEmpty())
        assertEquals(11, s.lessons(Group.G1).size)
        assertEquals(10, s.lessons(Group.G2).size)
        assertEquals(10, s.lessons(Group.G3).size)
        assertEquals(32, s.questions(Group.G1).size)
        assertEquals(76, s.questions(null).size)
    }

    @Test
    fun importValidatesSavesAndReplaces() = runBlocking {
        val repo = ContentRepository(ctx)
        repo.ensureLoaded()
        val pack = """{"schemaVersion":1,"items":[
            {"id":"imp-1","group":"G2","type":"oral_question","question":"Nowe pytanie?","answer":"Odpowiedź z paczki."},
            {"id":"g1-question-001","group":"G1","type":"oral_question","question":"Zmienione?","answer":"Tak."},
            {"id":"bad","group":"G1","type":"oral_question","question":"Bez odpowiedzi"}
        ]}"""
        val report = repo.importText(pack, "moja paczka.json")
        assertNull(report.error)
        assertEquals(2, report.imported); assertEquals(1, report.replaced); assertEquals(1, report.issues.size)
        val s = repo.state.value
        assertNotNull(s.item("imp-1"))
        assertEquals("Zmienione?", s.item("g1-question-001")!!.question)
        assertEquals(listOf("moja_paczka.json"), s.importedPacks)

        assertNotNull(repo.importText("[]", "pusta.json").error)
        assertNotNull(repo.importText("{zly", "zly.json").error)
        assertEquals(1, repo.state.value.importedPacks.size)

        repo.deletePack("moja_paczka.json")
        assertNull(repo.state.value.item("imp-1"))
        assertEquals("Czym różni się E od D?", repo.state.value.item("g1-question-001")!!.question)
    }
}
