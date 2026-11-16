package pl.septrener

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.septrener.content.ContentParser
import pl.septrener.content.ContentState
import pl.septrener.playback.ExamTiming
import pl.septrener.playback.MediaIds
import pl.septrener.playback.PlaybackRestorer
import pl.septrener.playback.PlaybackStore
import pl.septrener.playback.SavedPlayback
import pl.septrener.playback.SpeechQueue
import pl.septrener.playback.TrackFactory
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class PlaybackRestoreTest {
    private val factory = TrackFactory(3984)
    private val content = ContentState(true, ContentParser.parse(TestFiles.asset("G1_lessons.json"), "g1").items)
    private val resolve = { id: String -> factory.forMediaId(id, content, ExamTiming()) }

    /** Symulacja ubicia procesu: zapis w jednej instancji DataStore, odczyt w nowej. */
    @Test
    fun positionSurvivesProcessDeath() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val file = File(ctx.filesDir, "playback_test_${System.nanoTime()}.preferences_pb")
        val ids = content.items.map { MediaIds.lesson(it.id) }

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        PlaybackStore(PreferenceDataStoreFactory.create(scope = scope1) { file }).save(SavedPlayback(ids, 4, 2, 123))
        scope1.cancel()
        kotlinx.coroutines.delay(100)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val loaded = PlaybackStore(PreferenceDataStoreFactory.create(scope = scope2) { file }).load()!!
        scope2.cancel()
        assertEquals(SavedPlayback(ids, 4, 2, 123), loaded)

        val restored = PlaybackRestorer.restore(loaded, resolve)!!
        assertEquals(4, restored.trackIndex)
        assertEquals(2, restored.segmentIndex)

        // kolejka po odtworzeniu jest wstrzymana i wznawia od zapisanego fragmentu
        val engine = FakeSpeechEngine()
        val queue = SpeechQueue(engine, { 0 }, object : SpeechQueue.Callback {})
        queue.setPlaylist(restored.tracks, restored.trackIndex, restored.segmentIndex)
        assertEquals(false, queue.playing)
        assertEquals(0, engine.queued.size)
        queue.play()
        assertEquals(restored.tracks[4].segments[2].text, engine.queued.first().text)
    }

    @Test
    fun restoreIsGracefulWhenContentChanged() {
        val ids = listOf("lesson:usunieta", MediaIds.lesson("g1-lesson-02"), MediaIds.lesson("g1-lesson-03"))
        // bieżąca pozycja wskazuje lekcję 2, a indeks fragmentu jest za duży
        val r = PlaybackRestorer.restore(SavedPlayback(ids, 1, 999, 0), resolve)!!
        assertEquals(2, r.tracks.size)
        assertEquals(0, r.trackIndex)
        assertEquals(r.tracks[0].segments.size - 1, r.segmentIndex)
        // bieżąca pozycja usunięta -> start od początku pozostałych
        val r2 = PlaybackRestorer.restore(SavedPlayback(ids, 0, 3, 0), resolve)!!
        assertEquals(0, r2.trackIndex); assertEquals(0, r2.segmentIndex)
        assertNull(PlaybackRestorer.restore(SavedPlayback(listOf("lesson:x"), 0, 0, 0), resolve))
    }
}
