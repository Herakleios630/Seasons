---
globs: "src/main/java/**/season/*.java"
description: Gilt bei Änderungen an der Season-Kernlogik, SeasonClock oder
  Season-Enum.
alwaysApply: true
---

Die aktuelle Season wird ausschließlich deterministisch aus `world.getFullTime()` und dem gespeicherten Year-Start-Offset berechnet. Niemals als mutable Feld speichern oder zwischenspeichern, das mit der Zeit auseinanderlaufen könnte. Season-Wechsel werden als Events gefeuert, aber der Zustand selbst bleibt rein funktional ableitbar.