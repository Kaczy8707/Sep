# ZADANIE DLA CLAUDE — SEP Trener G1/G2/G3 E i D

Pracuj na istniejącym repozytorium aplikacji Kotlin/Compose. Nie twórz aplikacji od nowa.

## Import danych
1. Przed zmianami wykonaj kopię danych i sprawdź obecne identyfikatory oraz testy.
2. Wczytaj `manifest.json`, zwaliduj wszystkie pliki. Importuj tylko nowe rekordy z `*_tylko_nowe.json`, a pliki `*_rozszerzone.json` traktuj jako punkt porównania. Nie dubluj rekordów.
3. G1 01–21 muszą pozostać identyczne, aby zachować postępy. G2/G3 01–10 też zachowaj, jeśli już są.
4. Zachowaj istniejące pytania G1 (w tym 22 pytań i 10 sytuacji dozoru pochodzących z TXT); ten pakiet nie zawiera ich kopii. Dodaj nowe pytania G1 z pliku `G1_exam_tylko_nowe.json`.
5. Używaj klucza `id` do migracji i zachowania zakładek, ocen i historii.
6. `scope` = E/D/E lub D to proponowane oznaczenie redakcyjne, a nie oficjalna kwalifikacja. Jeśli aplikacja nie obsługuje `scope`, bezpiecznie je ignoruj lub dodaj kompatybilny parser.
7. Każdy rekord ma `verified:false`; nie zamieniaj na true bez audytu. Nie nazywaj pytań oficjalnymi pytaniami komisji.

## Funkcje
- Podział G1/G2/G3 i E/D, filtrowanie po zakresie, TTS offline, odczyt po zablokowaniu ekranu, odtwarzanie kolejnych lekcji, regulacja szybkości, tryb pytań z pauzą 8 s.
- Pytania z błędów i powtórki rozłożone w czasie. Dodaj scenariusz „co zrobiłbyś w sytuacji awaryjnej?” i ocenę odpowiedzi przez użytkownika.
- Wprowadź ekran „źródła i status weryfikacji” oraz oznaczenie treści pomocniczych.
- Nie wprowadzaj fikcyjnych wartości granicznych ani procedur bez dokumentacji.

## Kontrola jakości
- Test unikalności identyfikatorów, zgodności liczby rekordów z manifestem, zachowania postępów, wymowy skrótów, braku internetu, migracji bazy, TTS z atrapą.
- Zbuduj debug APK i release, uruchom lint i testy, pokaż raport i przekaż APK.
- Nie twierdź, że testy na fizycznym telefonie wykonano, jeśli nie wykonano.

## Kolejny etap: audyt i rozbudowa
Przeprowadź porównanie zakresów urządzeń i czynności z aktualnym załącznikiem rozporządzenia Dz.U. 2022 poz. 1392 oraz przepisami BHP. Zrób macierz pokrycia G1 2/6/8/10, G2 1/2/4/5/7/9/10, G3 6/8/10, bez automatycznego przenoszenia numeracji starych świadectw. Opracuj osobno materiały E i D. Zidentyfikuj brakujące tematy i zaproponuj audyt przez specjalistę.
