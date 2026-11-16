package pl.septrener.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lista identyfikatorów pytań sytuacyjnych (assets/content/scenarios.json). */
object ScenarioList {
    @Serializable
    private data class File(val note: String? = null, val ids: List<String> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): List<String> = json.decodeFromString(File.serializer(), text).ids.map { it.trim() }.filter { it.isNotEmpty() }
}

/** Opis źródeł treści (assets/content/sources.json) dla ekranu „Źródła i status weryfikacji”. */
@Serializable
data class SourcesInfo(
    val note: String = "",
    val packages: List<SourcePackage> = emptyList(),
    val officialSources: List<OfficialSource> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): SourcesInfo = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class SourcePackage(val name: String, val files: List<String> = emptyList(), val description: String = "", val status: String = "")

@Serializable
data class OfficialSource(val title: String, val url: String)
