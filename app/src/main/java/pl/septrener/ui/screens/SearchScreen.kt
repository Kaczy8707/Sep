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
    outerPadding: PaddingValues,
) {
    val content by container.content.state.collectAsStateWithLifecycle()
    val item = content.item(questionId)
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
        }
    }
}
