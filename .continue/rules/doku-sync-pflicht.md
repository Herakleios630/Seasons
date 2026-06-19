---
globs: src/main/**/*.java,src/main/resources/*.yml,docs/**/*.md,Plannung/**/*.md,README.md
description: Gilt bei jeder inhaltlichen Änderung an Code oder YAML-Konfiguration.
alwaysApply: false
---

Nach jedem abgeschlossenen Slice folgende Dateien synchron halten: README.md (neue Features/Commands), docs/developer-guide.md (Schichten-Impact, neue Datenmodelle), docs/handover.md (Status, offene Baustellen, Prioritäten), Plannung/roadmap.md (Erledigtes abhaken, neue Punkte eintragen).