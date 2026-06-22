---
globs: src/main/java/de/ajsch/seasons/**/*.java
description: Verhindert, dass SnowMelter im Nicht-Winter veraltete
  ChunkCacheEntries abarbeitet und dabei natürliches Schnee-/Eis-Generierung
  zerstört.
alwaysApply: true
---

ChunkCacheEntries DÜRFEN NUR während der WINTER-Season erstellt und persistiert werden. Sobald die Season wechselt (SeasonChangeEvent), muss der gesamte Cache (ChunkCacheStore + In-Memory Cache) geleert und die JSON-Datei gelöscht werden. Nach einem Reload/Restart außerhalb des Winters darf kein alter Cache geladen werden. Der SeasonClock.getCurrentSeason()-Check muss VOR jedem Cache-Schreibzugriff und VOR dem Laden aus der JSON-Datei erfolgen.