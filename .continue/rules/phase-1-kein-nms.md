---
globs: "src/main/java/**/*.java"
description: Gilt beim Schreiben von neuem Code in Phase 1 (MVP). Schützt vor
  vorzeitigen NMS-Abhängigkeiten.
alwaysApply: true
---

Phase 1 (MVP) verwendet ausschließlich Paper-API ohne NMS/Reflection. Erst in Phase 2 (FoliageTintManager) werden Packet-Overrides eingeführt. Keine vorzeitige NMS-Nutzung in Phase 1.