# Raport importu – rozbudowa G1 (lekcje 12–21)

## 1. Walidacja pakietu

| Sprawdzenie | Wynik |
|---|---|
| `G1_lessons_rozszerzone.json` – liczba pozycji | 21 (format v0: sama tablica, migrowany automatycznie do v1) |
| Lekcje 01–11 vs dotychczasowe w aplikacji | **identyczne** (bajt w bajt po sparsowaniu) |
| Lekcje 12–21 vs `G1_lessons_tylko_nowe.json` | **identyczne** |
| Unikalność `id`, `group = G1`, `type = lesson`, niepuste `title`/`content` | ✅ bez uwag |
| `verified` | wszystkie `false` – pozostawione bez zmian |
| `scope` (E/D) | brak we wszystkich lekcjach → w aplikacji „E/D – do weryfikacji” |

## 2. Co zmieniono w aplikacji

* **Wbudowane lekcje G1: 21** (plik `app/src/main/assets/content/G1_lessons.json` = `G1_lessons_rozszerzone.json`).
* **Import bez przebudowy** (Ustawienia → Importuj paczkę JSON) na telefonie z poprzednią wersją:
  * przed zapisem tworzona jest **kopia zapasowa postępów** (`files/backups/postepy-*.json`, ostatnie 10),
  * raport: nowe / zmienione / bez zmian, liczba pozycji z zachowanym postępem, nazwa kopii,
  * zakładki, ukończenia i oceny są powiązane z `id`, więc pozostają; dla lekcji o **zmienionej treści** zerowany jest
    tylko punkt wznowienia (stary indeks fragmentu wskazywałby inne miejsce),
  * przywracanie ostatniej kopii w Ustawieniach (z kopią bieżącego stanu przed przywróceniem),
  * błędna paczka niczego nie zmienia i nie tworzy kopii.
  Uwaga: należy importować **tylko** `G1_lessons_rozszerzone.json`. Ponowny import `tylko_nowe` jest nieszkodliwy (10 × „bez zmian”).
* **Wymowa (TTS)** – nowy moduł `TtsNormalizer`, działa tylko na tekście wysyłanym do syntezatora (ekran bez zmian),
  przełącznik i „Test wymowy” w Ustawieniach. Przykłady z lekcji:

| Tekst | Czytane jako |
|---|---|
| `Ib ≤ In ≤ Iz` | I be mniejsze lub równe I en mniejsze lub równe I zet |
| `Zs × Ia ≤ U0` | Z es razy I a mniejsze lub równe U zero |
| `P = √3 × U × I × cos φ` | P równa się pierwiastek z trzech razy U razy I razy kosinus fi |
| `2,3 Ω`, `R [Ω]`, `2,5 mm²` | 2,3 oma, R w omach, 2,5 milimetra kwadratowego |
| `IΔn`, `ΔT`, `I²R` | I delta en, delta T, I do kwadratu R |
| `RCD`, `RCBO`, `PE`, `PEN`, `N–PE` | er ce de, er ce be o, pe e, pe e en, en pe e |
| `TN-C-S`, `TT`, `IT` | te en ce es, te te, i te |
| `UPS/PV/agregatu` | u pe es lub pe fau lub agregatu |
| `LEKCJA 1. 1.`, `LOTO`, `TEST` | Lekcja 1., loto, test |

  Testy sprawdzają, że po normalizacji w żadnej lekcji ani pytaniu nie zostaje znak `× ≤ ≥ Δ √ ² φ Ω _ = [ ]`
  ani skrót pisany wersalikami bez reguły wymowy. **Brzmienia nie da się zweryfikować bez telefonu** –
  użyj przycisku „Test wymowy wzorów i skrótów”. Jeśli silnik czyta skróty lepiej sam, wyłącz przełącznik.

## 3. Testy

56 testów JVM/Robolectric – wszystkie zielone, w tym nowe:
`G1ExtensionImportTest` (import na „starej” wersji: +10 lekcji, 11 bez zmian, zachowane zakładki/ukończenia/oceny/punkt wznowienia, kopia zapasowa, brak duplikatów, przywracanie kopii, zmieniona lekcja, błędna paczka)
oraz `TtsNormalizerTest` (jednostki, wzory, skróty, pokrycie całej treści, limit długości fragmentu, tekst ekranowy bez zmian).

## 4. Do osobnego przeglądu merytorycznego (bez potwierdzania zgodności)

Poniższe punkty **nie są stwierdzeniem błędu** – to miejsca, które ekspert powinien porównać z aktualnymi przepisami
i normami przed zmianą statusu na `verified: true`:

1. **L12 (LOTO)** – termin z praktyki zagranicznej; sprawdzić spójność z kolejnością czynności przygotowania miejsca pracy
   w aktualnych przepisach BHP przy urządzeniach energetycznych i normie PN-EN 50110-1 oraz z instrukcjami zakładowymi.
2. **L13** – warunek `Ib ≤ In ≤ Iz` opisuje tylko część koordynacji przeciążeniowej; sprawdzić, czy lekcja powinna
   wspomnieć o drugim warunku z PN-HD 60364-4-43 (dotyczącym prądu zadziałania zabezpieczenia).
3. **L14** – zakres i kolejność prób odbiorczych/okresowych porównać z PN-HD 60364-6; lekcja nie podaje terminów badań okresowych.
4. **L15** – sformułowanie „RCD mierzy sumę prądów” (precyzyjniej: sumę wektorową/różnicę prądów); typy AC/A/F/B
   i ich zastosowania porównać z aktualną normą i dokumentacją producentów.
5. **L18** – typy/klasy SPD i koordynację porównać z PN-HD 60364-5-534 i serią PN-EN 62305.
6. **L19** – wymagania dla instalacji PV (strona DC, oznakowanie, odłączanie) porównać z PN-HD 60364-7-712.
7. **L20** – pojęcia „polecenie”, „dopuszczenie”, „prace szczególnie niebezpieczne” oraz rola osoby dozoru porównać
   z aktualnym rozporządzeniem w sprawie BHP przy urządzeniach energetycznych i rozporządzeniem w sprawie stwierdzania
   posiadania kwalifikacji (zakres E/D).
8. **L21** – kolejność postępowania porównać z aktualnymi wytycznymi resuscytacji (ERC / Polska Rada Resuscytacji).
9. **Zakres E/D** – żadna lekcja nie ma pola `scope`. Kandydaci do decyzji eksperta: L20 (dozór), L11 (dokumentacja/odpowiedzialność dozoru);
   pozostałe prawdopodobnie E i D. Aplikacja nie zgaduje – pokazuje „E/D – do weryfikacji”.
10. **„Sprawdź wiedzę”** – każda nowa lekcja zawiera parę pytanie/odpowiedź w treści. Mogą zostać przeniesione dosłownie
    jako pozycje `oral_question` (egzamin ustny), jeśli taka będzie decyzja – nie zrobiono tego automatycznie.
