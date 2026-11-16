package pl.septrener.content

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ContentState(
    val loaded: Boolean = false,
    val items: List<ContentItem> = emptyList(),
    val importedPacks: List<String> = emptyList(),
    val loadIssues: List<String> = emptyList(),
) {
    private val byId: Map<String, ContentItem> by lazy { items.associateBy { it.id } }
    fun item(id: String): ContentItem? = byId[id]
    fun lessons(group: Group) = items.filter { it.isLesson && it.group == group }
    fun questions(group: Group?) = items.filter { it.isQuestion && (group == null || it.group == group) }
    fun questionsForLesson(lessonId: String) = items.filter { it.isQuestion && it.sourceLessonId == lessonId }
}

data class ImportReport(
    val fileName: String,
    /** Liczba poprawnych pozycji w paczce. */
    val imported: Int,
    /** Pozycje o identyfikatorach już istniejących (zmienione + bez zmian). */
    val replaced: Int,
    val issues: List<ParseIssue>,
    val migratedFrom: Int?,
    val error: String? = null,
    /** Nowe pozycje (tytuły). */
    val added: List<ContentItem> = emptyList(),
    /** Istniejące pozycje ze zmienioną treścią. */
    val updated: List<ContentItem> = emptyList(),
    val unchanged: Int = 0,
    /** Plik kopii zapasowej postępów utworzonej przed importem. */
    val backupFile: String? = null,
    /** Liczba pozycji z zapisanym postępem, których dotyczył import (postęp zachowany). */
    val progressKept: Int = 0,
    /** Identyfikatory wszystkich poprawnych pozycji paczki. */
    val itemIds: List<String> = emptyList(),
)

/** Treść wbudowana (assets/content) + paczki zaimportowane przez użytkownika (files/packs). Działa w pełni offline. */
private fun ContentItem.sameContent(other: ContentItem) =
    copy(origin = "", isScenario = false) == other.copy(origin = "", isScenario = false)

class ContentRepository(private val context: Context, private val builtIn: List<String> = BUILT_IN) {
    private val _state = MutableStateFlow(ContentState())
    val state: StateFlow<ContentState> = _state.asStateFlow()
    private val mutex = Mutex()

    private val packsDir get() = File(context.filesDir, "packs").apply { mkdirs() }

    suspend fun ensureLoaded(): ContentState {
        if (!_state.value.loaded) reload()
        return _state.value
    }

    suspend fun reload() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val issues = mutableListOf<String>()
            val merged = LinkedHashMap<String, ContentItem>()
            for (name in builtIn) {
                runCatching {
                    val text = context.assets.open("content/$name").bufferedReader().use { it.readText() }
                    ContentParser.parse(text, origin = "wbudowane: $name")
                }.onSuccess { r ->
                    r.items.forEach { merged[it.id] = it }
                    r.issues.forEach { issues += "$name – $it" }
                }.onFailure { issues += "$name – ${it.message}"; Log.w(TAG, "asset $name", it) }
            }
            val packs = packsDir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            for (file in packs) {
                runCatching { ContentParser.parse(file.readText(), origin = "import: ${file.name}") }
                    .onSuccess { r -> r.items.forEach { merged[it.id] = it } }
                    .onFailure { issues += "${file.name} – ${it.message}" }
            }
            // redakcyjna lista scenariuszy awaryjnych
            runCatching {
                val text = context.assets.open("content/$SCENARIOS").bufferedReader().use { it.readText() }
                ScenarioList.parse(text)
            }.onSuccess { ids ->
                ids.forEach { id ->
                    val item = merged[id]
                    if (item?.isQuestion == true) merged[id] = item.copy(isScenario = true)
                    else if (builtIn == BUILT_IN) issues += "$SCENARIOS – „$id” nie jest pytaniem w bazie"
                }
            }.onFailure { if (it !is java.io.FileNotFoundException) issues += "$SCENARIOS – ${it.message}" }
            // odwołania pytań do lekcji
            merged.values.filter { it.sourceLessonId != null && merged[it.sourceLessonId]?.isLesson != true }
                .forEach { issues += "${it.id} – sourceLessonId „${it.sourceLessonId}” nie wskazuje istniejącej lekcji" }
            _state.value = ContentState(true, merged.values.toList(), packs.map { it.name }, issues)
        }
    }

    /** Import paczki przez Storage Access Framework. Plik jest walidowany przed zapisaniem. */
    suspend fun import(uri: Uri, displayName: String?): ImportReport = withContext(Dispatchers.IO) {
        val name = sanitize(displayName ?: uri.lastPathSegment ?: "paczka.json")
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_IMPORT_BYTES) return@withContext ImportReport(name, 0, 0, emptyList(), null, "Plik jest zbyt duży (limit 5 MB)")
                bytes.toString(Charsets.UTF_8)
            } ?: return@withContext ImportReport(name, 0, 0, emptyList(), null, "Nie można otworzyć pliku")
        } catch (e: Exception) {
            return@withContext ImportReport(name, 0, 0, emptyList(), null, "Błąd odczytu: ${e.message}")
        }
        importText(text, name)
    }

    suspend fun importText(text: String, fileName: String): ImportReport {
        val name = sanitize(fileName)
        val result = try {
            ContentParser.parse(text, origin = "import: $name")
        } catch (e: ContentFormatException) {
            return ImportReport(name, 0, 0, emptyList(), null, e.message)
        }
        if (!result.isUsable) return ImportReport(name, 0, 0, result.issues, result.migratedFrom, "Brak poprawnych pozycji – nic nie zaimportowano")
        val current = ensureLoaded()
        val added = result.items.filter { current.item(it.id) == null }
        val updated = result.items.filter { new -> current.item(new.id)?.let { !it.sameContent(new) } == true }
        val replaced = result.items.size - added.size
        beforeApply?.invoke(name)
        withContext(Dispatchers.IO) {
            val target = File(packsDir, name)
            if (target.exists()) target.copyTo(File(packsDir.parentFile, "backups/packs/$name.${System.currentTimeMillis()}.bak").apply { parentFile?.mkdirs() }, overwrite = true)
            target.writeText(text)
        }
        reload()
        return ImportReport(
            name, result.items.size, replaced, result.issues, result.migratedFrom,
            added = added, updated = updated, unchanged = replaced - updated.size,
            itemIds = result.items.map { it.id },
        )
    }

    /** Wywoływane tuż przed zapisaniem paczki (np. utworzenie kopii zapasowej postępów). */
    var beforeApply: (suspend (fileName: String) -> Unit)? = null

    suspend fun deletePack(fileName: String) {
        withContext(Dispatchers.IO) { File(packsDir, sanitize(fileName)).delete() }
        reload()
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        return if (base.endsWith(".json")) base else "$base.json"
    }

    companion object {
        private const val TAG = "ContentRepository"
        private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        const val SCENARIOS = "scenarios.json"
        const val SOURCES = "sources.json"
        /** Kolejność ma znaczenie: kolejne paczki dopisują rekordy (nowe id) za wcześniejszymi. */
        val BUILT_IN = listOf(
            "G1_lessons.json", "G1_lessons_2026.json", "G1_exam.json", "G1_exam_2026.json",
            "G2_lessons.json", "G2_lessons_2026.json", "G2_exam.json", "G2_exam_2026.json",
            "G3_lessons.json", "G3_lessons_2026.json", "G3_exam.json", "G3_exam_2026.json",
        )
    }
}
