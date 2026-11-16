#!/usr/bin/env python3
"""Konwertuje materialy/G1_Audiobook_Dialogi_Egzaminacyjne.txt do G1_exam.json.

Treść pytań i odpowiedzi jest przenoszona DOSŁOWNIE z pliku TXT (bez dopisywania).
Wszystkie pozycje mają verified=false. Zakres "D" jest ustawiany tylko dla pozycji
jawnie opisanych w źródle jako "Sytuacja dozoru"; pozostałe nie mają zakresu
(aplikacja pokaże "E/D – do weryfikacji").
Użycie: python3 tools/convert_g1_dialogs.py materialy/G1_Audiobook_Dialogi_Egzaminacyjne.txt out.json
"""
import json, re, sys

src, out = sys.argv[1], sys.argv[2]
text = open(src, encoding="utf-8").read()
pattern = re.compile(
    r"EGZAMINATOR:\s*(?P<q>.+?)\n\[PAUZA (?P<p>\d+) SEKUND\]\nLEKTOR:\s*(?P<a>.+?)(?:\n\n|\Z)",
    re.S,
)
items, nq, ns = [], 0, 0
for m in pattern.finditer(text):
    q, a, pause = m["q"].strip(), m["a"].strip(), int(m["p"])
    mq = re.match(r"Pytanie (\d+)\.\s*(.+)", q)
    ms = re.match(r"(Sytuacja dozoru \d+)\.\s*(.+)", q)
    if mq:
        nq += 1
        item = {"id": f"g1-question-{nq:03d}", "group": "G1", "type": "oral_question",
                "title": mq[2], "question": mq[2]}
    elif ms:
        ns += 1
        item = {"id": f"g1-situation-{ns:03d}", "group": "G1", "type": "oral_question",
                "title": f"{ms[1]}: {ms[2]}", "question": ms[2], "scope": "D"}
    else:
        raise SystemExit(f"Nierozpoznany wpis: {q!r}")
    item.update({"answer": a, "pauseSeconds": pause, "verified": False,
                 "source": "G1_Audiobook_Dialogi_Egzaminacyjne.txt"})
    items.append(item)
json.dump(items, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(f"{len(items)} pozycji ({nq} pytań, {ns} sytuacji dozoru)")
