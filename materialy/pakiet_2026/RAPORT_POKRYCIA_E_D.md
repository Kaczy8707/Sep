# SEP Trener – raport importu pakietu 2026 i pokrycia zagadnień E/D

Stan na 2026-11-16. Wszystkie treści: **verified = false** (materiał pomocniczy, nie oficjalna baza pytań komisji).

## 1. Import i walidacja

| Kontrola | Wynik |
|---|---|
| Kopia danych przed zmianami | ✅ kopia postępów tworzona automatycznie: przy imporcie paczki oraz przy pierwszym uruchomieniu nowego APK ze zmienioną treścią (`files/backups/postepy-*.json`, z historią odpowiedzi) |
| Zaimportowano | wyłącznie `*_tylko_nowe.json`: 18 lekcji + 72 pytania = **90 rekordów** (jako osobne pliki `*_2026.json`, dotychczasowe pliki bez zmian) |
| Pliki porównawcze | `*_pelne_rozszerzenie.json` / `*_rozszerzone.json` = dotychczasowe rekordy + nowe – **zgodne co do rekordu** (test `Pakiet2026Test`) |
| G1 01–21, G2/G3 01–10, pytania G2/G3 001–022 | **identyczne** z poprzednią wersją – postępy (powiązane z `id`) zachowane |
| Pytania G1 z TXT (22 + 10 sytuacji dozoru) | zachowane; nowe pytania G1 mają osobne id `g1-question-ext-001…024` |
| Unikalność id | 207 rekordów, 207 unikalnych |
| Zgodność z `manifest.json` | lekcje G1 27, G2 16, G3 16 ✅; pytania G2 46, G3 46 ✅; G1: manifest liczy 24 nowe + 32 istniejące = 56 w aplikacji |
| `sourceLessonId` | wszystkie 72 odwołania wskazują istniejące lekcje |
| `scope` | obsłużony jako **propozycja redakcyjna** (etykieta w aplikacji: „Zakres (propozycja redakcyjna)”) |
| Paczka dla poprzedniego APK | `SEP_2026_import_tylko_nowe.json` (90 rekordów, schemat v1) – import przez Ustawienia, bez duplikatów przy powtórzeniu |

### Nowe funkcje aplikacji
* Filtr zakresu w egzaminie: **E**, **D**, wszystkie (filtr E/D obejmuje też pytania „E i D”; nieoznaczone tylko w „wszystkie”).
* Pule pytań: wszystkie / do powtórki (harmonogram) / **pytania z błędów** (ostatnia ocena „Powtórz” lub „Częściowo”).
* Tryb **„Co zrobiłbyś w sytuacji awaryjnej?”**: 24 pytania sytuacyjne z redakcyjnej listy `scenarios.json` (same identyfikatory, bez nowej treści; paczki mogą też oznaczać rekord `"scenario": true`).
* Ocena trzystopniowa: **Umiem / Częściowo / Powtórz**, z historią odpowiedzi (data, ocena, tryb) na ekranie pytania.
* Ekran **„Źródła i status weryfikacji”** (Ustawienia): pakiety, pliki, liczby rekordów, status, akty prawne do samodzielnej kontroli; oznaczenie „Treść pomocnicza” na każdej pozycji.
* Powiązanie pytanie ↔ lekcja (`sourceLessonId`): „Pytania do tej lekcji” i „Powiązana lekcja”.
* Baza danych v1 → v2 (licznik „częściowo”, tabela historii) z migracją zachowującą postępy.
* Poprawiony błąd odtwarzacza: polecenie „play” wydane w trakcie wczytywania playlisty (np. wznowienie z Bluetooth po ubiciu procesu) było gubione.
* Wymowa: dodatkowo `ups`, `lpg` zapisane w nowych pytaniach małymi literami.

### Testy
71 testów JVM/Robolectric – wszystkie zielone, lint bez błędów, zbudowane APK debug i release.
Nowe: `Pakiet2026Test` (manifest, unikalność, porównanie z „rozszerzone”, kontrola egzaminu ustnego dla każdego z 148 pytań: pytanie → pauza 8 s → odpowiedź),
`ContentUpdateTest` (aktualizacja przez nowe APK i przez import na poprzedniej wersji z zachowaniem zakładek, ocen, historii),
`DatabaseMigrationTest` (v1 → v2), `ExamSelectionTest` (E/D, błędy, scenariusze, ocena „częściowo”),
`TtsPlayerTest.extendedPackQuestionPlaysWithPause` (TTS z atrapą), `TtsNormalizerTest` (wymowa skrótów w całej treści), `OfflineAndImportTest` (brak INTERNET).
**Nie wykonano testów na fizycznym telefonie ani emulatorze** (brak akceleracji w środowisku budowania).

## 2. Pokrycie E/D (stan treści)

| Grupa | Lekcje | z sekcjami „Na egzamin E/D” | Pytania | E | D | E i D | bez oznaczenia | Scenariusze |
|---|---|---|---|---|---|---|---|---|
| G1 | 27 | 6 (22–27) | 56 | 6 | 16* | 12 | 22 | 12 |
| G2 | 16 | 6 (11–16) | 46 | 6 | 6 | 12 | 22 | 5 |
| G3 | 16 | 6 (11–16) | 46 | 6 | 6 | 12 | 22 | 7 |

\* G1: 6 pytań D z pakietu 2026 + 10 „sytuacji dozoru” z TXT (oznaczonych D, bo tak nazywa je źródło).

Wnioski:
* Rozdzielenie E i D istnieje tylko w nowych lekcjach (po jednym zdaniu „Na egzamin E” i „Na egzamin D”) i w 72 nowych pytaniach. **Lekcje G1 01–21 i G2/G3 01–10 nie mają oznaczenia E/D ani przypisanych pytań.**
* Pytania z pakietu 2026 mają jednolity szablon (4 na lekcję: zasada / zagrożenia / przykład E / obowiązki D); odpowiedzi to zdania z lekcji. Dobre do powtórek, ale **nie symulują pytań problemowych** komisji.
* Część D w każdej lekcji to jedno zdanie – **niewystarczające** do przygotowania na egzamin dozorowy.

## 3. Macierz punktów wskazanych do przeglądu

Tekst załącznika Dz.U. 2022 poz. 1392 **nie był dostępny w środowisku** (blokada sieci), dlatego kolumna „nazwa punktu” wymaga uzupełnienia z tekstu aktu.
Przypisanie lekcji jest **robocze, według tematów lekcji** – bez automatycznego przenoszenia numeracji starych świadectw
(lekcje G1 7–9 same opisują się jako „dawny punkt G1/6, G1/8, G1/10”, czyli numeracja sprzed 2022 r.).

| Punkt | Nazwa wg Dz.U. 2022 poz. 1392 | Lekcje (robocze przypisanie) | Pytania | Ocena pokrycia |
|---|---|---|---|---|
| G1 2 | do uzupełnienia | G1 01–06, 10–27 (instalacje nn, ochrona, pomiary, BHP, dozór) | 56 | **częściowe** – dobre podstawy nn; brak tematów z sekcji 4 |
| G1 6 | do uzupełnienia | G1 07 (elektroliza – wg lekcji „dawny G1/6”) | 2 (pytania 15–16) + 1 sytuacja | **szczątkowe** – 1 lekcja |
| G1 8 | do uzupełnienia | G1 08 (sieć trakcyjna – „dawny G1/8”) | 1 + 1 sytuacja | **szczątkowe** – 1 lekcja |
| G1 10 | do uzupełnienia | G1 09 (AKPiA – „dawny G1/10”), częściowo 24 | 1 | **szczątkowe** |
| G2 1, 2, 4, 5, 7, 9, 10 | do uzupełnienia | kotły: 02, 12 · sieci cieplne: 03, 16 · para/ciśnienie: 04 · pompy, wentylatory, sprężarki: 05, 14 · chłodnictwo/wentylacja: 06, 15 · piece: 07 · AKPiA: 08 · bilans: 11 · wymienniki: 13 | 46 | **wymaga przypisania** do konkretnych punktów; przypuszczalnie po 1–2 lekcje na rodzaj urządzeń |
| G3 6, 8, 10 | do uzupełnienia | instalacje i próby: 02, 04, 13 · odbiorniki przemysłowe/palniki: 03, 06, 15 · redukcja: 05, 14 · detekcja/AKPiA: 07 · gazy/LPG: 11 · CO: 12 · awarie: 08, 16 | 46 | **wymaga przypisania**; brak lekcji o pracach gazoniebezpiecznych |

## 4. Brakujące tematy do opracowania (propozycja, do potwierdzenia przez eksperta)

Wyszukiwanie w treści (207 rekordów) nie znalazło lub znalazło tylko marginalnie:

**Wspólne dla D (wszystkie grupy)**
1. Przepisy wymagane w części D egzaminu: prawo energetyczne i akty wykonawcze, przepisy o eksploatacji i dokumentacji technicznej, efektywność energetyczna / racjonalna gospodarka energią – **brak** w treści.
2. Przepisy BHP przy urządzeniach energetycznych: rodzaje poleceń (pisemne/ustne), funkcje (poleceniodawca, koordynujący, dopuszczający, kierujący, nadzorujący), prace szczególnie niebezpieczne – tylko ogólne wzmianki (G1 20).
3. Instrukcja eksploatacji – wymagana zawartość, aktualizacja, odpowiedzialność – 1 wzmianka.
4. Ochrona przeciwpożarowa i dobór środków gaśniczych przy urządzeniach – marginalnie.
5. Ochrona środowiska przy eksploatacji (emisje, czynniki chłodnicze, odpady) – marginalnie.

**G1**
6. Sprzęt ochronny i izolacyjny – rodzaje i badania okresowe – brak.
7. Uziemienia i pomiar rezystancji uziemień, połączenia wyrównawcze – tylko wzmianki.
8. Transformatory, kable i ich badania, akumulatory/baterie, kompensacja mocy biernej – brak.
9. Elektroliza, sieć trakcyjna, AKPiA – po jednej lekcji; potrzebne osobne moduły E i D dla każdego wskazanego punktu.
10. Urządzenia w wykonaniu przeciwwybuchowym / strefy zagrożenia wybuchem – 1 wzmianka (jeśli w zakresie świadectwa).

**G2**
11. Uzdatnianie wody kotłowej i sieciowej – brak.
12. Relacja z dozorem technicznym (UDT) dla urządzeń ciśnieniowych – kilka wzmianek, bez opracowania.
13. Paliwa: składowanie i podawanie, spalanie i emisje w kotłach – brak/marginalnie.
14. Pompy ciepła, turbiny (jeśli w zakresie) – brak.
15. AKPiA dla urządzeń cieplnych – 1 lekcja.

**G3**
16. Prace gazoniebezpieczne – organizacja, polecenia, zabezpieczenia – **brak** (kluczowe dla D).
17. Strefy zagrożenia wybuchem i urządzenia Ex – wzmianki.
18. Odorowanie, właściwości gazów (granice wybuchowości – tylko z dokumentacji, bez wymyślonych wartości) – marginalnie.
19. LPG – butle, zbiorniki, instalacje – tylko lekcja 11.
20. Przemysłowe odbiorniki gazu i ich automatyka – 2–3 lekcje, bez podziału E/D w starszych.

**Format pytań**
21. Pytania problemowe i sytuacyjne dla G2/G3 (obecnie scenariusze G2/G3 to 5 i 7 pozycji; w G1 opisy sytuacji są w pytaniu, w G2/G3 często dopiero w odpowiedzi).
22. Osobne zestawy „egzamin E” i „egzamin D” dla każdego wskazanego punktu.

## 5. Usterki danych do poprawy w źródle (bez zmian w aplikacji)
* Skróty małymi literami w treści pytań: „zasilanie rezerwowe **ups**”, „gaz ziemny i **lpg**” (lektor czyta poprawnie dzięki regule wymowy; na ekranie widoczne małe litery).
* Podwójny dwukropek, np. „Wyjaśnij zasadę: kotły: poziom wody…”.
* Pytania G2/G3 „Wyjaśnij: sytuacja egzaminacyjna?” – sytuacja jest opisana dopiero w odpowiedzi; pytanie nie nadaje się do trybu scenariuszy w obecnej postaci.
* Pytania G2/G3 001–022 bez `scope` i `sourceLessonId`.
* Każda nowa lekcja kończy się identycznym akapitem „Sprawdź wiedzę… Ważne…” – przy słuchaniu kilku lekcji z rzędu powtarza się.

## 6. Propozycja audytu przez specjalistę
1. Porównanie zakresów punktów G1 2/6/8/10, G2 1/2/4/5/7/9/10, G3 6/8/10 z aktualnym załącznikiem Dz.U. 2022 poz. 1392 i uzupełnienie kolumny „nazwa punktu” w sekcji 3.
2. Przegląd każdej lekcji i pytania przez osobę z kwalifikacjami D w danej grupie, z aktualnymi przepisami BHP i normami (PN-HD 60364, PN-EN 50110-1, normy instalacji gazowych i ciśnieniowych właściwe dla zakresu).
3. Decyzja o oznaczeniu E/D dla 66 pytań i 41 lekcji bez oznaczenia.
4. Po audycie – ustawienie `verified: true` **w paczce**, z polem `source` wskazującym akt/normę i datę przeglądu; aplikacja pokaże status automatycznie.
5. Opracowanie tematów z sekcji 4 jako nowe paczki (ten sam format, nowe `id`), z treścią opartą na dokumentach źródłowych, bez wymyślonych wartości granicznych.
