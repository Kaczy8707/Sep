package pl.septrener.content

import android.net.Uri
import pl.septrener.data.ProgressBackup
import pl.septrener.data.ProgressRepository

/**
 * Import paczek z zachowaniem postępów:
 * 1) walidacja, 2) kopia zapasowa postępów, 3) zapis paczki, 4) reset punktu wznowienia tylko dla lekcji o zmienionej treści.
 * Zakładki, ukończenia i oceny są powiązane z identyfikatorem pozycji, więc pozostają bez zmian.
 */
class ContentImporter(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val backup: ProgressBackup,
) {
    suspend fun import(uri: Uri, displayName: String?): ImportReport = finish { content.import(uri, displayName) }

    suspend fun importText(text: String, fileName: String): ImportReport = finish { content.importText(text, fileName) }

    private suspend fun finish(block: suspend () -> ImportReport): ImportReport {
        var backupName: String? = null
        content.beforeApply = { name -> backupName = backup.create("przed importem $name").file.name }
        val report = try { block() } finally { content.beforeApply = null }
        if (report.error != null) return report
        report.updated.filter { it.isLesson }.forEach { progress.resetResumePoint(it.id) }
        val stored = progress.snapshot().map { it.itemId }.toSet()
        return report.copy(backupFile = backupName, progressKept = report.itemIds.count { it in stored })
    }
}
