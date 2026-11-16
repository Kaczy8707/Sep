package pl.septrener.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.septrener.AppContainer
import pl.septrener.BuildConfig
import pl.septrener.content.ImportReport
import pl.septrener.settings.AppSettings
import pl.septrener.tts.TtsNormalizer
import pl.septrener.tts.TtsStatus
import pl.septrener.data.BackupInfo
import pl.septrener.ui.displayTitle
import java.text.DateFormat
import java.util.Date
import pl.septrener.ui.DISCLAIMER
import pl.septrener.ui.components.ScreenScaffold
import pl.septrener.ui.components.SectionTitle
import java.util.Locale

private const val SAMPLE = "Dzień dobry. To jest odsłuch próbny lektora aplikacji SEP Trener. Przed rozpoczęciem pracy sprawdź brak napięcia."

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer, outerPadding: PaddingValues, onOpenSources: () -> Unit = {}) {
    val ctx = LocalContext.current
    val settings by container.settings.collectAsStateWithLifecycle()
    val status by container.tts.status.collectAsStateWithLifecycle()
    val voices by container.tts.voices.collectAsStateWithLifecycle()
    val engines by container.tts.engines.collectAsStateWithLifecycle()
    val activeVoice by container.tts.activeVoice.collectAsStateWithLifecycle()
    val testing by container.tts.testing.collectAsStateWithLifecycle()
    val content by container.content.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
    var pitch by remember(settings.pitch) { mutableFloatStateOf(settings.pitch) }
    var pause by remember(settings.examPauseSeconds) { mutableFloatStateOf(settings.examPauseSeconds.toFloat()) }
    var report by remember { mutableStateOf<ImportReport?>(null) }
    var importing by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var confirmRestore by remember { mutableStateOf<BackupInfo?>(null) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { backups = container.progressBackup.list() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            scope.launch {
                report = container.importer.import(uri, displayName(ctx, uri))
                backups = container.progressBackup.list()
                importing = false
            }
        }
    }

    fun speak(text: String) {
        container.connection.pause()
        container.tts.speakTest(if (settings.speakSymbols) TtsNormalizer.normalize(text) else text)
    }
    fun test() = speak(SAMPLE)

    ScreenScaffold("Ustawienia", outerPadding) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Syntezator mowy (TextToSpeech, pl-PL)")
            val statusText = when (val s = status) {
                TtsStatus.Initializing -> "Uruchamianie…"
                is TtsStatus.Ready -> "Gotowy · silnik: ${s.engineLabel} · język polski dostępny"
                is TtsStatus.PolishMissing -> "Silnik ${s.engineLabel}: " + if (s.missingData) "brak pobranych danych głosu polskiego" else "brak obsługi języka polskiego"
                TtsStatus.NoEngine -> "Brak silnika syntezy mowy w systemie"
                is TtsStatus.Error -> s.message
            }
            Text(statusText, fontWeight = FontWeight.SemiBold, color = if (status is TtsStatus.Ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (status !is TtsStatus.Ready && status != TtsStatus.Initializing) {
                Card {
                    Text(
                        "Jak zainstalować polski głos:\n" +
                            "1. Otwórz Ustawienia systemu → Ułatwienia dostępu (lub System → Języki) → Zamiana tekstu na mowę.\n" +
                            "2. Wybierz preferowany mechanizm (np. Usługi rozpoznawania mowy Google).\n" +
                            "3. Otwórz ustawienia mechanizmu → Zainstaluj dane głosowe → Polski i pobierz głos.\n" +
                            "4. Wróć do aplikacji i naciśnij „Sprawdź ponownie”.\n" +
                            "Nazwy menu różnią się zależnie od producenta telefonu.",
                        Modifier.padding(12.dp),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openIntent(ctx, Intent("com.android.settings.TTS_SETTINGS")) }) { Text("Ustawienia syntezatora") }
                OutlinedButton(onClick = { openIntent(ctx, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }) { Text("Pobierz dane głosu") }
                OutlinedButton(onClick = { container.tts.retry() }) { Text("Sprawdź ponownie") }
            }

            if (engines.size > 1) {
                SectionTitle("Silnik")
                RadioRow("Domyślny systemowy", settings.enginePackage == null) { scope.launch { container.settingsStore.setEngine(null) } }
                engines.forEach { e ->
                    RadioRow(e.label, settings.enginePackage == e.packageName) { scope.launch { container.settingsStore.setEngine(e.packageName) } }
                }
            }

            SectionTitle("Głos")
            Text(
                "Aplikacja preferuje głos męski, ale system nie gwarantuje płci głosu – nie każdy silnik ją podaje. Odsłuchaj głosy i wybierz najlepszy.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (voices.isEmpty()) Text("Brak listy polskich głosów (zależy od silnika).", style = MaterialTheme.typography.bodyMedium)
            voices.forEach { v ->
                val desc = buildList {
                    add(if (v.networkRequired) "wymaga internetu" else "offline")
                    add("jakość ${v.qualityLabel}")
                    if (v.notInstalled) add("nie pobrany")
                    v.genderHint?.let { add(it) }
                }.joinToString(" · ")
                RadioRow(
                    title = v.name, subtitle = desc,
                    selected = (settings.voiceName ?: activeVoice) == v.name, enabled = !v.notInstalled,
                ) {
                    scope.launch { container.settingsStore.setVoice(v.name) }
                }
            }
            Button(onClick = ::test, enabled = status is TtsStatus.Ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (testing) "Odtwarzam próbkę…" else "Odsłuch próbny")
            }

            Text("Prędkość mowy: ${fmt(rate)}×", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = rate, onValueChange = { rate = it }, valueRange = AppSettings.MIN_RATE..AppSettings.MAX_RATE, steps = 13,
                onValueChangeFinished = { scope.launch { container.settingsStore.setSpeechRate(rate) } },
            )
            Text("Wysokość głosu: ${fmt(pitch)}", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = pitch, onValueChange = { pitch = it }, valueRange = AppSettings.MIN_PITCH..AppSettings.MAX_PITCH, steps = 13,
                onValueChangeFinished = { scope.launch { container.settingsStore.setPitch(pitch) } },
            )

            SectionTitle("Wymowa symboli i skrótów")
            SwitchRow("Czytaj symbole i skróty słownie (zalecane)", settings.speakSymbols) { v -> scope.launch { container.settingsStore.setSpeakSymbols(v) } }
            Text(
                "Np. „Ω” → „om”, „≤” → „mniejsze lub równe”, „√3” → „pierwiastek z trzech”, „RCD” → „er ce de”, „TN-C-S” → „te en ce es”. " +
                    "Zmienia tylko to, co słyszysz – tekst na ekranie zostaje bez zmian. Zmiana działa od następnego uruchomienia lekcji.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { speak(TtsNormalizer.PRONUNCIATION_SAMPLE) }, enabled = status is TtsStatus.Ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Test wymowy wzorów i skrótów")
            }
            if (settings.speakSymbols) Text("Odczyt: " + TtsNormalizer.normalize(TtsNormalizer.PRONUNCIATION_SAMPLE), style = MaterialTheme.typography.bodySmall)

            SectionTitle("Odtwarzanie i egzamin")
            SwitchRow("Po lekcji przechodź automatycznie do następnej", settings.autoAdvanceLessons) { v -> scope.launch { container.settingsStore.setAutoAdvance(v) } }
            Text("Pauza na odpowiedź: ${pause.toInt()} s (domyślnie 8 s)", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = pause, onValueChange = { pause = it }, valueRange = 3f..30f, steps = 26,
                onValueChangeFinished = { scope.launch { container.settingsStore.setExamPause(pause.toInt()) } },
            )
            SwitchRow("Używaj pauz zapisanych w paczce (jeśli podano)", settings.usePackPause) { v -> scope.launch { container.settingsStore.setUsePackPause(v) } }

            SectionTitle("Treści")
            Text(
                "Wbudowane: ${content.items.count { it.isLesson }} lekcji, ${content.items.count { it.isQuestion }} pytań. " +
                    "Wszystkie mają status „wymaga weryfikacji”.",
            )
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !importing, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(if (importing) "Importuję…" else "Importuj paczkę JSON") }
            Text("Paczka jest sprawdzana (schemat, wymagane pola, grupy G1–G3). Pozycje bez odpowiedzi są odrzucane.", style = MaterialTheme.typography.bodySmall)
            content.importedPacks.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { container.content.deletePack(name) } }) { Icon(Icons.Filled.Delete, contentDescription = "Usuń paczkę $name") }
                }
            }
            Text(
                "Przed każdym importem tworzona jest kopia postępów (zakładki, ukończenia, oceny). " +
                    "Postępy są przypisane do identyfikatorów, więc import tych samych id ich nie usuwa.",
                style = MaterialTheme.typography.bodySmall,
            )
            backups.firstOrNull()?.let { b ->
                Text("Kopie postępów: ${backups.size} · ostatnia: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.createdAt))} (${b.reason}, ${b.itemCount} pozycji)", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { confirmRestore = b }) { Text("Przywróć ostatnią kopię postępów") }
            }
            restoreMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            content.loadIssues.take(10).forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }

            SectionTitle("O aplikacji")
            OutlinedButton(onClick = onOpenSources, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Źródła i status weryfikacji") }
            Text(DISCLAIMER)
            Text(
                "Lekcje i pytania pochodzą z wcześniej przygotowanych skryptów szkoleniowych i nie zostały niezależnie zweryfikowane. " +
                    "Nie są oficjalną bazą pytań komisji. Tam, gdzie materiał nie rozróżnia jednoznacznie E i D, pokazujemy „E/D – do weryfikacji”. " +
                    "Przed publikacją produkcyjną wymagany jest osobny audyt merytoryczny z aktualnymi aktami prawnymi i normami.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Wersja ${BuildConfig.VERSION_NAME} · działa offline", style = MaterialTheme.typography.bodySmall)
        }
    }

    report?.let { r ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(if (r.error == null) "Zaimportowano ${r.fileName}" else "Import nieudany") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    r.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (r.error == null) {
                        Text("Poprawne pozycje: ${r.imported}")
                        Text("Nowe: ${r.added.size} · zmienione: ${r.updated.size} · bez zmian: ${r.unchanged}")
                        Text("Postępy zachowane dla ${r.progressKept} pozycji z paczki.")
                        r.backupFile?.let { Text("Kopia postępów przed importem: $it", style = MaterialTheme.typography.bodySmall) }
                        if (r.added.isNotEmpty()) Text("Dodane:", fontWeight = FontWeight.SemiBold)
                        r.added.take(40).forEach { Text("• ${it.group.code}: ${displayTitle(it.title)}", style = MaterialTheme.typography.bodySmall) }
                        if (r.updated.isNotEmpty()) Text("Zmienione (wznowienie lekcji od początku):", fontWeight = FontWeight.SemiBold)
                        r.updated.take(40).forEach { Text("• ${it.group.code}: ${displayTitle(it.title)}", style = MaterialTheme.typography.bodySmall) }
                        Text("Status treści: wymaga weryfikacji – import nie potwierdza zgodności z przepisami.", style = MaterialTheme.typography.bodySmall)
                    }
                    r.migratedFrom?.let { Text("Zmigrowano ze schematu v$it do aktualnego.") }
                    if (r.issues.isNotEmpty()) Text("Pominięte/uwagi (${r.issues.size}):", fontWeight = FontWeight.SemiBold)
                    r.issues.take(30).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { report = null }) { Text("OK") } },
        )
    }

    confirmRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Przywrócić kopię postępów?") },
            text = { Text("Obecne postępy zostaną zastąpione kopią „${b.reason}” (${b.itemCount} pozycji). Przed przywróceniem zostanie zapisana kopia bieżącego stanu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        container.progressBackup.create("przed przywróceniem kopii")
                        val n = container.progressBackup.restore(b.file)
                        restoreMessage = "Przywrócono postępy: $n pozycji."
                        backups = container.progressBackup.list()
                    }
                }) { Text("Przywróć") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, subtitle: String? = null, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun fmt(v: Float) = String.format(Locale("pl", "PL"), "%.2f", v)

private fun openIntent(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
