package pl.septrener.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.septrener.AppContainer
import pl.septrener.playback.SegmentKind
import pl.septrener.playback.TrackKind
import pl.septrener.ui.RESUME_NOTE
import pl.septrener.ui.components.SegmentText
import pl.septrener.ui.displayTitle

@Composable
fun NowPlayingScreen(
    container: AppContainer,
    gate: (() -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    outerPadding: PaddingValues,
) {
    val state by container.playback.collectAsStateWithLifecycle()
    val track = state.track
    val c = container.connection
    pl.septrener.ui.components.ScreenScaffold(
        title = track?.let { displayTitle(it.title) } ?: "Odtwarzacz",
        outerPadding = outerPadding,
        onBack = onBack,
        bottomBar = {
            if (track != null) Surface(tonalElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = c::previous, enabled = state.trackIndex > 0, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Poprzednia pozycja")
                    }
                    FilledTonalIconButton(onClick = { c.seekSegment(state.segmentIndex - 1) }, enabled = state.segmentIndex > 0, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.FastRewind, contentDescription = "Poprzedni fragment")
                    }
                    FilledIconButton(
                        onClick = { if (state.playing) c.pause() else gate { c.play() } },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.playing) "Pauza" else "Wznów", modifier = Modifier.size(40.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { c.seekSegment(state.segmentIndex + 1) },
                        enabled = state.segmentIndex < track.segments.size - 1, modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Filled.FastForward, contentDescription = "Następny fragment") }
                    IconButton(onClick = c::next, enabled = state.trackIndex < state.tracks.size - 1, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Następna pozycja")
                    }
                }
            }
        },
    ) { inner ->
        if (track == null) {
            Text("Nic nie jest odtwarzane.", Modifier.padding(inner).padding(16.dp)); return@ScreenScaffold
        }
        val listState = rememberLazyListState()
        LaunchedEffect(state.trackIndex, state.segmentIndex) { listState.animateScrollToItem(1 + state.segmentIndex) }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + 16.dp)) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Pozycja ${state.trackIndex + 1} z ${state.tracks.size} · ${track.group.code}", style = MaterialTheme.typography.bodyMedium)
                    Text(RESUME_NOTE, style = MaterialTheme.typography.bodySmall)
                    if (track.kind == TrackKind.LESSON) TextButton(onClick = { onOpenLesson(track.itemId) }) { Text("Otwórz lekcję") }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            itemsIndexed(track.segments) { i, seg ->
                val label = when (seg.kind) {
                    SegmentKind.QUESTION -> "Pytanie"
                    SegmentKind.ANSWER -> "Odpowiedź"
                    SegmentKind.PAUSE -> "Pauza na odpowiedź"
                    SegmentKind.GAP -> "Przerwa"
                    SegmentKind.LESSON_TEXT -> "Fragment ${i + 1} z ${track.segments.size}"
                }
                SegmentText(
                    text = if (seg.isSilence) "${seg.durationMs / 1000} s ciszy" else seg.text,
                    highlighted = i == state.segmentIndex,
                    label = label,
                    onClick = { gate { c.seekSegment(i, thenPlay = true) } },
                )
            }
        }
    }
}
