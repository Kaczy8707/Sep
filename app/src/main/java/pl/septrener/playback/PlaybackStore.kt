package pl.septrener.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Utrwalony punkt odtwarzania: lista mediów, indeks ścieżki i indeks fragmentu. */
data class SavedPlayback(
    val mediaIds: List<String>,
    val trackIndex: Int,
    val segmentIndex: Int,
    val savedAt: Long,
)

class PlaybackStore(private val store: DataStore<Preferences>) {
    private val ids = stringPreferencesKey("media_ids")
    private val track = intPreferencesKey("track_index")
    private val segment = intPreferencesKey("segment_index")
    private val time = longPreferencesKey("saved_at")

    suspend fun save(state: SavedPlayback) = store.edit {
        it[ids] = state.mediaIds.joinToString(SEPARATOR)
        it[track] = state.trackIndex
        it[segment] = state.segmentIndex
        it[time] = state.savedAt
    }

    suspend fun load(): SavedPlayback? {
        val p = store.data.first()
        val list = p[ids]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        if (list.isEmpty()) return null
        return SavedPlayback(list, p[track] ?: 0, p[segment] ?: 0, p[time] ?: 0)
    }

    suspend fun clear() = store.edit { it.clear() }

    companion object { private const val SEPARATOR = "\n" }
}

/**
 * Odtwarza utrwalony stan w sposób odporny na zmiany treści (np. usunięta paczka):
 * pomija brakujące pozycje, przycina indeksy, a w razie potrzeby zaczyna od początku.
 */
object PlaybackRestorer {
    data class Restored(val tracks: List<Track>, val trackIndex: Int, val segmentIndex: Int)

    fun restore(saved: SavedPlayback, resolve: (String) -> Track?): Restored? {
        val savedCurrentId = saved.mediaIds.getOrNull(saved.trackIndex)
        val tracks = saved.mediaIds.mapNotNull(resolve).filter { it.segments.isNotEmpty() }
        if (tracks.isEmpty()) return null
        val index = tracks.indexOfFirst { it.mediaId == savedCurrentId }
        return if (index >= 0) {
            Restored(tracks, index, saved.segmentIndex.coerceIn(0, tracks[index].segments.size - 1))
        } else {
            Restored(tracks, 0, 0)
        }
    }
}
