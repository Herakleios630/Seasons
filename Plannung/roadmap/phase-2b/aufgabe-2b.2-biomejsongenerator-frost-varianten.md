---
title: "Arbeitsauftrag: BiomeJsonGenerator – Frost-Biome erzeugen"
quelle: "roadmap.md → Phase 2b, Sprint 2b.2"
related-roadmap: "Plannung/roadmap.md#phase-2b-frost-system"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: BiomeJsonGenerator – Frost-Biome erzeugen

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`BiomeJsonGenerator` so erweitern, dass pro `enabled_biome` aus `season_colors.yml` eine zusätzliche Frost-Variante `frost_<biome>.json` erzeugt wird.

## Aktuelles Ergebnis
- `BiomeJsonGenerator` erzeugt bereits Saison-Sub-Varianten (frühling, sommer, herbst, winter mit Zwischenstufen)
- `FrostConfig` existiert (aus 2b.1), liefert Frost-Zielfarben
- Frost-Biome werden noch NICHT generiert

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeJsonGenerator.java` | 🔄 Erweitern: Frost-Variante pro Biom generieren |
| `src/main/java/de/ajsch/seasons/config/FrostConfig.java` | 📖 Gelesen: Frost-Farben abrufen |
| `src/main/java/de/ajsch/seasons/visual/SeasonColorConfig.java` | 📖 Gelesen: `enabled_biomes`-Liste |
| `src/main/java/de/ajsch/seasons/visual/VanillaBiomeReference.java` | 📖 Gelesen: Vanilla-JSON als Basis kopieren |

## Erbetene Hilfe
1. `BiomeJsonGenerator` um Methode `generateFrostBiomes()` erweitern
2. Für jedes Biom in `enabled_biomes`:
   - Vanilla-JSON via `VanillaBiomeReference.copyOf(biomeKey)` holen
   - `grass_color` auf `FrostConfig.getTargetGrassColor()` setzen
   - `foliage_color` auf `FrostConfig.getTargetFoliageColor()` setzen
   - Als `frost_<biomeKey>.json` in den Datapack-Ordner schreiben (z.B. `frost_forest.json`)
3. `generateFrostBiomes()` in der Haupt-`generate()`-Methode aufrufen (nach den normalen Saison-Biomen)
4. Build: `.\gradlew.bat compileJava`
5. Bei Compile-Fehlern korrigieren, dann `.\gradlew.bat shadowJar -x test`

## Done‑Definition
- [ ] `BiomeJsonGenerator` erzeugt `frost_*.json` für alle `enabled_biomes`
- [ ] Jede Frost-JSON ist eine 1:1-Kopie des Vanilla-Originals mit überschriebenen Farben
- [ ] `target-grass-color` und `target-foliage-color` aus `FrostConfig` werden korrekt verwendet
- [ ] Build erfolgreich