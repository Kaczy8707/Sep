package pl.septrener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.septrener.content.ContentParser
import pl.septrener.playback.ExamTiming
import pl.septrener.playback.SpeechQueue
import pl.septrener.playback.TrackFactory
import pl.septrener.tts.TtsNormalizer.normalize

class TtsNormalizerTest {
    private val allItems = ContentRepositoryFiles.all.flatMap { ContentParser.parse(TestFiles.asset(it), it).items }
    private val allTexts = allItems.flatMap { listOf(it.title, it.content, it.question, it.answer) }.filter { it.isNotBlank() }

    @Test
    fun unitsAreSpokenWithPolishForms() {
        assertEquals("R w omach opisuje opór", normalize("R [Ω] opisuje opór"))
        assertEquals("granica Zs wynosi 2,3 oma.", normalize("granica Zs wynosi 2,3 Ω.").replace("Z es", "Zs"))
        assertEquals("1 megaom, 2 megaomy, 5 megaomów, 0,5 megaoma", normalize("1 MΩ, 2 MΩ, 5 MΩ, 0,5 MΩ"))
        assertEquals("przekrój 2,5 milimetra kwadratowego", normalize("przekrój 2,5 mm²"))
        assertEquals("22 kiloomy i 12 kiloomów", normalize("22 kΩ i 12 kΩ"))
        assertEquals("U w woltach, I w amperach", normalize("U [V], I [A]"))
    }

    @Test
    fun formulasAreSpokenInWords() {
        assertEquals("I be mniejsze lub równe I en mniejsze lub równe I zet", normalize("Ib ≤ In ≤ Iz"))
        assertEquals("Z es razy I a mniejsze lub równe U zero", normalize("Zs × Ia ≤ U0"))
        assertEquals("P równa się pierwiastek z trzech razy U razy I razy kosinus fi", normalize("P = √3 × U × I × cos φ"))
        assertEquals("P strat równa się I do kwadratu R", normalize("Pstrat = I²R"))
        assertEquals("RCD o I delta en".replace("RCD", "er ce de"), normalize("RCD o IΔn"))
        assertEquals("delta T różnica temperatur", normalize("ΔT różnica temperatur"))
        assertEquals("Q równa się m z kropką razy c pe razy delta T", normalize("Q = m_dot × c_p × ΔT"))
        assertEquals("układzie 230 na 400 V", normalize("układzie 230/400 V"))
        assertEquals("w kg na s, kJ na (kg K)", normalize("w kg/s, kJ/(kg K)"))
        assertEquals("z u pe es lub pe fau lub agregatu", normalize("z UPS/PV/agregatu"))
    }

    @Test
    fun acronymsAreSpelledLongestFirst() {
        assertEquals("te en ce es, te en ce, te en es i te te", normalize("TN-C-S, TN-C, TN-S i TT"))
        assertEquals("przewody pe e i pe e en, mostki en pe e", normalize("przewody PE i PEN, mostki N–PE"))
        assertEquals("er ce be o oraz er ce de", normalize("RCBO oraz RCD"))
        assertEquals("u pe es, pe fau, es pe de", normalize("UPS, PV, SPD"))
        // nie rusza słów, które tylko zawierają te litery
        assertEquals("PERSONEL, PEŁNY, Instalacja, Iarząb", normalize("PERSONEL, PEŁNY, Instalacja, Iarząb"))
    }

    @Test
    fun lessonHeaderAndScopeAreReadNaturally() {
        assertEquals("Lekcja 1. Zakres kwalifikacji i odpowiedzialność E i D.", normalize("LEKCJA 1. 1. Zakres kwalifikacji i odpowiedzialność E/D."))
        assertEquals("Elektroliza — dawny punkt G1 łamane przez 6.", normalize("Elektroliza — dawny punkt G1/6."))
        assertEquals("Lekcja 12. Bezpieczne odłączanie i loto.", normalize("LEKCJA 12. Bezpieczne odłączanie i LOTO."))
        assertEquals("Przycisk test na er ce de", normalize("Przycisk TEST na RCD"))
    }

    /** Żaden symbol matematyczny z treści nie trafia do syntezatora w postaci znaku. */
    @Test
    fun noRawSymbolsReachTheSynthesizer() {
        val forbidden = "×≤≥Δ√²φΩ_=[]".toSet()
        allTexts.forEach { t ->
            val spoken = normalize(t)
            val left = spoken.filter { it in forbidden }
            assertTrue("Pozostały symbole „$left” w: ${spoken.take(120)}", left.isEmpty())
        }
    }

    @Test
    fun everyAcronymInContentIsCoveredOrDeliberatelyLeft() {
        // skróty pozostałe w tekście PO normalizacji – czyli to, co usłyszy użytkownik
        val spoken = allTexts.joinToString("\n") { normalize(it) }
        val found = Regex("\\b[A-Z]{2,}(?:-[A-Z]+)*\\b").findAll(spoken).map { it.value }.toSet()
        assertTrue("Skróty bez reguły wymowy: $found", found.isEmpty())
        val unknown = found
        assertTrue("Skróty bez reguły wymowy: $unknown", unknown.isEmpty())
    }

    @Test
    fun spokenSegmentsFitEngineLimitAndDisplayIsUnchanged() {
        val factory = TrackFactory(3984)
        allItems.forEach { item ->
            val tracks = if (item.isLesson) listOf(factory.lesson(item), factory.lesson(item, normalize = false))
            else listOf(factory.examQuestion(item, ExamTiming()), factory.examQuestion(item, ExamTiming(), normalize = false))
            val (on, off) = tracks
            assertEquals(on.segments.map { it.text }, off.segments.map { it.text })
            off.segments.forEach { assertEquals(it.text, it.spoken) }
            on.segments.filterNot { it.isSilence }.forEach { assertTrue(it.spoken.length <= 3984); assertFalse(it.spoken.isBlank()) }
        }
    }

    @Test
    fun queueSendsSpokenTextToEngine() {
        val item = allItems.first { it.id == "g1-lesson-13" }
        val engine = FakeSpeechEngine()
        val queue = SpeechQueue(engine, { 0 }, object : SpeechQueue.Callback {})
        val track = TrackFactory(3984).lesson(item)
        queue.setPlaylist(listOf(track)); queue.play()
        val spoken = engine.queued.joinToString(" ") { it.text!! }
        assertTrue(spoken.contains("I be mniejsze lub równe I en mniejsze lub równe I zet"))
        assertFalse(spoken.contains("≤"))
        assertTrue(track.segments.joinToString(" ") { it.text }.contains("Ib ≤ In ≤ Iz"))
    }
}

object ContentRepositoryFiles {
    val all = listOf("G1_lessons.json", "G1_exam.json", "G2_lessons.json", "G2_exam.json", "G3_lessons.json", "G3_exam.json")
}
