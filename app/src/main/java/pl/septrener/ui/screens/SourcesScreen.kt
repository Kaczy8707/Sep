package pl.septrener.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.septrener.AppContainer
import pl.septrener.content.ContentRepository
import pl.septrener.content.Group
import pl.septrener.content.Scope
import pl.septrener.content.SourcesInfo
import pl.septrener.ui.DISCLAIMER
import pl.septrener.ui.NOT_OFFICIAL
import pl.septrener.ui.components.DisclaimerCard
import pl.septrener.ui.components.ScreenScaffold
import pl.septrener.ui.components.SectionTitle

/** Ekran „Źródła i status weryfikacji”: skąd pochodzą treści i jaki mają status. */
@Composable
fun SourcesScreen(container: AppContainer, onBack: () -> Unit, outerPadding: PaddingValues) {
    val ctx = LocalContext.current
    val content by container.content.state.collectAsStateWithLifecycle()
    val sources by produceState<SourcesInfo?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { SourcesInfo.parse(ctx.assets.open("content/${ContentRepository.SOURCES}").bufferedReader().use { it.readText() }) }.getOrNull()
        }
    }
    ScreenScaffold("Źródła i status weryfikacji", outerPadding, onBack) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DisclaimerCard(extra = NOT_OFFICIAL)
            val verified = content.items.count { it.verified }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Status weryfikacji", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Zweryfikowane: $verified z ${content.items.size} pozycji.")
                    Text(
                        "Wszystkie treści to materiał pomocniczy wymagający audytu eksperckiego z aktualnymi przepisami i normami. " +
                            "Status zmienia się wyłącznie w paczce po audycie (pole verified) – aplikacja nie potwierdza zgodności sama.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionTitle("Zawartość według grup")
            Group.entries.forEach { g ->
                val lessons = content.lessons(g)
                val qs = content.questions(g)
                fun count(s: Scope) = qs.count { it.scope == s }
                Text(
                    "${g.code}: ${lessons.size} lekcji, ${qs.size} pytań · zakres pytań (propozycja redakcyjna): " +
                        "E ${count(Scope.E)}, D ${count(Scope.D)}, E i D ${count(Scope.ED)}, nieoznaczone ${count(Scope.UNKNOWN)} · " +
                        "scenariusze ${qs.count { it.isScenario }}",
                )
            }

            SectionTitle("Pliki treści")
            content.items.groupBy { it.origin }.forEach { (origin, items) ->
                Text("• $origin – ${items.count { it.isLesson }} lekcji, ${items.count { it.isQuestion }} pytań, zweryfikowane: ${items.count { it.verified }}", style = MaterialTheme.typography.bodyMedium)
            }

            sources?.let { info ->
                SectionTitle("Pakiety")
                info.packages.forEach { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(p.description, style = MaterialTheme.typography.bodyMedium)
                            Text("Pliki: " + p.files.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            Text("Status: ${p.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                SectionTitle("Akty prawne do samodzielnej kontroli")
                Text("Aplikacja działa offline – linki otwierają się w przeglądarce telefonu.", style = MaterialTheme.typography.bodySmall)
                info.officialSources.forEach { src ->
                    Column {
                        Text(src.title, style = MaterialTheme.typography.bodyMedium)
                        SelectionContainer { Text(src.url, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text("Otwórz w przeglądarce") }
                    }
                }
            }
            if (content.importedPacks.isNotEmpty()) {
                SectionTitle("Paczki zaimportowane przez użytkownika")
                content.importedPacks.forEach { Text("• $it") }
            }
            Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
