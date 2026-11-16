package pl.septrener.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.septrener.content.ContentItem

class ProgressRepository(private val dao: ProgressDao, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()

    val all: Flow<Map<String, ItemProgress>> = dao.observeAll().map { list -> list.associateBy { it.itemId } }

    private suspend fun update(item: ContentItem, change: (ItemProgress) -> ItemProgress) = mutex.withLock {
        val current = dao.get(item.id) ?: ItemProgress(item.id, item.group.code, item.type.code)
        dao.upsert(change(current))
    }

    suspend fun get(id: String) = dao.get(id)
    suspend fun toggleBookmark(item: ContentItem) = update(item) { it.copy(bookmarked = !it.bookmarked) }
    suspend fun setCompleted(item: ContentItem, completed: Boolean) =
        update(item) { it.copy(completed = completed, lastSegment = if (completed) 0 else it.lastSegment) }
    suspend fun setLastSegment(item: ContentItem, segment: Int) = update(item) { it.copy(lastSegment = segment) }
    suspend fun lessonFinished(item: ContentItem) =
        update(item) { it.copy(completed = true, lastSegment = 0, repetitions = it.repetitions + 1, lastReviewedAt = clock()) }
    suspend fun recordAnswer(item: ContentItem, known: Boolean) = recordAnswer(item, if (known) Grade.KNOWN else Grade.HARD, "oral")

    /** Zapisuje ocenę, aktualizuje harmonogram powtórek i dopisuje wpis do historii. */
    suspend fun recordAnswer(item: ContentItem, grade: Grade, mode: String) {
        val now = clock()
        update(item) { Srs.grade(it, grade, now) }
        dao.insertLog(AnswerLogEntry(itemId = item.id, answeredAt = now, grade = grade.code, mode = mode))
    }

    suspend fun history(itemId: String, limit: Int = 20) = dao.history(itemId, limit)
    suspend fun resetAll() = mutex.withLock { dao.deleteAll(); dao.deleteHistory() }
    suspend fun snapshot(): List<ItemProgress> = mutex.withLock { dao.getAll() }
    suspend fun historySnapshot(): List<AnswerLogEntry> = mutex.withLock { dao.allHistory() }
    suspend fun replaceAll(items: List<ItemProgress>, history: List<AnswerLogEntry>? = null) = mutex.withLock { dao.replaceAll(items, history) }
    /** Po zmianie treści lekcji stary indeks fragmentu może wskazywać inne miejsce – wznowienie od początku. */
    suspend fun resetResumePoint(itemId: String) = mutex.withLock {
        dao.get(itemId)?.let { if (it.lastSegment != 0) dao.upsert(it.copy(lastSegment = 0)) }
    }
}
