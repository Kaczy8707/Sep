package pl.septrener.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class ProgressBackupFile(
    val formatVersion: Int = 1,
    val createdAt: Long,
    val reason: String,
    val items: List<ItemProgress>,
    /** Historia odpowiedzi (od formatu 2; kopie z formatu 1 jej nie mają). */
    val history: List<AnswerLogEntry>? = null,
)

data class BackupInfo(val file: File, val createdAt: Long, val reason: String, val itemCount: Int)

/** Kopie zapasowe postępów (zakładki, ukończenia, oceny, harmonogram) w files/backups, przechowywane lokalnie. */
class ProgressBackup(private val progress: ProgressRepository, private val dir: File, private val keep: Int = 10) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun create(reason: String, now: Long = System.currentTimeMillis()): BackupInfo {
        val items = progress.snapshot()
        val history = progress.historySnapshot()
        return withContext(Dispatchers.IO) {
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date(now))
            val file = File(dir, "postepy-$stamp.json")
            file.writeText(json.encodeToString(ProgressBackupFile.serializer(), ProgressBackupFile(formatVersion = 2, createdAt = now, reason = reason, items = items, history = history)))
            list().drop(keep).forEach { it.file.delete() }
            BackupInfo(file, now, reason, items.size)
        }
    }

    suspend fun list(): List<BackupInfo> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.startsWith("postepy-") && f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { read(f) }.getOrNull()?.let { BackupInfo(f, it.createdAt, it.reason, it.items.size) } }
            .sortedByDescending { it.createdAt }
    }

    suspend fun restore(file: File): Int {
        val data = withContext(Dispatchers.IO) { read(file) }
        progress.replaceAll(data.items, data.history)
        return data.items.size
    }

    private fun read(file: File) = json.decodeFromString(ProgressBackupFile.serializer(), file.readText())
}
