# Polecenie dla Claude — import treści SEP Trener

Załączone pliki zawierają 11 oryginalnych lekcji i 10 nowych lekcji G1 w tym samym schemacie JSON: id, group, type, title, content, verified.

1. Przed importem utwórz kopię obecnej bazy.
2. Wczytaj G1_lessons_rozszerzone.json jako listę 21 lekcji. Nie importuj równocześnie pliku tylko_nowe, aby uniknąć duplikatów.
3. Waliduj unikalność id, group=G1, type=lesson i niepuste title/content.
4. Zachowaj istniejące identyfikatory 01–11 i dotychczasowe postępy użytkownika. Nowe pozycje mają identyfikatory 12–21.
5. Nie zmieniaj verified:false na true bez niezależnego audytu technicznego.
6. Sprawdź poprawne odczytywanie znaków Ω, ≤, Δ i skrótów TTS. Nie zastępuj treści odpowiedziami generowanymi automatycznie.
7. Zaimplementuj aktualizację treści z zachowaniem zakładek i ocen; pokaż raport importu.
8. Przygotuj testy importu, migracji i TTS, następnie zbuduj APK.
9. Zgłoś ewentualne rozbieżności z przepisami i normami do osobnego przeglądu, bez automatycznego potwierdzania zgodności.

Uwaga: nowe lekcje są materiałem pomocniczym, nie zastępują instrukcji eksploatacji, norm i aktualnych przepisów.
