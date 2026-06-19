---
title: "Arbeitsauftrag: Alte Melt-Logik aus SnowAccumulator entfernen"
quelle: "roadmap.md → Phase 1b, Sprint 1b.5"
related-roadmap: "Plannung/roadmap.md → Phase 1b"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: Alte Melt-Logik aus SnowAccumulator entfernen

**Quelle:** roadmap.md → Phase 1b, Sprint 1b.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die alte Melt-Logik aus `SnowAccumulator` entfernen, jetzt da `SnowMeltManager` alle Schmelz-Aufgaben übernimmt. Gleichzeitig `SnowAccumulator` aufräumen: `SnowAccumulator` soll NUR noch für Growth zuständig sein (Single Responsibility).

## Vorbedingungen
- Alle vorherigen Phase-1b-Sprints (1b.1–1b.4) sind abgeschlossen
- `SnowMeltManager` funktioniert eigenständig
- `SeasonsPlugin.tick()` ruft bereits korrekt Growth vs Melt auf

## Aktuelles Ergebnis
- `SnowAccumulator.java` enthält noch:
  - `meltSnow(World world)` (Zeilen ~100-115)
  - `processMeltChunk(Chunk chunk)` (Zeilen ~118-150)
  - Zähler `totalMelted`
  - `meltChunksPerTick`-Referenz
- Diese sind jetzt tot (unused) – müssen entfernt werden

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Hauptbearbeitung: `meltSnow()`, `processMeltChunk()`, Melt-Zähler entfernen |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Veraltete Getter prüfen (nicht löschen – werden evtl. noch von SnowListener gebraucht) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Prüfen, ob noch Referenzen auf `snowAccumulator.meltSnow()` existieren |

## Erbetene Hilfe

1. **`SnowAccumulator.meltSnow()` entfernen:**
   - Methode komplett löschen
   - `processMeltChunk()` komplett löschen
   - `totalMelted`-Feld entfernen
   - `meltChunksPerTick`-Referenz im Summary-Log entfernen

2. **`SnowAccumulator.start()`-Runnable bereinigen:**
   - Melt-Season-Zweig entfernt (schon in 1b.3 gemacht)
   - Summary-Log aktualisieren: nur noch `placed`, `grown`, Cache-Stats

3. **Auf ungenutzte Imports prüfen:**
   - `compileJava` muss fehlerfrei durchlaufen

4. **`WeatherConfig`-Getter prüfen:**
   - `getMeltThreshold()`, `getMeltSpeed()`, `getMeltChunksPerTick()`, `getSnowMeltBonemeal()` werden möglicherweise noch von `SnowListener` genutzt → NICHT löschen
   - Nur entfernen, wenn wirklich nirgendwo mehr referenziert

5. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Java-Dateien ≤ 400 Zeilen:** SnowAccumulator sollte nach Bereinigung deutlich unter 400 Zeilen liegen
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (Schichten-Impact, SnowAccumulator jetzt reines Growth-Modul)
- `docs/handover.md` (Status)
- `Plannung/roadmap.md` (1b.5 abhaken)