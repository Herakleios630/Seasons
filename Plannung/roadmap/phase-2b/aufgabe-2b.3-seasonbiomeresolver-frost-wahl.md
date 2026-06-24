---
title: "Arbeitsauftrag: SeasonBiomeResolver – Frost-Biom wählen"
quelle: "roadmap.md → Phase 2b, Sprint 2b.3"
related-roadmap: "Plannung/roadmap.md#phase-2b-frost-system"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: SeasonBiomeResolver – Frost-Biom wählen

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`SeasonBiomeResolver.resolveBiome()` so erweitern, dass bei Temperaturen unter `freeze-threshold` das Frost-Biom (`seasons:frost_<biomeKey>`) statt des normalen Saison-Bioms gewählt wird.

## Aktuelles Ergebnis
- `SeasonBiomeResolver.resolveBiome()` wählt aktuell das Saison-Biom basierend auf `season` und `transitionStep`
- Signatur: `resolveBiome(Biome originalBiome, Season season, int transitionStep, ...)`
- Temperatur wird NICHT in die Entscheidung einbezogen
- Frost-Biome existieren noch nicht im Generator (kommt aus 2b.2)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/SeasonBiomeResolver.java` | 🔄 `resolveBiome()` um Temperatur-Parameter + Frost-Logik erweitern |
| `src/main/java/de/ajsch/seasons/config/FrostConfig.java` | 📖 Gelesen: `getFreezeThreshold()`, `isFrostAllowedInBiome()` |
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofCoordinator.java` | 🔄 Aufruf von `resolveBiome()` um Temperatur ergänzen |

## Erbetene Hilfe
1. `SeasonBiomeResolver.resolveBiome()` Signatur prüfen und um `double temperature` erweitern
2. Frost-Logik EINBAUEN VOR der normalen Saison-Biom-Wahl:
   - Wenn `frostConfig.isEnabled() && temperature < frostConfig.getFreezeThreshold()`
   - UND `frostConfig.isFrostAllowedInBiome(originalBiome)` → `true`
   - DANN: `NamespacedKey key = new NamespacedKey("seasons", "frost_" + biomeKey); return Registry.BIOME.get(key);`
3. `BiomeSpoofCoordinator.run()` – Aufruf von `resolveBiome()` um Temperatur ergänzen:
   - Temperatur via `TemperatureCalculator.getTemperature(location)` berechnen (falls nicht bereits vorhanden)
   - An `resolveBiome()` übergeben
4. Build: `.\gradlew.bat compileJava`
5. Bei Compile-Fehlern korrigieren, dann `.\gradlew.bat shadowJar -x test`

## Done‑Definition
- [x] `resolveBiome()` prüft Temperatur und wählt Frost-Biom bei `temp < freezeThreshold`
- [x] `excluded-biomes` werden respektiert (kein Frost in Wüste, Nether, End etc.)
- [x] Bei `temp >= freezeThreshold` oder excluded-Biom → normales Saison-Biom (kein Regression)
- [x] `BiomeSpoofCoordinator` übergibt Temperatur korrekt
- [x] Build erfolgreich

## Umsetzung (2025-07-09)

### Geänderte Dateien
| Datei | Änderung |
|---|---|
| `SeasonBiomeResolver.java` | `FrostConfig`-Feld + Konstruktor-Parameter; `resolveTargetBiome()` delegiert an temperatur-bewusste Overload-Variante; neue `resolveTargetBiome(chunk,season,variant,temperature)` mit Frost-Prüfung VOR Saison-Biom-Wahl; `resolveFrostBiome()` Helper |
| `BiomeSpoofCoordinator.java` | `TemperatureCalculator`-Feld + Konstruktor-Parameter; in `runInternal()`: `tempCalc.calculate(dayOfYear, sampleBiome)` übergibt Temperatur an `resolveTargetBiome()` |
| `SeasonsPlugin.java` | `FrostConfig`-Instantiierung vorgezogen; `SeasonBiomeResolver`-Konstruktor bekommt `FrostConfig`; `BiomeSpoofCoordinator`-Konstruktor bekommt `TemperatureCalculator`; doppelte `FrostConfig`-Erzeugung entfernt |

### Logik-Flow
```
BiomeSpoofCoordinator.runInternal()
  → tempCalc.calculate(dayOfYear, sampleBiome)
    → resolveTargetBiome(chunk, season, variant, temperature)
      → if !NaN && frost.enabled && temp < freezeThreshold && !excluded
          → resolveFrostBiome(biomeKey) → seasons:frost_<biomeKey> ✓
      → else
          → resolveTargetBiome(chunk, season, variant) → normales Saison-Biom
```