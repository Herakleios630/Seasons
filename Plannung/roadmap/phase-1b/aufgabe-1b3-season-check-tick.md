---
title: "Arbeitsauftrag: Saison-Check in tick() – Winter vs Melt"
quelle: "roadmap.md → Phase 1b, Sprint 1b.3"
related-roadmap: "Plannung/roadmap.md → Phase 1b"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: Saison-Check in tick() – Winter vs Melt

**Quelle:** roadmap.md → Phase 1b, Sprint 1b.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
In `SeasonsPlugin` den Haupt-Tick so umbauen, dass:
- **Winter:** `SnowAccumulator.accumulateSnow(world)` läuft (Growth-System)
- **Frühling/Sommer/Herbst:** `SnowMeltManager.accumulateMelt(world)` läuft (Melt-System)
- Keine parallele Ausführung von Growth und Melt

## Vorbedingungen
- Sprint 1b.1: `SnowMeltManager`-Klasse existiert
- Sprint 1b.2: `processMeltChunk()` ist implementiert
- Phase 1a: `SnowAccumulator` hat Growth-Logik und shared Cache
- Aktuell läuft in `SnowAccumulator.start()` ein Runnable, das selbst Season-checkt und sowohl `accumulateSnow()` als auch `meltSnow()` aufruft. Dies muss aufgetrennt werden.

## Aktuelles Ergebnis
- `SnowAccumulator.start()` führt selbstständig Season-Check durch: `if (WINTER) accumulateSnow() else meltSnow()`
- `SnowAccumulator` enthält sowohl Growth- als auch Melt-Logik – Verantwortlichkeiten vermischt
- `SnowMeltManager` soll diese Trennung sauber machen

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Hauptbearbeitung: `SnowMeltManager` instanziieren, tick logik anpassen |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `start()`-Methode: Melt-Aufruf entfernen, NUR noch Winter-Growth |
| `src/main/java/de/ajsch/seasons/weather/SnowMeltManager.java` | `start()`-Methode hinzufügen (eigenes Runnable) |

## Erbetene Hilfe

1. **`SnowAccumulator.start()` umbauen:**
   - Season-Check entfernen – nur noch `accumulateSnow(world)` aufrufen
   - KEIN `meltSnow()` mehr (wird in 1b.5 komplett entfernt)
   - Optimal: `if (season != WINTER) return;` als Guard

2. **`SnowMeltManager.start()` implementieren:**
   - Eigenes `BukkitRunnable` starten (selber `scanInterval` wie Growth)
   - Guard: `if (!weatherConfig.isEnabled()) return;`
   - Season-Check: `if (clock.getCurrentSeason() == Season.WINTER) return;`
   - Dann `accumulateMelt(world)` aufrufen
   - Summary-Log alle `summaryIntervalScans`

3. **`SeasonsPlugin.onEnable()` anpassen:**
   - `SnowMeltManager` instanziieren (sharedCache aus `snowAccumulator.getCache()` holen)
   - `snowMeltManager.start()` aufrufen

4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine Magic Numbers:** Scan-Intervall aus Config
- **Season deterministisch:** `clock.getCurrentSeason()` nutzen, kein mutable Field
- **Keine NMS/Reflection**
- **Java-Dateien ≤ 400 Zeilen**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `README.md` (neue Klasse dokumentieren)
- `docs/developer-guide.md` (Tick-Ablauf erklären)
- `docs/handover.md` (Status)
- `Plannung/roadmap.md` (1b.3 abhaken)