package pl.septrener.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Index
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Postęp nauki dla pojedynczej lekcji lub pytania. Przechowywany lokalnie (Room), bez konta. */
@Serializable
@Entity(tableName = "progress")
data class ItemProgress(
    @PrimaryKey val itemId: String,
    val groupCode: String,
    val type: String,
    val completed: Boolean = false,
    val bookmarked: Boolean = false,
    /** Ostatni rozpoczęty fragment lekcji (punkt wznowienia). */
    val lastSegment: Int = 0,
    val knownCount: Int = 0,
    val hardCount: Int = 0,
    /** Odpowiedzi ocenione jako „częściowo” (od wersji 2 bazy). */
    @ColumnInfo(defaultValue = "0") val partialCount: Int = 0,
    val repetitions: Int = 0,
    /** Pudełko systemu Leitnera 0..5. */
    val box: Int = 0,
    val markedHard: Boolean = false,
    val lastReviewedAt: Long? = null,
    val nextDueAt: Long? = null,
)

/** Ocena odpowiedzi w egzaminie/quizie. Kod zapisywany w bazie – nie zmieniać. */
enum class Grade(val code: Int, val label: String) {
    KNOWN(2, "umiem"), PARTIAL(1, "częściowo"), HARD(0, "do powtórki");
    companion object { fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: HARD }
}

/** Historia odpowiedzi (od wersji 2 bazy). */
@Serializable
@Entity(tableName = "answer_log", indices = [Index("itemId")])
data class AnswerLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val answeredAt: Long,
    val grade: Int,
    /** Tryb: oral, quiz, scenario. */
    val mode: String,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ItemProgress>>

    @Query("SELECT * FROM progress")
    suspend fun getAll(): List<ItemProgress>

    @Query("SELECT * FROM progress WHERE itemId = :id")
    suspend fun get(id: String): ItemProgress?

    @Upsert
    suspend fun upsert(progress: ItemProgress)

    @Upsert
    suspend fun upsertAll(progress: List<ItemProgress>)

    /** Zastępuje całą tabelę zawartością kopii (atomowo). */
    @Transaction
    suspend fun replaceAll(progress: List<ItemProgress>, history: List<AnswerLogEntry>? = null) {
        deleteAll()
        upsertAll(progress)
        if (history != null) {
            deleteHistory()
            insertLogs(history.map { it.copy(id = 0) })
        }
    }

    @Query("DELETE FROM progress")
    suspend fun deleteAll()

    @Insert
    suspend fun insertLog(entry: AnswerLogEntry)

    @Query("SELECT * FROM answer_log WHERE itemId = :id ORDER BY answeredAt DESC LIMIT :limit")
    suspend fun history(id: String, limit: Int = 20): List<AnswerLogEntry>

    @Query("SELECT * FROM answer_log ORDER BY answeredAt")
    suspend fun allHistory(): List<AnswerLogEntry>

    @Query("DELETE FROM answer_log")
    suspend fun deleteHistory()

    @Insert
    suspend fun insertLogs(entries: List<AnswerLogEntry>)

    @Query("DELETE FROM progress WHERE groupCode = :group")
    suspend fun deleteGroup(group: String)
}

/**
 * Wersja 1 bazy. Przy zmianie schematu: podnieś version, dodaj Migration(x, y) w [AppDatabase.MIGRATIONS]
 * i zachowaj wyeksportowany schemat w app/schemas (używany przez testy migracji).
 */
@Database(entities = [ItemProgress::class, AnswerLogEntry::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        const val NAME = "sep_trener.db"

        /** v1 → v2: licznik ocen „częściowo” i tabela historii odpowiedzi. Istniejące postępy pozostają bez zmian. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `progress` ADD COLUMN `partialCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `answer_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`itemId` TEXT NOT NULL, `answeredAt` INTEGER NOT NULL, `grade` INTEGER NOT NULL, `mode` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_answer_log_itemId` ON `answer_log` (`itemId`)")
            }
        }
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
