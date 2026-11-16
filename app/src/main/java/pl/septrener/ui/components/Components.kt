package pl.septrener.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import pl.septrener.content.ContentItem
import pl.septrener.playback.PlaybackUiState
import pl.septrener.settings.SettingsStore
import pl.septrener.tts.TtsStatus
import pl.septrener.ui.DISCLAIMER
import pl.septrener.ui.displayTitle

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
fun DisclaimerCard(modifier: Modifier = Modifier, extra: String? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(DISCLAIMER, style = MaterialTheme.typography.bodyMedium)
                if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun Badge(text: String, warning: Boolean = false) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemBadges(item: ContentItem) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Badge(item.group.code)
        Badge(if (item.verified) "Oznaczone w paczce jako zweryfikowane" else "Wymaga weryfikacji", warning = !item.verified)
        Badge(
            if (item.scope == pl.septrener.content.Scope.UNKNOWN) "Zakres: ${item.scope.label}" else "Zakres (propozycja redakcyjna): ${item.scope.label}",
            warning = item.scope == pl.septrener.content.Scope.UNKNOWN,
        )
        if (item.isScenario) Badge("Scenariusz sytuacyjny")
        Badge("Treść pomocnicza")
    }
}

/** Komunikat o stanie syntezatora z instrukcją instalacji polskiego głosu. */
@Composable
fun TtsStatusBanner(status: TtsStatus, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (status) {
        is TtsStatus.Ready -> return
        TtsStatus.Initializing -> "Uruchamiam syntezator mowy…"
        is TtsStatus.PolishMissing -> if (status.missingData)
            "Brak pobranego polskiego głosu w silniku „${status.engineLabel}”. Pobierz go w ustawieniach syntezatora."
        else "Silnik „${status.engineLabel}” nie obsługuje języka polskiego. Zainstaluj np. Usługi rozpoznawania mowy Google i ustaw je jako domyślny silnik."
        TtsStatus.NoEngine -> "Brak syntezatora mowy w systemie. Zainstaluj silnik TTS z obsługą języka polskiego."
        is TtsStatus.Error -> status.message
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status == TtsStatus.Initializing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (status != TtsStatus.Initializing) TextButton(onClick = onOpenSettings) { Text("Napraw") }
        }
    }
}

@Composable
fun MiniPlayer(state: PlaybackUiState, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val track = state.track ?: return
    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable(onClickLabel = "Otwórz odtwarzacz", onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(displayTitle(track.title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${track.group.code} · fragment ${state.segmentIndex + 1}/${track.segments.size}" +
                        when { state.waitingForEngine -> " · czekam na syntezator"; state.ended -> " · koniec"; else -> "" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(52.dp)) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Pauza" else "Wznów",
                )
            }
            if (state.trackIndex < state.tracks.size - 1) {
                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.SkipNext, contentDescription = "Następna") }
            }
        }
    }
}

/** Tekst fragmentu: pierwsza krótka linia (nagłówek sekcji) pogrubiona. */
@Composable
fun SegmentText(text: String, highlighted: Boolean, label: String, onClick: (() -> Unit)?) {
    val lines = text.split('\n')
    val hasHeader = lines.size > 1 && lines[0].length < 100
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Czytaj od tego fragmentu", onClick = onClick) else Modifier)
            .semantics { contentDescription = "$label. $text" },
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasHeader) Text(lines[0], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (hasHeader) lines.drop(1).joinToString("\n") else text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Android 13+: prośba o POST_NOTIFICATIONS przed pierwszym odtwarzaniem (powiadomienie sterujące na ekranie blokady).
 * Odtwarzanie działa także bez zgody – wtedy powiadomienie jest ukryte.
 */
@Composable
fun rememberNotificationGate(settingsStore: SettingsStore, alreadyAsked: Boolean): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending[0]?.invoke(); pending[0] = null
    }
    return { action ->
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs && !alreadyAsked) {
            pending[0] = action
            scope.launch { settingsStore.setNotificationsAsked() }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
}
