package pl.septrener.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.septrener.AppContainer
import pl.septrener.content.Group
import pl.septrener.playback.MediaIds
import pl.septrener.ui.components.DisclaimerCard
import pl.septrener.ui.components.ScreenScaffold
import pl.septrener.ui.components.TtsStatusBanner
import pl.septrener.ui.displayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    gate: (() -> Unit) -> Unit,
    onOpenLesson: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    val content by container.content.state.collectAsStateWithLifecycle()
    val progress by container.progress.all.collectAsStateWithLifecycle(emptyMap())
    val ttsStatus by container.tts.status.collectAsStateWithLifecycle()
    var group by rememberSaveable { mutableStateOf(Group.G1) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val lessons = content.lessons(group)
    val visible = when (filter) {
        1 -> lessons.filter { progress[it.id]?.bookmarked == true }
        2 -> lessons.filter { progress[it.id]?.completed != true }
        else -> lessons
    }

    ScreenScaffold(
        title = "SEP Trener",
        outerPadding = contentPadding,
        actions = { IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Szukaj w lekcjach i pytaniach") } },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp, bottom = 24.dp)) {
            item { DisclaimerCard() }
            item { TtsStatusBanner(ttsStatus, onOpenSettings, Modifier.padding(top = 8.dp)) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Group.entries.forEachIndexed { i, g ->
                        SegmentedButton(
                            selected = g == group, onClick = { group = g },
                            shape = SegmentedButtonDefaults.itemShape(i, Group.entries.size),
                            modifier = Modifier.heightIn(min = 52.dp),
                        ) { Text(g.code) }
                    }
                }
                Text(
                    "${group.code} – urządzenia ${group.shortName}", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    listOf("Wszystkie", "Zakładki", "Nieukończone").forEachIndexed { i, label ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val ids = lessons.map { MediaIds.lesson(it.id) }
                        val start = lessons.indexOfFirst { progress[it.id]?.completed != true }.coerceAtLeast(0)
                        val seg = lessons.getOrNull(start)?.let { progress[it.id]?.lastSegment } ?: 0
                        if (ids.isNotEmpty()) gate { container.connection.playItems(ids, start, seg) }
                    },
                    enabled = lessons.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.Headphones, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Słuchaj lekcji ${group.code} (od pierwszej nieukończonej)")
                }
            }
            if (!content.loaded) item { Text("Wczytywanie…", Modifier.padding(16.dp)) }
            if (content.loaded && visible.isEmpty()) item { Text("Brak lekcji dla wybranego filtra.", Modifier.padding(16.dp)) }
            items(visible, key = { it.id }) { lesson ->
                val p = progress[lesson.id]
                val status = when {
                    p?.completed == true -> "Ukończona"
                    (p?.lastSegment ?: 0) > 0 -> "Wznowienie od fragmentu ${p!!.lastSegment + 1}"
                    else -> "Nierozpoczęta"
                } + " · wymaga weryfikacji"
                ListItem(
                    modifier = Modifier.clickable(onClickLabel = "Otwórz lekcję") { onOpenLesson(lesson.id) },
                    leadingContent = {
                        if (p?.completed == true) Icon(Icons.Filled.CheckCircle, contentDescription = "Ukończona", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    },
                    headlineContent = { Text(displayTitle(lesson.title)) },
                    supportingContent = { Text(status) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { container.progress.toggleBookmark(lesson) } }) {
                            Icon(
                                if (p?.bookmarked == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (p?.bookmarked == true) "Usuń zakładkę" else "Dodaj zakładkę",
                            )
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
