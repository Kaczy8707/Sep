package pl.septrener.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Postęp nauki dla pojedynczej lekcji lub pytania. Przechowywany lokalnie (Room), bez konta. */
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
    val repetitions: Int = 0,
    /** Pudełko systemu Leitnera 0..5. */
    val box: Int = 0,
    val markedHard: Boolean = false,
    val lastReviewedAt: Long? = null,
    val nextDueAt: Long? = null,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ItemProgress>>

    @Query("SELECT * FROM progress WHERE itemId = :id")
    suspend fun get(id: String): ItemProgress?

    @Upsert
    suspend fun upsert(progress: ItemProgress)

    @Query("DELETE FROM progress")
    suspend fun deleteAll()

    @Query("DELETE FROM progress WHERE groupCode = :group")
    suspend fun deleteGroup(group: String)
}

/**
 * Wersja 1 bazy. Przy zmianie schematu: podnieś version, dodaj Migration(x, y) w [AppDatabase.MIGRATIONS]
 * i zachowaj wyeksportowany schemat w app/schemas (używany przez testy migracji).
 */
@Database(entities = [ItemProgress::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        const val NAME = "sep_trener.db"
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}
