package pl.septrener.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import pl.septrener.AppContainer
import pl.septrener.tts.SpeechEngine
import pl.septrener.ui.displayTitle

/**
 * Odtwarzacz media3 oparty o systemowy TextToSpeech.
 *
 * Konwencja pozycji: `positionMs` w setMediaItems/seekTo oznacza INDEKS FRAGMENTU (nie milisekundy).
 * Czas trwania jest nieznany (C.TIME_UNSET), więc system nie pokazuje paska postępu.
 */
@UnstableApi
class TtsPlayer(
    context: Context,
    private val container: AppContainer,
    engine: SpeechEngine = container.tts,
) : SimpleBasePlayer(Looper.getMainLooper()), SpeechQueue.Callback {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = SpeechQueue(engine, SystemClock::elapsedRealtime, this)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumeOnFocusGain = false
    private var hasFocus = false
    private var noisyRegistered = false
    private var released = false
    private val wakeLock: PowerManager.WakeLock =
        appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SepTrener:tts").apply { setReferenceCounted(false) }

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        .setWillPauseWhenDucked(true) // mowy nie ściszamy – lepiej wstrzymać, by nie zgubić treści
        .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, mainHandler)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeOnFocusGain = false
                queue.pause() // odłączenie słuchawek -> pauza
            }
        }
    }

    init {
        scope.launch { restoreIfEmpty() }
    }

    // ---------------------------------------------------------------- media3 state

    override fun getState(): State {
        val tracks = queue.tracks
        val builder = State.Builder()
            .setAvailableCommands(availableCommands())
            .setPlayWhenReady(queue.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        if (tracks.isEmpty()) return builder.setPlaybackState(Player.STATE_IDLE).build()
        return builder
            .setPlaylist(tracks.map { t -> MediaItemData.Builder(t.mediaId).setMediaItem(mediaItemFor(t)).setDurationUs(C.TIME_UNSET).build() })
            .setCurrentMediaItemIndex(queue.trackIndex)
            .setContentPositionMs(0)
            .setPlaybackState(
                when {
                    queue.ended -> Player.STATE_ENDED
                    queue.waitingForEngine -> Player.STATE_BUFFERING
                    else -> Player.STATE_READY
                }
            )
            .build()
    }

    private fun availableCommands(): Player.Commands {
        val b = Player.Commands.Builder().addAll(
            COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SET_MEDIA_ITEM, COMMAND_CHANGE_MEDIA_ITEMS,
            COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA, COMMAND_RELEASE,
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_TO_DEFAULT_POSITION,
        )
        if (queue.tracks.isNotEmpty()) b.addAll(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT)
        if (queue.trackIndex > 0) b.add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        if (queue.trackIndex < queue.tracks.size - 1) b.add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        return b.build()
    }

    private fun mediaItemFor(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(displayTitle(track.title))
                .setArtist("SEP Trener · ${track.group.code}")
                .setAlbumTitle(if (track.kind == TrackKind.LESSON) "Lekcje ${track.group.code}" else "Egzamin ustny ${track.group.code}")
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        ).build()

    // ---------------------------------------------------------------- media3 handlers

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            resumeOnFocusGain = false
            if (requestFocus()) queue.play()
        } else {
            resumeOnFocusGain = false
            queue.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        resumeOnFocusGain = false
        queue.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val ids = mediaItems.map { it.mediaId }
        val requestedIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        val segment = if (startPositionMs == C.TIME_UNSET || startPositionMs < 0) 0 else startPositionMs.toInt()
        return scope.future {
            val content = container.content.ensureLoaded()
            val settings = container.loadSettings()
            val timing = settings.examTiming
            val resolved = ids.mapIndexedNotNull { i, id -> container.trackFactory.forMediaId(id, content, timing)?.let { i to it } }
            val tracks = resolved.map { it.second }
            val index = resolved.indexOfFirst { it.first >= requestedIndex }.coerceAtLeast(0)
            queue.autoAdvance = tracks.any { it.kind == TrackKind.EXAM_QUESTION } || settings.autoAdvanceLessons
            queue.setPlaylist(tracks, index, segment)
            persist()
        }
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET) {
            val segment = if (positionMs == C.TIME_UNSET || positionMs < 0) 0 else positionMs.toInt()
            queue.seek(mediaItemIndex, segment)
            persist()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        queue.release()
        releaseResources()
        abandonFocus()
        scope.cancel()
        container.publishPlayback(PlaybackUiState())
        return Futures.immediateVoidFuture()
    }

    // ---------------------------------------------------------------- queue callbacks

    override fun onQueueStateChanged() {
        if (released) return
        if (queue.playing) acquireResources() else {
            releaseResources()
            if (!resumeOnFocusGain) abandonFocus()
        }
        container.publishPlayback(
            PlaybackUiState(
                tracks = queue.tracks, trackIndex = queue.trackIndex, segmentIndex = queue.segmentIndex,
                playing = queue.playing, waitingForEngine = queue.waitingForEngine, ended = queue.ended,
                segmentStartedAt = queue.segmentStartedAt, error = queue.lastError,
            )
        )
        invalidateState()
    }

    override fun onSegmentStarted(track: Track, segmentIndex: Int) {
        persist()
        if (track.kind == TrackKind.LESSON) {
            val item = container.content.state.value.item(track.itemId) ?: return
            container.appScope.launch { container.progress.setLastSegment(item, segmentIndex) }
        }
    }

    override fun onTrackCompleted(track: Track) {
        if (track.kind == TrackKind.LESSON) {
            val item = container.content.state.value.item(track.itemId) ?: return
            container.appScope.launch { container.progress.lessonFinished(item) }
        }
    }

    // ---------------------------------------------------------------- persistence

    private fun persist() {
        val tracks = queue.tracks
        if (tracks.isEmpty()) return
        // Pojedyncze pytania z interaktywnego egzaminu nie nadpisują punktu wznowienia lekcji.
        if (tracks.size == 1 && tracks[0].kind == TrackKind.EXAM_QUESTION) return
        val saved = SavedPlayback(tracks.map { it.mediaId }, queue.trackIndex, queue.segmentIndex, System.currentTimeMillis())
        container.appScope.launch { container.playbackStore.save(saved) }
    }

    /** Odtworzenie stanu po ubiciu procesu: playlista i fragment z DataStore, odtwarzanie wstrzymane. */
    private suspend fun restoreIfEmpty() {
        if (queue.tracks.isNotEmpty()) return
        val saved = container.playbackStore.load() ?: run { onQueueStateChanged(); return }
        val content = container.content.ensureLoaded()
        val settings = container.loadSettings()
        val timing = settings.examTiming
        val restored = PlaybackRestorer.restore(saved) { container.trackFactory.forMediaId(it, content, timing) }
        if (restored == null || queue.tracks.isNotEmpty()) { onQueueStateChanged(); return }
        queue.autoAdvance = restored.tracks.any { it.kind == TrackKind.EXAM_QUESTION } || settings.autoAdvanceLessons
        queue.setPlaylist(restored.tracks, restored.trackIndex, restored.segmentIndex)
    }

    // ---------------------------------------------------------------- audio focus, noisy, wake lock

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val result = audioManager.requestAudioFocus(focusRequest)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                hasFocus = false
                queue.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // np. połączenie telefoniczne lub komunikat nawigacji
                if (queue.playing) {
                    resumeOnFocusGain = true
                    queue.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    queue.play()
                }
            }
        }
    }

    private fun acquireResources() {
        if (!wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(
                appContext, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            noisyRegistered = true
        }
    }

    private fun releaseResources() {
        if (wakeLock.isHeld) wakeLock.release()
        if (noisyRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L
    }
}
