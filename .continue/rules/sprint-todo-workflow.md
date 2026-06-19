---
globs: "Plannung/**/*.md,docs/**/*.md,src/main/**/*.java"
description: Gilt bei jeder Arbeit an einem neuen oder laufenden Sprint.
  Definiert den kleinschrittigen Workflow mit TODO-Dateien pro Sprint.
alwaysApply: true
---

Für jeden Sprint in der Roadmap liegt eine eigene TODO-Datei unter `Plannung/sprints/X.Y.md`.

Vor Beginn der Sprint-Arbeit:
1. `Plannung/sprints/X.Y.md` lesen
2. Mit aktueller `Plannung/roadmap.md` und `docs/architecture-concept.md` abgleichen
3. Bei Abweichungen die TODO-Datei aktualisieren bevor Code geschrieben wird
4. TODO-Punkte einzeln abarbeiten und in der Datei als erledigt markieren (`[ ]` → `[x]`)

Nach Abschluss des Sprints:
- Sprint in `Plannung/roadmap.md` als erledigt abhaken (`[ ]` → `[x]`)
- `docs/developer-guide.md`, `docs/handover.md` und `README.md` synchronisieren (siehe doku-sync-pflicht)
- Nächste Sprint-TODO-Datei anlegen, falls noch nicht vorhanden