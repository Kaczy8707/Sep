package pl.septrener.playback

/** Stan odtwarzacza publikowany przez [TtsPlayer] dla UI (ten sam proces). */
data class PlaybackUiState(
    val tracks: List<Track> = emptyList(),
    val trackIndex: Int = 0,
    val segmentIndex: Int = 0,
    val playing: Boolean = false,
    val waitingForEngine: Boolean = false,
    val ended: Boolean = false,
    /** SystemClock.elapsedRealtime() rozpoczęcia bieżącego fragmentu – do odliczania pauzy egzaminacyjnej. */
    val segmentStartedAt: Long = 0,
    val error: String? = null,
) {
    val track: Track? get() = tracks.getOrNull(trackIndex)
    val segment: Segment? get() = track?.segments?.getOrNull(segmentIndex)
    val hasPlaylist get() = tracks.isNotEmpty()
    fun isCurrent(mediaId: String) = track?.mediaId == mediaId
}
