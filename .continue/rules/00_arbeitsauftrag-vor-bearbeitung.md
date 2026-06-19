---
globs: '["**/*"]'
description: Stellt sicher, dass jedes Vorhaben als strukturierte
  Arbeitsanweisung aufgesetzt wird bevor Code-Arbeit beginnt.
alwaysApply: true
---
Vor der Bearbeitung eines neuen Features, Bugfixes oder Refactorings IMMER zuerst eine konkrete Arbeitsanweisung nach der Vorlage `Plannung/_TEMPLATE.md` erstellen. Niemals direkt aus roadmap.md, ToDo-Listen, Handover-Notizen oder anderen Planungsdokumenten arbeiten.

## Ordnerstruktur für Arbeitsanweisungen

Die Arbeitsanweisung MUSS in einem zur Quelle passenden Ordner unter `Plannung/` abgelegt werden:

| Quelle | Ablageort | Dateiname |
|--------|-----------|-----------|
| roadmap.md | `Plannung/roadmap/phase-XX/` | `aufgabe-YY-kurztitel.md` |
| ToDo-Liste / Handover | `Plannung/todo/` oder `Plannung/bugfixes/` | `aufgabe-XX-kurztitel.md` |
| Ad-hoc (direkt vom Nutzer) | `Plannung/ad-hoc/` | `kurztitel.md` |

Bei roadmap-Aufgaben MUSS im Template-Feld `Quelle` die genaue Referenz stehen: `roadmap.md → Phase XX, Aufgabe YY`.

## Nur Planen, nicht Ausführen

Nach Erstellung der Arbeitsanweisung WIRD NICHT automatisch mit der Arbeit begonnen. Der Nutzer muss die Arbeitsanweisung erst prüfen und explizit grünes Licht geben (z.B. „umsetzen", „los", „go"). Erst DANN mit dem ersten Schritt der ToDo-Liste beginnen.

## Inhalt der Arbeitsanweisung

Die Arbeitsanweisung muss enthalten: Quelle (falls vorhanden), klaren Auftrag, aktuelles Ergebnis, Ursachenverdacht, betroffene Schichten/Dateien, erbetene Hilfe als ToDo-Liste. Fortschritt in der Arbeitsanweisung dokumentieren, Status (in-progress/done) aktuell halten.

## Nach jedem abgeschlossenen Slice

Folgende Dateien synchron halten: README.md (neue Features/Commands), docs/developer-guide.md (Schichten-Impact, neue Datenmodelle), docs/handover.md (Status, offene Baustellen, Prioritäten), Plannung/roadmap.md (Erledigtes abhaken, neue Punkte eintragen).