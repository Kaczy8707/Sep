package pl.septrener.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.septrener.AppContainer
import pl.septrener.content.Group
import pl.septrener.content.Scope
import pl.septrener.data.Grade
import pl.septrener.exam.ExamSelection
import pl.septrener.exam.QuestionPool
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Remove
import pl.septrener.exam.ExamConfig
import pl.septrener.exam.ExamViewModel
import pl.septrener.playback.MediaIds
import pl.septrener.playback.SegmentKind
import pl.septrener.ui.NOT_OFFICIAL
import pl.septrener.ui.components.DisclaimerCard
import pl.septrener.ui.components.ItemBadges
import pl.septrener.ui.components.ScreenScaffold
import pl.septrener.ui.components.SectionTitle
import pl.septrener.ui.components.TtsStatusBanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamSetupScreen(
    container: AppContainer,
    gate: (() -> Unit) -> Unit,
    onStart: (ExamConfig) -> Unit,
    onListen: () -> Unit,
    onOpenSettings: () -> Unit,
    outerPadding: PaddingValues,
) {
    val content by container.content.state.collectAsStateWithLifecycle()
    val progress by container.progress.all.collectAsStateWithLifecycle(emptyMap())
    val settings by container.settings.collectAsStateWithLifecycle()
    val ttsStatus by container.tts.status.collectAsStateWithLifecycle()
    var groupCode by rememberSaveable { mutableStateOf<String?>(null) }
    var scopeName by rememberSaveable { mutableStateOf<String?>(null) }
    var poolName by rememberSaveable { mutableStateOf(QuestionPool.ALL.name) }
    var scenarios by rememberSaveable { mutableStateOf(false) }
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var pause by remember(settings.examPauseSeconds) { mutableFloatStateOf(settings.examPauseSeconds.toFloat()) }
    val scope = rememberCoroutineScope()
    val group = groupCode?.let { Group.fromCode(it) }
    val edScope = scopeName?.let { Scope.valueOf(it) }
    val pool = QuestionPool.valueOf(poolName)
    val now = remember { System.currentTimeMillis() }
    fun config(oral: Boolean) = ExamConfig(group, edScope, pool, shuffle, oral, scenarios)
    val questions = ExamSelection.select(content.items, progress, config(true), now)
    val unscoped = content.questions(group).count { it.scope == Scope.UNKNOWN }

    ScreenScaffold("Egzamin ustny", outerPadding) { inner ->
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisclaimerCard(extra = NOT_OFFICIAL)
            TtsStatusBanner(ttsStatus, onOpenSettings)
            SectionTitle("Grupa")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = group == null, onClick = { groupCode = null }, label = { Text("Wszystkie") })
                Group.entries.forEach { g -> FilterChip(selected = group == g, onClick = { groupCode = g.code }, label = { Text(g.code) }) }
            }
            SectionTitle("Zakres")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = edScope == null, onClick = { scopeName = null }, label = { Text("E i D – wszystkie") })
                FilterChip(selected = edScope == Scope.E, onClick = { scopeName = Scope.E.name }, label = { Text("E – eksploatacja") })
                FilterChip(selected = edScope == Scope.D, onClick = { scopeName = Scope.D.name }, label = { Text("D – dozór") })
            }
            Text(
                "Oznaczenie E/D jest propozycją redakcyjną z paczki, nie oficjalną kwalifikacją. Filtr E lub D obejmuje też pytania „E i D”. " +
                    "Pytania bez oznaczenia ($unscoped w wybranej grupie) są dostępne tylko w opcji „wszystkie”.",
                style = MaterialTheme.typography.bodySmall,
            )
            SectionTitle("Pytania")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuestionPool.entries.forEach { p -> FilterChip(selected = pool == p, onClick = { poolName = p.name }, label = { Text(p.label) }) }
            }
            if (pool == QuestionPool.ERRORS) Text("Pytania ocenione ostatnio „Powtórz” lub „Częściowo” (jeszcze nieopanowane).", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tylko scenariusze awaryjne („Co zrobiłbyś…?”)", Modifier.weight(1f)); Switch(scenarios, { scenarios = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Losowa kolejność", Modifier.weight(1f)); Switch(shuffle, { shuffle = it })
            }
            Text("Pauza na odpowiedź: ${pause.toInt()} s", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = pause, onValueChange = { pause = it }, valueRange = 3f..30f, steps = 26,
                onValueChangeFinished = { scope.launch { container.settingsStore.setExamPause(pause.toInt()) } },
            )
            Text("Liczba pytań: ${questions.size}", fontWeight = FontWeight.SemiBold)

            Button(
                onClick = { gate { onStart(config(true)) } },
                enabled = questions.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            ) { Icon(Icons.Filled.RecordVoiceOver, null); Spacer(Modifier.width(8.dp)); Text(if (scenarios) "Scenariusze z lektorem" else "Egzamin ustny z lektorem") }
            Text("Lektor czyta pytanie → odliczanie pauzy → lektor czyta odpowiedź. Oceniasz się: „Umiem”, „Częściowo”, „Powtórz”.", style = MaterialTheme.typography.bodySmall)

            OutlinedButton(
                onClick = { onStart(config(false)) },
                enabled = questions.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            ) { Icon(Icons.Filled.Quiz, null); Spacer(Modifier.width(8.dp)); Text("Cichy quiz (bez dźwięku)") }

            OutlinedButton(
                onClick = {
                    val ids = (if (shuffle) questions.shuffled() else questions).map { MediaIds.exam(it.id) }
                    gate { container.connection.playItems(ids, 0, 0); onListen() }
                },
                enabled = questions.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            ) { Icon(Icons.Filled.Headphones, null); Spacer(Modifier.width(8.dp)); Text("Słuchaj pytań w tle (także przy blokadzie)") }
        }
    }
}

@Composable
fun ExamSessionScreen(
    container: AppContainer,
    config: ExamConfig,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    outerPadding: PaddingValues,
) {
    val key = "exam-$config"
    val vm: ExamViewModel = viewModel(key = key, factory = viewModelFactory { initializer { ExamViewModel(container, config) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val playback by container.playback.collectAsStateWithLifecycle()
    val ttsStatus by container.tts.status.collectAsStateWithLifecycle()
    val item = ui.current

    ScreenScaffold(
        when {
            config.scenarios -> "Scenariusze awaryjne"
            config.oral -> "Egzamin ustny"
            else -> "Cichy quiz"
        },
        outerPadding, onBack,
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.loading) { Text("Przygotowuję pytania…"); return@Column }
            if (ui.total == 0) { Text("Brak pytań dla wybranych filtrów."); return@Column }
            LinearProgressIndicator(
                progress = { (ui.total - ui.remaining).toFloat() / ui.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Pozostało: ${ui.remaining} · Umiem: ${ui.known} · Częściowo: ${ui.partial} · Do powtórki: ${ui.hard}", style = MaterialTheme.typography.bodyMedium)
            if (ui.finished || item == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Koniec sesji", style = MaterialTheme.typography.headlineSmall)
                        Text("Oceny: umiem ${ui.known}, częściowo ${ui.partial}, do powtórki ${ui.hard}. Wyniki i historię zapisano w postępach.")
                        Button(onClick = onBack, Modifier.padding(top = 12.dp)) { Text("Wróć") }
                    }
                }
                return@Column
            }
            if (config.oral) TtsStatusBanner(ttsStatus, onOpenSettings)
            ItemBadges(item)

            val mediaId = MediaIds.exam(item.id)
            val isThis = config.oral && playback.isCurrent(mediaId)
            val seg = if (isThis) playback.segment else null
            val answerPhase = isThis && (seg?.kind == SegmentKind.ANSWER || seg?.kind == SegmentKind.GAP || playback.ended)
            val showAnswer = ui.revealed || answerPhase

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (config.scenarios || item.isScenario) "Co zrobiłbyś w tej sytuacji?" else "Pytanie", style = MaterialTheme.typography.labelLarge)
                    Text(item.question, style = MaterialTheme.typography.headlineSmall)
                }
            }

            if (config.oral) {
                val phase = when {
                    ui.answering -> "Odpowiadasz – odliczanie wstrzymane. Gdy skończysz, naciśnij „Pokaż odpowiedź”."
                    !isThis -> "Przygotowuję lektora…"
                    playback.waitingForEngine -> "Czekam na syntezator mowy…"
                    seg?.kind == SegmentKind.QUESTION && playback.playing -> "Lektor czyta pytanie…"
                    seg?.kind == SegmentKind.PAUSE && playback.playing -> null
                    seg?.kind == SegmentKind.ANSWER && playback.playing -> "Lektor czyta odpowiedź…"
                    playback.ended || seg?.kind == SegmentKind.GAP -> "Oceń swoją odpowiedź."
                    else -> "Wstrzymano."
                }
                if (phase != null) Text(phase, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (isThis && seg?.kind == SegmentKind.PAUSE && playback.playing && !ui.answering) {
                    Countdown(totalMs = seg.durationMs, startedAt = playback.segmentStartedAt)
                }
            }

            if (showAnswer) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Odpowiedź (materiał pomocniczy)", style = MaterialTheme.typography.labelLarge)
                        Text(item.answer, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (!showAnswer) {
                if (config.oral) {
                    Button(onClick = vm::answering, enabled = !ui.answering, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                        Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp)); Text("Odpowiadam")
                    }
                }
                OutlinedButton(onClick = vm::reveal, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    Icon(Icons.Filled.Visibility, null); Spacer(Modifier.width(8.dp)); Text("Pokaż odpowiedź")
                }
            } else {
                Text(
                    if (config.scenarios || item.isScenario) "Porównaj swoje działania z odpowiedzią: kolejność, bezpieczeństwo własne, alarmowanie, odpowiedzialność E/D."
                    else "Oceń swoją odpowiedź:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { vm.grade(Grade.KNOWN) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp)); Text("Umiem")
                }
                OutlinedButton(onClick = { vm.grade(Grade.PARTIAL) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    Icon(Icons.Filled.Remove, null); Spacer(Modifier.width(8.dp)); Text("Częściowo")
                }
                Button(
                    onClick = { vm.grade(Grade.HARD) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) { Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(8.dp)); Text("Powtórz") }
                Text("„Częściowo” – powtórka jutro. „Powtórz” – pytanie wróci jeszcze w tej sesji i trafi do „Pytań z błędów”.", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                if (config.oral) TextButton(onClick = vm::playCurrent) { Text("Przeczytaj pytanie ponownie") }
                TextButton(onClick = vm::skip) { Text("Pomiń na później") }
            }
        }
    }
}

@Composable
private fun Countdown(totalMs: Long, startedAt: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (true) { now = SystemClock.elapsedRealtime(); delay(100) }
    }
    val remaining = (totalMs - (now - startedAt)).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Czas na odpowiedź", style = MaterialTheme.typography.bodyLarge)
        Text(
            "${(remaining + 999) / 1000} s", style = MaterialTheme.typography.displayMedium,
        )
        LinearProgressIndicator(progress = { remaining.toFloat() / totalMs.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
    }
}
