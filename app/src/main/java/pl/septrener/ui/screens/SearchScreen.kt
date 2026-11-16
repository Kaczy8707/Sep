package pl.septrener.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.septrener.AppContainer
import pl.septrener.content.SearchIndex
import pl.septrener.playback.MediaIds
import pl.septrener.ui.NOT_OFFICIAL
import pl.septrener.ui.components.ItemBadges
import pl.septrener.ui.components.ScreenScaffold
import pl.septrener.ui.displayTitle

@Composable
fun SearchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenQuestion: (String) -> Unit,
    outerPadding: PaddingValues,
) {
    val content by container.content.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, content) { SearchIndex.search(content.items, query) }
    ScreenScaffold("Szukaj", outerPadding, onBack) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Szukaj w lekcjach i pytaniach") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (query.length < 2) "Wpisz co najmniej 2 znaki. Wielkość liter i polskie znaki są ignorowane." else "Wyniki: ${hits.size}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(hits, key = { it.item.id }) { hit ->
                ListItem(
                    modifier = Modifier.clickable { if (hit.item.isLesson) onOpenLesson(hit.item.id) else onOpenQuestion(hit.item.id) },
                    overlineContent = { Text("${hit.item.group.code} · ${if (hit.item.isLesson) "lekcja" else "pytanie ustne"}") },
                    headlineContent = { Text(displayTitle(hit.item.title)) },
                    supportingContent = { Text(hit.snippet) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun QuestionScreen(
    container: AppContainer,
    questionId: String,
    gate: (() -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    outerPadding: PaddingValues,
) {
    val content by container.content.state.collectAsStateWithLifecycle()
    val progressMap by container.progress.all.collectAsStateWithLifecycle(emptyMap())
    val item = content.item(questionId)
    var history by remember { mutableStateOf(emptyList<pl.septrener.data.AnswerLogEntry>()) }
    androidx.compose.runtime.LaunchedEffect(questionId, progressMap[questionId]) {
        history = container.progress.history(questionId)
    }
    ScreenScaffold("Pytanie ustne", outerPadding, onBack) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (item == null) { Text("Nie znaleziono pytania."); return@Column }
            ItemBadges(item)
            Text(NOT_OFFICIAL, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            Card(Modifier.fillMaxWidth()) { Text(item.question, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp)) }
            Text("Odpowiedź", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            Text(item.answer, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { gate { container.connection.playItems(listOf(MediaIds.exam(item.id))) } }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Odsłuchaj (pytanie → pauza → odpowiedź)")
            }
            item.sourceLessonId?.let { content.item(it) }?.let { lesson ->
                androidx.compose.material3.TextButton(onClick = { onOpenLesson(lesson.id) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Powiązana lekcja: ${displayTitle(lesson.title)}")
                }
            }
            val p = progressMap[item.id]
            Text("Historia odpowiedzi", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            if (p == null) Text("Jeszcze nie odpowiadano.", style = MaterialTheme.typography.bodySmall)
            else Text(
                "Umiem ${p.knownCount} · częściowo ${p.partialCount} · do powtórki ${p.hardCount} · następna powtórka: " +
                    (p.nextDueAt?.let { java.text.DateFormat.getDateInstance().format(java.util.Date(it)) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
            )
            val df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            history.forEach { h ->
                Text("• ${df.format(java.util.Date(h.answeredAt))} – ${pl.septrener.data.Grade.fromCode(h.grade).label} (${h.mode})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
