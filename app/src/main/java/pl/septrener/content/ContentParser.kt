package pl.septrener.content

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ParseIssue(val itemIndex: Int?, val itemId: String?, val message: String) {
    override fun toString(): String {
        val where = when {
            itemId != null -> "„$itemId”"
            itemIndex != null -> "pozycja #${itemIndex + 1}"
            else -> "plik"
        }
        return "$where: $message"
    }
}

data class ParseResult(
    val items: List<ContentItem>,
    val issues: List<ParseIssue>,
    val schemaVersion: Int,
    val migratedFrom: Int?,
) {
    val isUsable get() = items.isNotEmpty()
}

class ContentFormatException(message: String) : Exception(message)

/**
 * Parser paczek treści.
 *
 * Obsługiwane formaty:
 * - v0 (legacy): sama tablica JSON z pozycjami (np. `G1_lessons.json`) – migrowana do v1,
 * - v1: obiekt `{ schemaVersion: 1, language, source, items: [...] }` (np. `content_manifest.json`).
 *
 * Nie generuje brakujących treści: pozycje bez wymaganych pól są odrzucane z opisem błędu.
 */
object ContentParser {
    const val CURRENT_SCHEMA = 1
    const val MIN_PAUSE_SECONDS = 1
    const val MAX_PAUSE_SECONDS = 120
    const val MAX_FIELD_LENGTH = 200_000

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /** Migracje schematu: wersja źródłowa -> funkcja przekształcająca do wersji +1. */
    private val migrations: Map<Int, (JsonElement) -> JsonObject> = mapOf(
        0 to { element ->
            buildJsonObject {
                put("schemaVersion", 1)
                put("items", element as JsonArray)
            }
        },
    )

    fun parse(text: String, origin: String): ParseResult {
        val root = try {
            json.parseToJsonElement(text.removePrefix("\uFEFF"))
        } catch (e: SerializationException) {
            throw ContentFormatException("Niepoprawny JSON: ${e.message?.take(200)}")
        } catch (e: IllegalArgumentException) {
            throw ContentFormatException("Niepoprawny JSON: ${e.message?.take(200)}")
        }

        var version = detectVersion(root)
        val originalVersion = version
        if (version > CURRENT_SCHEMA) {
            throw ContentFormatException(
                "Paczka ma schemat w wersji $version, a aplikacja obsługuje najwyżej $CURRENT_SCHEMA. Zaktualizuj aplikację."
            )
        }
        var element: JsonElement = root
        while (version < CURRENT_SCHEMA) {
            val migration = migrations[version]
                ?: throw ContentFormatException("Brak migracji ze schematu $version")
            element = migration(element)
            version++
        }

        val pack = try {
            json.decodeFromJsonElement<RawPack>(element)
        } catch (e: SerializationException) {
            throw ContentFormatException("Niezgodny schemat paczki: ${e.message?.take(200)}")
        } catch (e: IllegalArgumentException) {
            throw ContentFormatException("Niezgodny schemat paczki: ${e.message?.take(200)}")
        }

        val issues = mutableListOf<ParseIssue>()
        val seen = HashSet<String>()
        val items = pack.items.mapIndexedNotNull { index, raw ->
            val result = validate(raw, index, origin, issues)
            if (result != null && !seen.add(result.id)) {
                issues += ParseIssue(index, result.id, "zduplikowany identyfikator – pominięto")
                null
            } else result
        }
        if (pack.items.isEmpty()) issues += ParseIssue(null, null, "paczka nie zawiera pozycji")
        return ParseResult(items, issues, CURRENT_SCHEMA, originalVersion.takeIf { it != CURRENT_SCHEMA })
    }

    private fun detectVersion(root: JsonElement): Int = when (root) {
        is JsonArray -> 0
        is JsonObject -> {
            val v = root["schemaVersion"]
            if (v == null) {
                if (root.containsKey("items")) 1 else throw ContentFormatException("Brak pola „items” w paczce")
            } else {
                v.jsonPrimitive.intOrNull ?: throw ContentFormatException("Pole schemaVersion musi być liczbą")
            }
        }
        else -> throw ContentFormatException("Oczekiwano obiektu lub tablicy JSON")
    }

    private fun validate(raw: RawItem, index: Int, origin: String, issues: MutableList<ParseIssue>): ContentItem? {
        fun fail(msg: String): ContentItem? {
            issues += ParseIssue(index, raw.id?.takeIf { it.isNotBlank() }, msg); return null
        }
        val id = raw.id?.trim().orEmpty()
        if (id.isEmpty()) return fail("brak identyfikatora „id”")
        if (!id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) return fail("niedozwolone znaki w identyfikatorze")
        val group = raw.group?.let { Group.fromCode(it) } ?: return fail("nieznana grupa „${raw.group}” (dozwolone G1, G2, G3)")
        val type = raw.type?.let { ItemType.fromCode(it) } ?: return fail("nieznany typ „${raw.type}” (dozwolone lesson, oral_question)")
        val title = raw.title?.trim().orEmpty()
        val fields = listOfNotNull(raw.title, raw.content, raw.question, raw.answer)
        if (fields.any { it.length > MAX_FIELD_LENGTH }) return fail("pole jest zbyt długie")
        val pause = raw.pauseSeconds
        if (pause != null && pause !in MIN_PAUSE_SECONDS..MAX_PAUSE_SECONDS) {
            return fail("pauseSeconds poza zakresem $MIN_PAUSE_SECONDS–$MAX_PAUSE_SECONDS")
        }
        return when (type) {
            ItemType.LESSON -> {
                val content = raw.content?.trim().orEmpty()
                if (content.isEmpty()) return fail("lekcja bez treści „content”")
                ContentItem(
                    id = id, group = group, type = type, title = title.ifEmpty { content.lineSequence().first().take(80) },
                    content = content, verified = raw.verified ?: false, scope = Scope.fromCode(raw.scope), origin = origin,
                    source = raw.source?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            ItemType.ORAL_QUESTION -> {
                val question = raw.question?.trim().orEmpty()
                val answer = raw.answer?.trim().orEmpty()
                if (question.isEmpty()) return fail("pytanie bez treści „question”")
                if (answer.isEmpty()) return fail("pytanie bez odpowiedzi „answer” – nie uzupełniamy odpowiedzi automatycznie")
                ContentItem(
                    id = id, group = group, type = type, title = title.ifEmpty { question },
                    question = question, answer = answer, pauseSeconds = pause,
                    verified = raw.verified ?: false, scope = Scope.fromCode(raw.scope), origin = origin,
                    source = raw.source?.trim()?.takeIf { it.isNotEmpty() },
                    sourceLessonId = raw.sourceLessonId?.trim()?.takeIf { it.isNotEmpty() },
                    isScenario = raw.scenario == true,
                )
            }
        }
    }
}
