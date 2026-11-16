package pl.septrener.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
/** Połączenie UI z usługą odtwarzania przez MediaController (gwarantuje poprawny start usługi pierwszoplanowej). */
class PlaybackConnection(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null

    suspend fun controller(): MediaController {
        val f = future ?: MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync().also { future = it }
        return f.await()
    }

    fun connect() { scope.launch { runCatching { controller() } } }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    private fun run(block: (MediaController) -> Unit) {
        scope.launch { runCatching { block(controller()) } }
    }

    /** Ustawia playlistę i (opcjonalnie) startuje od fragmentu [segment] ścieżki [startIndex]. */
    fun playItems(mediaIds: List<String>, startIndex: Int = 0, segment: Int = 0, autoplay: Boolean = true) = run { c ->
        c.setMediaItems(mediaIds.map { MediaItem.Builder().setMediaId(it).build() }, startIndex, segment.toLong())
        c.prepare()
        if (autoplay) c.play()
    }

    fun play() = run { it.play() }
    fun pause() = run { it.pause() }
    fun next() = run { it.seekToNextMediaItem() }
    fun previous() = run { it.seekToPreviousMediaItem() }
    fun seekSegment(segment: Int, thenPlay: Boolean = false) = run { c ->
        c.seekTo(segment.toLong())
        if (thenPlay) c.play()
    }
    fun seek(track: Int, segment: Int) = run { it.seekTo(track, segment.toLong()) }
}
