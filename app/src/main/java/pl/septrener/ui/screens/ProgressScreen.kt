package pl.septrener.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.septrener.AppContainer
import pl.septrener.content.Group
import pl.septrener.data.Srs
import pl.septrener.ui.components.ScreenScaffold
import pl.septrener.ui.components.SectionTitle
import java.text.DateFormat
import java.util.Date

@Composable
fun ProgressScreen(container: AppContainer, onOpenQuestion: (String) -> Unit, outerPadding: PaddingValues) {
    val content by container.content.state.collectAsStateWithLifecycle()
    val progress by container.progress.all.collectAsStateWithLifecycle(emptyMap())
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    ScreenScaffold("Postępy", outerPadding) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Postępy są zapisywane tylko na tym urządzeniu (bez konta, bez internetu).", style = MaterialTheme.typography.bodySmall)
            Group.entries.forEach { g ->
                val lessons = content.lessons(g)
                val questions = content.questions(g)
                val done = lessons.count { progress[it.id]?.completed == true }
                val known = questions.count { progress[it.id]?.let { p -> p.knownCount > 0 && !p.markedHard } == true }
                val hard = questions.count { progress[it.id]?.markedHard == true }
                val due = questions.count { Srs.isDue(progress[it.id], now) }
                val last = (lessons + questions).mapNotNull { progress[it.id]?.lastReviewedAt }.maxOrNull()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${g.code} – ${g.shortName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Lekcje ukończone: $done z ${lessons.size}")
                        LinearProgressIndicator(progress = { done.toFloat() / lessons.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                        Text("Pytania: umiem $known · trudne $hard · do powtórki dziś $due · razem ${questions.size}")
                        LinearProgressIndicator(progress = { known.toFloat() / questions.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                        Text("Ostatnia nauka: ${last?.let { df.format(Date(it)) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val hardItems = content.items.filter { it.isQuestion && progress[it.id]?.markedHard == true }
            SectionTitle("Trudne pytania (${hardItems.size})")
            if (hardItems.isEmpty()) Text("Brak – oznaczaj pytania przyciskiem „Powtórz” podczas egzaminu.")
            hardItems.forEach { q ->
                val p = progress[q.id]
                TextButton(onClick = { onOpenQuestion(q.id) }) {
                    Text("${q.group.code}: ${q.question} (powtórek: ${p?.repetitions ?: 0})")
                }
            }
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 16.dp)) { Text("Wyzeruj wszystkie postępy") }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Wyzerować postępy?") },
            text = { Text("Usunie ukończenia, zakładki, oceny i harmonogram powtórek. Tej operacji nie można cofnąć.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; scope.launch { container.progress.resetAll() } }) { Text("Wyzeruj") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Anuluj") } },
        )
    }
}
