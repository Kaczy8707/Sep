package pl.septrener

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.septrener.playback.MediaIds
import pl.septrener.playback.SegmentKind
import pl.septrener.playback.TtsPlayer

/** Integracja odtwarzacza media3 (SimpleBasePlayer) z kolejką TTS, fokusem audio i zapisem pozycji. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class TtsPlayerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val container = AppContainer(app)

    private fun idle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun lessonIds() = runBlocking { container.content.ensureLoaded() }
        .lessons(pl.septrener.content.Group.G1).map { MediaIds.lesson(it.id) }

    @Test
    fun lessonPlaylistPlayPauseResumeNextAndRestore() {
        val engine = FakeSpeechEngine()
        val player = TtsPlayer(app, container, engine)
        idle()
        val ids = lessonIds()
        player.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).build() }, 1, 2L)
        player.prepare()
        idle()
        assertEquals(ids.size, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals("Lekcja 2 · Podstawy elektrotechniki i obliczenia", player.mediaMetadata.title.toString())

        player.play()
        idle()
        assertTrue(player.playWhenReady)
        val track = container.playback.value.track!!
        assertEquals(track.segments.drop(2).map { it.text }, engine.queued.map { it.text })
        val audio = shadowOf(app.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, audio.lastAudioFocusRequest.durationHint)

        engine.playNext()       // fragment 3 skończony
        engine.startNext()      // fragment 4 trwa
        idle()
        assertEquals(3, container.playback.value.segmentIndex)

        player.pause()
        idle()
        assertFalse(player.playWhenReady)
        assertTrue(engine.queued.isEmpty())
        player.play()
        idle()
        assertEquals(track.segments[3].text, engine.queued.first().text)

        // utrata fokusu (np. połączenie) -> pauza, odzyskanie -> wznowienie
        audio.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        idle()
        assertFalse(player.playWhenReady)
        audio.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        idle()
        assertTrue(player.playWhenReady)

        // odłączenie słuchawek -> pauza
        app.sendBroadcast(android.content.Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY).setPackage(app.packageName))
        idle()
        assertFalse(player.playWhenReady)

        player.seekToNextMediaItem()
        idle()
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(0, container.playback.value.segmentIndex)
        player.release()
        idle()

        // „ubicie procesu”: nowy odtwarzacz odczytuje zapisaną pozycję z DataStore, wstrzymany
        val restoredPlayer = TtsPlayer(app, container, FakeSpeechEngine())
        idle()
        assertEquals(ids.size, restoredPlayer.mediaItemCount)
        assertEquals(2, restoredPlayer.currentMediaItemIndex)
        assertFalse(restoredPlayer.playWhenReady)
        restoredPlayer.release()
        idle()
    }

    @Test
    fun examQuestionHasPauseAndEnds() {
        val engine = FakeSpeechEngine()
        val player = TtsPlayer(app, container, engine)
        idle()
        player.setMediaItems(listOf(MediaItem.Builder().setMediaId(MediaIds.exam("g1-question-003")).build()))
        player.play()
        idle()
        assertEquals(listOf(null, 8_000L, null, 1_200L), engine.queued.map { it.silenceMs })
        engine.playNext(); engine.startNext(); idle()
        assertEquals(SegmentKind.PAUSE, container.playback.value.segment!!.kind)
        repeat(3) { engine.playNext() }
        idle()
        assertEquals(Player.STATE_ENDED, player.playbackState)
        assertFalse(player.playWhenReady)
        player.release()
        idle()
    }

    @Test
    fun unknownMediaIdsAreSkippedGracefully() {
        val player = TtsPlayer(app, container, FakeSpeechEngine())
        idle()
        player.setMediaItems(listOf(MediaItem.Builder().setMediaId("lesson:nie-ma").build()))
        idle()
        assertEquals(0, player.mediaItemCount)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        player.play()
        idle()
        player.release()
        idle()
    }
}
