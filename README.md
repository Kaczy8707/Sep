# SEP Trener (Android)

Natywna aplikacja Android (Kotlin + Jetpack Compose) do nauki na egzaminy kwalifikacyjne **G1, G2, G3** w zakresie **E i D**.
Działa w pełni **offline** (aplikacja nie ma uprawnienia `INTERNET`), czyta lekcje polskim głosem przez systemowy
**Android TextToSpeech** – również przy zablokowanym ekranie.

> Materiał pomocniczy; obowiązujące przepisy, normy i zakres kwalifikacji sprawdź przed egzaminem.
> Wszystkie treści mają status **„wymaga weryfikacji”** i **nie są oficjalną bazą pytań komisji**.

## Funkcje

| Obszar | Co jest |
|---|---|
| Biblioteka | G1 (11 lekcji), G2 (10), G3 (10); zakładki, ukończenie (ręczne i automatyczne po odsłuchu), filtr, wyszukiwarka pełnotekstowa (bez rozróżniania wielkości liter i polskich znaków) |
| Lektor | `TextToSpeech` z `Locale("pl","PL")`, sprawdzenie polskiego głosu + instrukcja instalacji, wybór silnika i głosu (lista z jakością, offline/online, wskazówką płci jeśli silnik ją podaje), prędkość 0,7–1,4, wysokość głosu, odsłuch próbny |
| Odtwarzanie w tle | `MediaSessionService` (media3) typu `mediaPlayback`, powiadomienie z play/pauza/następna/poprzednia, ekran blokady, przyciski słuchawek/Bluetooth, fokus audio (pauza przy połączeniu, wznowienie po), pauza po odłączeniu słuchawek, `POST_NOTIFICATIONS` na Android 13+ |
| Fragmenty | podział tekstu na akapity/zdania, każdy fragment < `getMaxSpeechInputLength()`; pauza i wznowienie **od początku bieżącego fragmentu** (bez śledzenia pojedynczych słów); przewijanie fragmentów; dotknięcie fragmentu = czytaj od niego |
| Pamięć pozycji | lista, lekcja i fragment w DataStore; postęp lekcji w Room; odtworzenie po ubiciu procesu (także z przycisku Play na słuchawkach) |
| Egzamin ustny | pytanie → odliczana pauza (domyślnie 8 s, konfigurowalna 3–30 s; opcjonalnie pauzy z paczki) → odpowiedź; przyciski **Odpowiadam**, **Pokaż odpowiedź**, **Umiem**, **Powtórz**; losowanie, filtr G1/G2/G3, tryb „tylko do powtórki” |
| Inne tryby | cichy quiz (bez dźwięku); słuchanie pytań w tle jako playlista |
| Postępy | Room: umiem/trudne, liczba powtórek, ostatnia data, powtórki interwałowe (system Leitnera: 0/1/3/7/14/30 dni) |
| Import | paczki JSON przez Storage Access Framework, walidacja schematu i pól, migracja formatu (tablica v0 → manifest v1), raport z pominiętych pozycji |
| UI | Material 3, tryb ciemny (i kolory dynamiczne na Android 12+), duże przyciski, etykiety dla TalkBack, polskie teksty |

## Treści

Pliki w `app/src/main/assets/content/` pochodzą z pakietu w `materialy/`:

* `G1/G2/G3_lessons.json`, `G2/G3_exam.json` – skopiowane bez zmian,
* `G1_exam.json` – w pakiecie był pusty (`[]`), dlatego został **dosłownie** przeniesiony z
  `materialy/G1_Audiobook_Dialogi_Egzaminacyjne.txt` skryptem `tools/convert_g1_dialogs.py`
  (22 pytania + 10 „sytuacji dozoru”; test `ContentParserTest.g1ExamWasConvertedVerbatimFromTxt` sprawdza zgodność z TXT).
  Zakres „D” nadano tylko pozycjom opisanym w źródle jako „Sytuacja dozoru”; pozostałe mają „E/D – do weryfikacji”.

Nie dopisano żadnych odpowiedzi ani progów pomiarowych. Pozycja bez odpowiedzi jest przy imporcie odrzucana.

### Format paczki (schemat v1)

```json
{
  "schemaVersion": 1,
  "language": "pl-PL",
  "items": [
    { "id": "g1-lesson-12", "group": "G1", "type": "lesson", "title": "…", "content": "…", "verified": false, "scope": "E/D" },
    { "id": "g1-question-100", "group": "G1", "type": "oral_question", "question": "…", "answer": "…", "pauseSeconds": 8, "verified": false }
  ]
}
```

Akceptowana jest też sama tablica `[ … ]` (format v0 z pakietu – migrowana automatycznie). `scope`: `E`, `D`, `E/D`; brak = „E/D – do weryfikacji”.
Pozycje o tym samym `id` co wbudowane zastępują je (raport importu podaje ich liczbę).

## Budowanie APK

Wymagania: **Android Studio Ladybug (2024.2) lub nowsze** albo JDK 17+ i Android SDK (platforma 35).

```bash
# Android Studio: File → Open → katalog repozytorium → Build → Build App Bundle(s)/APK(s) → Build APK(s)

# lub z wiersza poleceń:
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk (R8, ~2 MB; podpis kluczem debug)
./gradlew testDebugUnitTest      # testy JVM + Robolectric
./gradlew connectedDebugAndroidTest  # test prawdziwego TTS na podłączonym telefonie
```

Instalacja: `adb install -r app/build/outputs/apk/debug/app-debug.apk` lub skopiuj APK na telefon.
Wersja release jest podpisana kluczem debug wyłącznie do użytku prywatnego – do publikacji skonfiguruj własny keystore.

Na telefonie: **Ustawienia → Zamiana tekstu na mowę** – wybierz silnik z polskim głosem (np. Usługi rozpoznawania mowy Google) i pobierz dane głosu „Polski”.
W aplikacji: **Ustawienia → Głos** – odsłuchaj dostępne głosy. System nie gwarantuje płci głosu; aplikacja preferuje głos oznaczony przez silnik jako męski, jeśli taki jest.

## Architektura

```
pl.septrener
├── content/   modele, parser + walidacja + migracje schematu, repozytorium (assets + importy), wyszukiwarka
├── tts/       TextChunker (podział tekstu), SpeechEngine (interfejs), AndroidTtsEngine (TextToSpeech pl-PL)
├── playback/  Track/TrackFactory, SpeechQueue (kolejka fragmentów), TtsPlayer (media3 SimpleBasePlayer),
│              PlaybackService (MediaSessionService), PlaybackStore (DataStore), PlaybackConnection (MediaController)
├── data/      Room: ItemProgress, DAO, baza (schemat w app/schemas), Srs (Leitner), ProgressRepository
├── exam/      ExamSession (kolejka i oceny), ExamViewModel
├── settings/  DataStore ustawień
└── ui/        Compose: biblioteka, lekcja, egzamin, quiz, odtwarzacz, wyszukiwarka, postępy, ustawienia
```

MVVM z ręcznym DI (`AppContainer`). UI steruje odtwarzaniem przez `MediaController`, więc usługa pierwszoplanowa
startuje zgodnie z ograniczeniami Androida 12/14+. Konwencja wewnętrzna: `positionMs` w `setMediaItems/seekTo` oznacza indeks fragmentu.

### Wersje

| Składnik | Wersja |
|---|---|
| Android Gradle Plugin | 8.7.3 (Gradle 8.11.1 przez wrapper) |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.28 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 (Android 8.0) |
| Compose BOM | 2024.12.01 (Material 3) |
| media3-session | 1.5.1 |
| Room | 2.6.1 |
| DataStore | 1.1.1 |
| Navigation Compose | 2.8.5 |
| Lifecycle | 2.8.7 |
| kotlinx.serialization / coroutines | 1.7.3 / 1.9.0 |
| Testy | JUnit 4.13.2, Robolectric 4.14.1 |

Lint wskazuje nowsze wersje bibliotek – projekt buduje się na powyższych; aktualizację warto zrobić osobnym krokiem z ponownym uruchomieniem testów.

## Testy (44, wszystkie zielone)

* `ContentParserTest` – parser JSON, walidacja, migracja v0→v1, odrzucanie braków, zgodność G1 z TXT,
* `TextChunkerTest` – limity długości fragmentów na wszystkich lekcjach, zachowanie treści,
* `SpeechQueueTest` – pauza/wznowienie od fragmentu, ignorowanie spóźnionych callbacków, przejście do następnej ścieżki, oczekiwanie na silnik, błędy,
* `ExamLogicTest` – pauza egzaminacyjna (8 s domyślnie, konfiguracja, limity), „Pokaż odpowiedź”, sesja i powtórki interwałowe,
* `ProgressRepositoryTest` – zapis postępów w Room,
* `PlaybackRestoreTest` – zapis pozycji, nowa instancja DataStore („ubicie procesu”), łagodne odtworzenie przy zmienionej treści,
* `TtsPlayerTest` – odtwarzacz media3: playlista, play/pauza/wznowienie, fokus audio, odłączenie słuchawek, następna lekcja, odtworzenie stanu,
* `OfflineAndImportTest` – brak uprawnienia `INTERNET`, wczytanie assetów, import/usuwanie paczek,
* `TtsEngineDeviceTest` (androidTest) – prawdziwy syntezator na urządzeniu.

## Co zostało zweryfikowane, a co nie

* ✅ Czysty build `assembleDebug` i `assembleRelease`, `lintDebug` bez błędów, 44 testy jednostkowe/Robolectric.
* ⚠️ **Nie uruchomiono na fizycznym telefonie ani emulatorze** (środowisko budowania nie ma akceleracji sprzętowej).
  Zachowanie prawdziwego silnika TTS, powiadomienia i ekranu blokady trzeba sprawdzić na urządzeniu –
  pomoże w tym `connectedDebugAndroidTest`.

## Przed publikacją produkcyjną

Wymagany jest **osobny audyt merytoryczny** treści przez eksperta z aktualnymi aktami prawnymi
(m.in. przepisy o świadectwach kwalifikacyjnych) i normami, w tym jednoznaczne oznaczenie zakresu E/D każdej lekcji i pytania.
Treści zachowują ostrzeżenia BHP; aplikacja nie zawiera instrukcji wykonywania prac niebezpiecznych ani danych osobowych.
