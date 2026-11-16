package pl.septrener.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.septrener.playback.ExamTiming
import pl.septrener.tts.TtsConfig

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback")

data class AppSettings(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String? = null,
    val enginePackage: String? = null,
    val examPauseSeconds: Int = ExamTiming.DEFAULT_PAUSE_SECONDS,
    val usePackPause: Boolean = false,
    val autoAdvanceLessons: Boolean = true,
    val notificationsAsked: Boolean = false,
) {
    val ttsConfig get() = TtsConfig(speechRate, pitch, voiceName, enginePackage)
    val examTiming get() = ExamTiming(examPauseSeconds, usePackPause)

    companion object {
        const val MIN_RATE = 0.7f
        const val MAX_RATE = 1.4f
        const val MIN_PITCH = 0.7f
        const val MAX_PITCH = 1.4f
    }
}

class SettingsStore(private val store: DataStore<Preferences>) {
    private object K {
        val rate = floatPreferencesKey("speech_rate")
        val pitch = floatPreferencesKey("pitch")
        val voice = stringPreferencesKey("voice_name")
        val engine = stringPreferencesKey("engine_package")
        val pause = intPreferencesKey("exam_pause_seconds")
        val packPause = booleanPreferencesKey("use_pack_pause")
        val autoAdvance = booleanPreferencesKey("auto_advance_lessons")
        val notifAsked = booleanPreferencesKey("notifications_asked")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            speechRate = (p[K.rate] ?: 1.0f).coerceIn(AppSettings.MIN_RATE, AppSettings.MAX_RATE),
            pitch = (p[K.pitch] ?: 1.0f).coerceIn(AppSettings.MIN_PITCH, AppSettings.MAX_PITCH),
            voiceName = p[K.voice],
            enginePackage = p[K.engine],
            examPauseSeconds = (p[K.pause] ?: ExamTiming.DEFAULT_PAUSE_SECONDS).coerceIn(ExamTiming.MIN_PAUSE_SECONDS, ExamTiming.MAX_PAUSE_SECONDS),
            usePackPause = p[K.packPause] ?: false,
            autoAdvanceLessons = p[K.autoAdvance] ?: true,
            notificationsAsked = p[K.notifAsked] ?: false,
        )
    }

    suspend fun setSpeechRate(v: Float) = store.edit { it[K.rate] = v.coerceIn(AppSettings.MIN_RATE, AppSettings.MAX_RATE) }
    suspend fun setPitch(v: Float) = store.edit { it[K.pitch] = v.coerceIn(AppSettings.MIN_PITCH, AppSettings.MAX_PITCH) }
    suspend fun setVoice(name: String?) = store.edit { if (name == null) it.remove(K.voice) else it[K.voice] = name }
    suspend fun setEngine(pkg: String?) = store.edit {
        if (pkg == null) it.remove(K.engine) else it[K.engine] = pkg
        it.remove(K.voice)
    }
    suspend fun setExamPause(seconds: Int) = store.edit { it[K.pause] = seconds.coerceIn(ExamTiming.MIN_PAUSE_SECONDS, ExamTiming.MAX_PAUSE_SECONDS) }
    suspend fun setUsePackPause(v: Boolean) = store.edit { it[K.packPause] = v }
    suspend fun setAutoAdvance(v: Boolean) = store.edit { it[K.autoAdvance] = v }
    suspend fun setNotificationsAsked() = store.edit { it[K.notifAsked] = true }
}
