package pl.septrener.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.septrener.AppContainer
import pl.septrener.playback.MediaIds
import pl.septrener.ui.RESUME_NOTE
import pl.septrener.ui.components.DisclaimerCard
import pl.septrener.ui.components.ItemBadges
import pl.septrener.ui.components.ScreenScaffold
import pl.septrener.ui.components.SegmentText
import pl.septrener.ui.components.TtsStatusBanner
import pl.septrener.ui.displayTitle

@Composable
fun LessonScreen(
    container: AppContainer,
    lessonId: String,
    gate: (() -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenSettings: () -> Unit,
    outerPadding: PaddingValues,
    onOpenQuestion: (String) -> Unit = {},
) {
    val content by container.content.state.collectAsStateWithLifecycle()
    val progressMap by container.progress.all.collectAsStateWithLifecycle(emptyMap())
    val playback by container.playback.collectAsStateWithLifecycle()
    val ttsStatus by container.tts.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val lesson = content.item(lessonId)
    if (lesson == null) {
        ScreenScaffold("Lekcja", outerPadding, onBack) { Text(if (content.loaded) "Nie znaleziono lekcji." else "Wczytywanie…", Modifier.padding(it).padding(16.dp)) }
        return
    }
    val progress = progressMap[lesson.id]
    val segments = remember(lesson) { container.trackFactory.lessonSegments(lesson) }
    val groupLessons = content.lessons(lesson.group)
    val indexInGroup = groupLessons.indexOfFirst { it.id == lesson.id }
    val mediaId = MediaIds.lesson(lesson.id)
    val isThis = playback.isCurrent(mediaId)
    val savedSegment = if (progress?.completed == true) 0 else (progress?.lastSegment ?: 0)
    val currentSegment = (if (isThis) playback.segmentIndex else savedSegment).coerceIn(0, (segments.size - 1).coerceAtLeast(0))
    val playingThis = isThis && playback.playing

    fun startAt(segment: Int) = gate {
        container.connection.playItems(groupLessons.map { MediaIds.lesson(it.id) }, indexInGroup.coerceAtLeast(0), segment)
    }
    fun playPause() {
        when {
            playingThis -> container.connection.pause()
            isThis && !playback.ended -> gate { container.connection.play() }
            else -> startAt(savedSegment)
        }
    }
    fun seekSegment(target: Int) {
        val t = target.coerceIn(0, segments.size - 1)
        if (isThis) container.connection.seekSegment(t) else startAt(t)
    }

    val listState = rememberLazyListState()
    val headerItems = 2
    LaunchedEffect(isThis, currentSegment) {
        if (isThis) listState.animateScrollToItem(headerItems + currentSegment)
    }

    ScreenScaffold(
        title = displayTitle(lesson.title),
        outerPadding = outerPadding,
        onBack = onBack,
        actions = {
            IconButton(onClick = { scope.launch { container.progress.toggleBookmark(lesson) } }) {
                Icon(
                    if (progress?.bookmarked == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (progress?.bookmarked == true) "Usuń zakładkę" else "Dodaj zakładkę",
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    val statusText = when {
                        isThis && playback.waitingForEngine -> "Czekam na syntezator mowy…"
                        playingThis -> "Czytam fragment ${currentSegment + 1} z ${segments.size}"
                        isThis && playback.ended -> "Lekcja odsłuchana"
                        isThis || savedSegment > 0 -> "Wstrzymano – fragment ${currentSegment + 1} z ${segments.size}"
                        else -> "${segments.size} fragmentów"
                    }
                    Text(
                        statusText, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    playback.error?.takeIf { isThis }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(onClick = { seekSegment(currentSegment - 1) }, enabled = currentSegment > 0, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.FastRewind, contentDescription = "Poprzedni fragment")
                        }
                        Button(onClick = ::playPause, modifier = Modifier.heightIn(min = 64.dp).width(190.dp)) {
                            Icon(if (playingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    playingThis -> "Pauza"
                                    (isThis && !playback.ended) || savedSegment > 0 -> "Wznów"
                                    else -> "Odtwórz"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        FilledTonalIconButton(onClick = { seekSegment(currentSegment + 1) }, enabled = currentSegment < segments.size - 1, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.FastForward, contentDescription = "Następny fragment")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { groupLessons.getOrNull(indexInGroup - 1)?.let { onOpenLesson(it.id) } },
                            enabled = indexInGroup > 0,
                        ) { Icon(Icons.Filled.SkipPrevious, null); Text("Poprzednia") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = progress?.completed == true,
                                onCheckedChange = { checked -> scope.launch { container.progress.setCompleted(lesson, checked) } },
                            )
                            Text("Ukończona")
                        }
                        TextButton(
                            onClick = { groupLessons.getOrNull(indexInGroup + 1)?.let { onOpenLesson(it.id) } },
                            enabled = indexInGroup in 0 until groupLessons.size - 1,
                        ) { Text("Następna"); Icon(Icons.Filled.SkipNext, null) }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = inner.calculateBottomPadding() + 16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemBadges(lesson)
                    TtsStatusBanner(ttsStatus, onOpenSettings)
                }
            }
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(RESUME_NOTE, style = MaterialTheme.typography.bodySmall)
                    Text("Dotknij fragmentu, aby czytać od niego.", style = MaterialTheme.typography.bodySmall)
                }
            }
            itemsIndexed(segments) { i, seg ->
                SegmentText(
                    text = seg.text,
                    highlighted = isThis && i == currentSegment,
                    label = "Fragment ${i + 1} z ${segments.size}" + if (isThis && i == currentSegment) (if (playback.playing) " · czytam" else " · tu wznowię") else "",
                    onClick = { if (isThis) gate { container.connection.seekSegment(i, thenPlay = true) } else startAt(i) },
                )
            }
            val related = content.questionsForLesson(lesson.id)
            if (related.isNotEmpty()) item {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Pytania do tej lekcji (${related.size})", style = MaterialTheme.typography.titleMedium)
                    related.forEach { q ->
                        TextButton(onClick = { onOpenQuestion(q.id) }) { Text("${q.scope.let { if (it == pl.septrener.content.Scope.UNKNOWN) "" else "[${it.name.replace("ED", "E/D")}] " }}${q.question}") }
                    }
                }
            }
            item { DisclaimerCard(Modifier.padding(top = 12.dp), extra = "W treści zachowano ostrzeżenia BHP. Nie wykonuj prac niebezpiecznych bez wymaganych uprawnień, poleceń i środków ochrony.") }
        }
    }
}
