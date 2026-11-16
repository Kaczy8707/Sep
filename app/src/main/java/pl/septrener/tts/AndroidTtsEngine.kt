package pl.septrener.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface TtsStatus {
    data object Initializing : TtsStatus
    data class Ready(val engineLabel: String) : TtsStatus
    /** Silnik działa, ale nie ma (lub nie ma pobranych danych) języka polskiego. */
    data class PolishMissing(val engineLabel: String, val missingData: Boolean) : TtsStatus
    data object NoEngine : TtsStatus
    data class Error(val message: String) : TtsStatus
}

data class VoiceInfo(
    val name: String,
    val localeTag: String,
    val quality: Int,
    val networkRequired: Boolean,
    val notInstalled: Boolean,
    /** Wskazówka z cech głosu, jeśli silnik ją podaje (wiele silników jej nie podaje). */
    val genderHint: String?,
) {
    val qualityLabel: String get() = when {
        quality >= Voice.QUALITY_VERY_HIGH -> "bardzo wysoka"
        quality >= Voice.QUALITY_HIGH -> "wysoka"
        quality >= Voice.QUALITY_NORMAL -> "normalna"
        else -> "niska"
    }
}

data class EngineInfo(val packageName: String, val label: String)

data class TtsConfig(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String? = null,
    val enginePackage: String? = null,
)

/** Silnik oparty o systemowy android.speech.tts.TextToSpeech, język pl-PL. Bez zewnętrznych API i bez MP3. */
class AndroidTtsEngine(context: Context) : SpeechEngine {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var listener: SpeechEngine.Listener? = null
    private var config = TtsConfig()
    private var initializedPackage: String? = null
    private var started = false

    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Initializing)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()
    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()
    private val _engines = MutableStateFlow<List<EngineInfo>>(emptyList())
    val engines: StateFlow<List<EngineInfo>> = _engines.asStateFlow()
    private val _activeVoice = MutableStateFlow<String?>(null)
    val activeVoice: StateFlow<String?> = _activeVoice.asStateFlow()
    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    override val maxInputLength: Int =
        (runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000) - 16).coerceIn(200, 3900)

    override val isReady: Boolean get() = _status.value is TtsStatus.Ready

    fun start(initial: TtsConfig) {
        if (started) { applyConfig(initial); return }
        started = true
        config = initial
        initialize(initial.enginePackage)
    }

    private fun initialize(enginePackage: String?) {
        tts?.let { runCatching { it.stop(); it.shutdown() } }
        _status.value = TtsStatus.Initializing
        listener?.onReadyChanged(false)
        initializedPackage = enginePackage
        var created: TextToSpeech? = null
        created = TextToSpeech(app, { code -> main.post { onInit(created, code) } }, enginePackage)
        tts = created
    }

    private fun onInit(instance: TextToSpeech?, code: Int) {
        if (instance == null || instance !== tts) return
        val engineList = runCatching { instance.engines.map { EngineInfo(it.name, it.label) } }.getOrDefault(emptyList())
        _engines.value = engineList
        if (code != TextToSpeech.SUCCESS) {
            _status.value = if (engineList.isEmpty()) TtsStatus.NoEngine
            else TtsStatus.Error("Nie udało się uruchomić syntezatora mowy (kod $code).")
            return
        }
        val engineLabel = engineList.firstOrNull { it.packageName == (instance.defaultEngine ?: "") && initializedPackage == null }?.label
            ?: engineList.firstOrNull { it.packageName == initializedPackage }?.label
            ?: instance.defaultEngine ?: "systemowy"
        instance.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        instance.setOnUtteranceProgressListener(progressListener)
        val langResult = instance.setLanguage(POLISH)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            _status.value = TtsStatus.PolishMissing(engineLabel, langResult == TextToSpeech.LANG_MISSING_DATA)
            _voices.value = emptyList()
            listener?.onReadyChanged(false)
            return
        }
        _voices.value = readPolishVoices(instance)
        applyVoiceAndParams(instance)
        _status.value = TtsStatus.Ready(engineLabel)
        listener?.onReadyChanged(true)
    }

    private fun readPolishVoices(instance: TextToSpeech): List<VoiceInfo> = runCatching {
        instance.voices.orEmpty()
            .filter { it.locale.language == POLISH.language }
            .map { v ->
                val features = v.features.orEmpty()
                VoiceInfo(
                    name = v.name,
                    localeTag = v.locale.toLanguageTag(),
                    quality = v.quality,
                    networkRequired = v.isNetworkConnectionRequired,
                    notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features,
                    genderHint = genderHint(v.name, features),
                )
            }
            .sortedWith(compareBy<VoiceInfo>({ it.notInstalled }, { it.networkRequired }, { -it.quality }, { it.name }))
    }.getOrElse { Log.w(TAG, "voices", it); emptyList() }

    private fun genderHint(name: String, features: Set<String>): String? {
        val all = (features + name).joinToString(" ").lowercase()
        return when {
            "female" in all -> "żeński (wg silnika)"
            "male" in all -> "męski (wg silnika)"
            else -> null
        }
    }

    /** Wybór głosu: zapisany przez użytkownika, w przeciwnym razie głos offline, z preferencją oznaczonych jako męskie. */
    private fun applyVoiceAndParams(instance: TextToSpeech) {
        val available = runCatching { instance.voices.orEmpty().filter { it.locale.language == POLISH.language } }.getOrDefault(emptyList())
        val usable = _voices.value.filter { !it.notInstalled }
        val chosenName = config.voiceName?.takeIf { n -> usable.any { it.name == n } }
            ?: usable.firstOrNull { !it.networkRequired && it.genderHint?.startsWith("męski") == true }?.name
            ?: usable.firstOrNull { !it.networkRequired }?.name
            ?: usable.firstOrNull()?.name
        available.firstOrNull { it.name == chosenName }?.let { runCatching { instance.voice = it } }
        _activeVoice.value = runCatching { instance.voice?.name }.getOrNull() ?: chosenName
        instance.setSpeechRate(config.speechRate)
        instance.setPitch(config.pitch)
    }

    fun applyConfig(newConfig: TtsConfig) {
        val old = config
        config = newConfig
        if (started && newConfig.enginePackage != old.enginePackage) { initialize(newConfig.enginePackage); return }
        val instance = tts ?: return
        if (_status.value is TtsStatus.Ready) applyVoiceAndParams(instance)
    }

    fun retry() = initialize(config.enginePackage)

    /** Odsłuch próbny – przerywa bieżącą wypowiedź. */
    fun speakTest(text: String) {
        val instance = tts ?: return
        if (!isReady) return
        instance.speak(text, TextToSpeech.QUEUE_FLUSH, null, TEST_PREFIX + System.nanoTime())
    }

    override fun setListener(listener: SpeechEngine.Listener?) {
        this.listener = listener
        listener?.onReadyChanged(isReady)
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        val instance = tts ?: return false
        return instance.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId) == TextToSpeech.SUCCESS
    }

    override fun silence(durationMs: Long, utteranceId: String): Boolean {
        val instance = tts ?: return false
        return instance.playSilentUtterance(durationMs, TextToSpeech.QUEUE_ADD, utteranceId) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        tts?.stop()
        _testing.value = false
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val id = utteranceId ?: return
            main.post { if (id.startsWith(TEST_PREFIX)) _testing.value = true else listener?.onUtteranceStart(id) }
        }

        override fun onDone(utteranceId: String?) {
            val id = utteranceId ?: return
            main.post { if (id.startsWith(TEST_PREFIX)) _testing.value = false else listener?.onUtteranceDone(id) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

        override fun onError(utteranceId: String?, errorCode: Int) {
            val id = utteranceId ?: return
            main.post { if (id.startsWith(TEST_PREFIX)) _testing.value = false else listener?.onUtteranceError(id, errorCode) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (utteranceId?.startsWith(TEST_PREFIX) == true) main.post { _testing.value = false }
        }
    }

    companion object {
        private const val TAG = "AndroidTtsEngine"
        private const val TEST_PREFIX = "test:"
        val POLISH: Locale = Locale("pl", "PL")
    }
}
