# ZADANIE DLA CLAUDE — APLIKACJA ANDROID „SEP TRENER”

Zbuduj DZIAŁAJĄCĄ natywną aplikację Android w Kotlin + Jetpack Compose, projekt Android Studio/Gradle, offline-first. Użytkownik uczy się do kwalifikacji energetycznych G1, G2, G3, w zakresie E i D. Dostarcz cały kod projektu, strukturę katalogów, konfigurację Gradle, instrukcję budowania APK i testy. Nie poprzestawaj na makietach, pseudokodzie ani planie.

## Dane
W załączonym pakiecie są pliki G1/G2/G3 lessons i exam JSON oraz content_manifest.json. Użyj ich jako lokalnych assetów, nie generuj automatycznie brakujących odpowiedzi. `verified:false` oznacza treści wymagające przeglądu eksperckiego, NIE nazywaj ich oficjalnymi pytaniami komisji. Zachowaj polski język i rozróżnienie E/D; dotychczasowe teksty nie są wystarczająco oznaczone E/D — tam gdzie brak jednoznaczności pokaż „E/D – do weryfikacji”, a nie zgaduj. Dodaj w UI notę: „Materiał pomocniczy; obowiązujące przepisy, normy i zakres kwalifikacji sprawdź przed egzaminem”. Nie wyświetlaj numerów PESEL ani danych osobowych z fotografii świadectw.

## Wymagania funkcjonalne
1. Biblioteka kursów: G1, G2, G3; lekcje, wyszukiwarka pełnotekstowa, zakładki, zaznaczanie ukończenia.
2. Polski syntezator Android TextToSpeech, Locale('pl','PL'), sprawdzenie dostępności polskiego głosu i czytelny komunikat jak zainstalować go w ustawieniach. Dostosowanie prędkości 0,7–1,4, pitch, wybór dostępnego głosu, odsłuch próbny. Preferuj głos męski, ale NIE zakładaj, że system gwarantuje płeć głosu; pokaż listę głosów. Nie używaj zewnętrznego API ani MP3.
3. Odtwarzanie w tle przez poprawnie zadeklarowany foreground service typu mediaPlayback, MediaSession, powiadomienie z play/pause/next/previous, kontrola z ekranu blokady i słuchawek. Obsłuż Android 13+ POST_NOTIFICATIONS i właściwe ograniczenia startu usługi na Android 14+. Audio focus, obsługa połączeń i odłączenia słuchawek. TTS z callbackami UtteranceProgressListener, dzielenie długiego tekstu na fragmenty mniejsze niż TextToSpeech.getMaxSpeechInputLength(), kolejka, możliwość zatrzymania i wznowienia od ostatniego fragmentu. Utrwalaj rozdział i indeks fragmentu w DataStore/Room. Nie obiecuj dokładnego wznowienia od słowa bez dodatkowego śledzenia pozycji.
4. Tryb egzaminu ustnego: odczytaj pytanie → odliczaj konfigurowalną pauzę (domyślnie 8 s) → odczytaj odpowiedź. Przyciski „Odpowiadam”, „Pokaż odpowiedź”, „Umiem”, „Powtórz”. Losowanie i filtr G1/G2/G3. Osobny tryb cichego quizu.
5. Postępy lokalnie w Room: poprawne/trudne, liczba powtórek, ostatnia data, proste powtórki interwałowe. Działa bez konta i bez internetu.
6. UI Material 3, dark mode, duże przyciski, obsługa TalkBack, polskie etykiety. Możliwość importu nowych paczek JSON przez Android Storage Access Framework, walidacja schematu i migracje.
7. Testy: parser JSON, podział tekstu na fragmenty TTS, przerwy egzaminacyjne, zapisywanie postępów, odtwarzanie po ubiciu procesu (graceful restore), brak internetu.

## Architektura
Kotlin, Jetpack Compose, MVVM, Room, DataStore, coroutines, dependency injection (Hilt lub prosta ręczna), media3 dla sesji i notyfikacji, Android TextToSpeech jako silnik syntezy. Nie używaj WebView ani PWA. Wymagaj kompatybilnego Android SDK i wypisz użyte wersje zależności; nie wymyślaj nieistniejących API.

## Kolejność realizacji
Najpierw utwórz uruchamialny projekt z G1 i testem syntezatora; potem dodaj wszystkie assety G1–G3, egzamin, postępy i usługę w tle. Po każdym etapie uruchom build/test, napraw błędy i podaj wynik. Jeśli nie możesz uruchomić Android SDK, jasno napisz, że build jest NIEZWERYFIKOWANY, oraz podaj polecenia do wykonania lokalnie. Nie pytaj o kwestie, które możesz rozsądnie ustalić samodzielnie. Jeśli musisz wybrać, priorytet: poprawne TTS i odtwarzanie w tle > wygląd > dodatkowe funkcje.

## Kontrola jakości treści
Nie dopisuj arbitralnych progów pomiarowych ani uniwersalnych instrukcji wykonywania niebezpiecznych prac. Oznacz każdą lekcję statusem „wymaga weryfikacji”. Przed publikacją produkcyjną zaproponuj osobny audyt merytoryczny z aktualnymi aktami prawa i normami. W treści zachowaj ostrzeżenia o BHP.
