package pl.septrener

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.septrener.content.ContentRepository
import pl.septrener.data.AppDatabase
import pl.septrener.data.ProgressRepository
import pl.septrener.playback.PlaybackConnection
import pl.septrener.playback.PlaybackStore
import pl.septrener.playback.PlaybackUiState
import pl.septrener.playback.TrackFactory
import pl.septrener.settings.AppSettings
import pl.septrener.settings.SettingsStore
import pl.septrener.settings.playbackDataStore
import pl.septrener.settings.settingsDataStore
import pl.septrener.tts.AndroidTtsEngine

/** Prosta, ręczna konfiguracja zależności (bez frameworka DI). */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val content = ContentRepository(app)
    val database: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()
    val progress = ProgressRepository(database.progressDao())
    val settingsStore = SettingsStore(app.settingsDataStore)
    val playbackStore = PlaybackStore(app.playbackDataStore)
    val tts by lazy { AndroidTtsEngine(app) }
    val trackFactory by lazy { TrackFactory(tts.maxInputLength) }
    val connection = PlaybackConnection(app, appScope)

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())
    fun currentSettings(): AppSettings = settings.value
    suspend fun loadSettings(): AppSettings = settingsStore.settings.first()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()
    fun publishPlayback(state: PlaybackUiState) { _playback.value = state }

    fun start() {
        appScope.launch { content.reload() }
        appScope.launch { settingsStore.settings.collect { tts.start(it.ttsConfig) } }
    }
}
