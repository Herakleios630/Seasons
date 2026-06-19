---
title: "Arbeitsauftrag: Alte Config-Werte + Methoden aufräumen"
quelle: "roadmap.md → Phase 1a, Sprint 1a.9"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: Alte Config-Werte + Methoden aufräumen

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.9

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die durch das neue Growth-Konzept obsoleten Config-Einträge und Methoden entfernen:
- `min-neighbors-for-growth` aus Config löschen
- `getMinNeighborsForGrowth()` aus `ConfigManager` und `WeatherConfig` entfernen
- `enoughNeighborsSnowOrBlocked()` aus `SnowAccumulator` entfernen (Nachbar-Prüfung entfällt)

## Vorbedingungen
- 1a.8: Neue Config-Einträge sind vorhanden
- Growth-Logik nutzt nicht mehr die Nachbar-Prüfung (schon in 1a.4/1a.5 entfernt)

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/resources/config.yml` | `min-neighbors-for-growth` Eintrag entfernen |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | `getMinNeighborsForGrowth()` entfernen |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | `getMinNeighborsForGrowth()` entfernen |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `enoughNeighborsSnowOrBlocked()` entfernen |

## Erbetene Hilfe

1. **`config.yml` bereinigen:**
   - Zeile `min-neighbors-for-growth: 3` entfernen

2. **`ConfigManager` bereinigen:**
   - `getMinNeighborsForGrowth()` Methode löschen

3. **`WeatherConfig` bereinigen:**
   - `getMinNeighborsForGrowth()` Methode löschen

4. **`SnowAccumulator` bereinigen:**
   - `enoughNeighborsSnowOrBlocked()` Methode und alle Aufrufe löschen
   - `NEIGHBORS`-Array entfernen (wird nur von dieser Methode genutzt)

5. Build: `.\gradlew.bat compileJava` → muss fehlerfrei sein, dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (veraltete Config-Werte dokumentieren)
- `docs/handover.md`
- `Plannung/roadmap.md` (1a.9 abhaken)