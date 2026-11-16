package pl.septrener.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.septrener.AppContainer
import pl.septrener.content.ContentItem
import pl.septrener.content.Group
import pl.septrener.data.Srs
import pl.septrener.playback.MediaIds

data class ExamConfig(val group: Group?, val dueOnly: Boolean, val shuffle: Boolean, val oral: Boolean)

data class ExamUiState(
    val loading: Boolean = true,
    val current: ContentItem? = null,
    val revealed: Boolean = false,
    /** Użytkownik nacisnął „Odpowiadam” – odliczanie wstrzymane. */
    val answering: Boolean = false,
    val finished: Boolean = false,
    val total: Int = 0,
    val remaining: Int = 0,
    val known: Int = 0,
    val hard: Int = 0,
)

/** Egzamin ustny (z lektorem) lub cichy quiz. Odtwarzanie odbywa się przez usługę w tle, więc działa też przy zablokowanym ekranie. */
class ExamViewModel(private val container: AppContainer, val config: ExamConfig) : ViewModel() {
    private val _ui = MutableStateFlow(ExamUiState())
    val ui: StateFlow<ExamUiState> = _ui.asStateFlow()
    private var session: ExamSession? = null

    init {
        viewModelScope.launch {
            val content = container.content.ensureLoaded()
            val progress = container.progress.all.first()
            val now = System.currentTimeMillis()
            val ids = content.questions(config.group)
                .filter { !config.dueOnly || Srs.isDue(progress[it.id], now) }
                .map { it.id }
            session = ExamSession(ids, shuffle = config.shuffle)
            publish(revealed = false)
            if (config.oral) playCurrent()
        }
    }

    private fun publish(revealed: Boolean = _ui.value.revealed, answering: Boolean = false) {
        val s = session ?: return
        val item = s.current?.let { container.content.state.value.item(it) }
        _ui.value = ExamUiState(
            loading = false, current = item, revealed = revealed, answering = answering, finished = s.finished,
            total = s.total, remaining = s.remaining, known = s.knownCount, hard = s.hardCount,
        )
    }

    fun playCurrent() {
        val item = _ui.value.current ?: return
        container.connection.playItems(listOf(MediaIds.exam(item.id)), 0, 0)
    }

    /** „Odpowiadam” – zatrzymuje odliczanie, użytkownik odpowiada na głos. */
    fun answering() {
        _ui.update { it.copy(answering = true) }
        if (config.oral) container.connection.pause()
    }

    /** „Pokaż odpowiedź” – pomija resztę pauzy i czyta odpowiedź. */
    fun reveal() {
        val item = _ui.value.current ?: return
        _ui.update { it.copy(revealed = true, answering = false) }
        if (config.oral) {
            val answerStart = container.trackFactory.examQuestion(item, container.currentSettings().examTiming).answerStart
            container.connection.playItems(listOf(MediaIds.exam(item.id)), 0, answerStart)
        }
    }

    /** „Umiem” (known=true) lub „Powtórz” (known=false – pytanie wróci w tej sesji i w powtórkach). */
    fun grade(known: Boolean) {
        val s = session ?: return
        val item = _ui.value.current ?: return
        viewModelScope.launch { container.progress.recordAnswer(item, known) }
        s.grade(known)
        publish(revealed = false)
        if (config.oral) {
            if (s.finished) container.connection.pause() else playCurrent()
        }
    }

    fun skip() {
        session?.skip()
        publish(revealed = false)
        if (config.oral) playCurrent()
    }

    override fun onCleared() {
        if (config.oral) container.connection.pause()
    }
}
