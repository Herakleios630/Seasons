---
title: "Arbeitsauftrag: FrostConfig + frost.yml"
quelle: "roadmap.md → Phase 2b, Sprint 2b.1"
related-roadmap: "Plannung/roadmap.md#phase-2b-frost-system"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: FrostConfig + frost.yml

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`frost.yml` als Resource-Datei anlegen, `FrostConfig.java` zum Lesen und Wrappen der Werte schreiben, und die neue Config in `ConfigManager.java` registrieren.

## Aktuelles Ergebnis
- `ConfigManager` lädt bereits `config.yml`, `precipitation_categories.yml`, `biome_spoof.yml`, `season_colors.yml`, `replaceable_plants.yml`.
- `FrostConfig` existiert nicht.
- `frost.yml` existiert nicht.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/frost.yml` | 🆕 Frost-Config (wird nach `plugins/Seasons/` kopiert) |
| `src/main/java/de/ajsch/seasons/config/FrostConfig.java` | 🆕 Wrapped alle Frost-Werte |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | 🔄 `frost.yml` laden + `FrostConfig` instanziieren |

## Erbetene Hilfe
1. `frost.yml` nach Vorgabe aus `Plannung/frost-concept-phase-2b.md` Abschnitt 3 im Ressourcen-Ordner anlegen
2. `FrostConfig.java` schreiben mit folgenden Methoden:
   - `isEnabled()`, `getFreezeThreshold()`, `getFullFrostThreshold()`
   - `getTargetGrassColor()`, `getTargetFoliageColor()`
   - `isParticlesEnabled()`, `getParticleType()`, `getParticlesPerSecond()`, `getSpreadRadius()`
   - `isFrostAllowedInBiome(Biome biome)` – prüft `excluded-biomes`
3. `ConfigManager.java` erweitern:
   - `frost.yml` nach `plugins/Seasons/frost.yml` kopieren (ResourceCopier)
   - `FrostConfig` instanziieren, Getter bereitstellen
4. Build: `.\gradlew.bat compileJava`
5. Bei Compile-Fehlern korrigieren, dann `.\gradlew.bat shadowJar -x test`

## Done‑Definition
- [x] `frost.yml` existiert als Resource und wird nach `plugins/Seasons/` kopiert
- [x] `FrostConfig.java` kompiliert fehlerfrei
- [x] `ConfigManager.getFrostConfig()` liefert die Instanz
- [x] Build erfolgreich