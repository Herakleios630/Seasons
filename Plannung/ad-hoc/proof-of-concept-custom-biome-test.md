---
title: "Arbeitsauftrag: Proof-of-Concept Custom Biome (seasons:fall_forest) testen"
quelle: "Ad-hoc (custom-biome-concept.md)"
related-roadmap: "Plannung/custom-biome-concept.md"
created: "2025-06-23"
status: in-progress
---

# Arbeitsauftrag: Proof-of-Concept Custom Biome testen

**Quelle:** Ad-hoc – Proof-of-Concept aus `custom-biome-concept.md`

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5

## Auftrag
Custom-Biome-Datapack `seasons:fall_forest` vollständig einrichten, deployen und visuelle Änderung der Laubfarbe bei Herbstsaison prüfen.

## Aktuelles Ergebnis
- Datapack `seasons_biomes` existiert lokal und auf dem Server
- Erster visueller Test zeigte Farbänderung, aber nicht braun/orange
- `biome_spoof.yml` noch auf Vanilla-Biome (WINDSWEPT_SAVANNA) konfiguriert
- Adapter verwendet `Biome.valueOf()` – unterstützt keine Custom-Biomes

## Ursachenverdacht
1. Farbwerte in `fall_forest.json` falsch
2. `biome_spoof.yml` nutzt noch Vanilla-Biome statt `seasons:fall_forest`
3. `BiomeSpoofAdapter` kann keine Custom-Biomes setzen (braucht Registry-Lookup)

## Betroffene Dateien
| Datei | Rolle |
|---|---|
| `datapacks/seasons_biomes/.../fall_forest.json` | Custom-Biome mit korrekten RGB-Werten |
| `src/main/resources/biome_spoof.yml` | FALL → `seasons:fall_forest` |
| `src/main/java/.../BiomeSpoofAdapter.java` | Custom-Biome via Registry anwenden |
| `src/main/java/.../ConfigManager.java` | NamespacedKey-Parsing |

## Erbetene Hilfe (ToDo-Liste)

### Slice 1: Farbwerte korrigieren
1. Gras (goldbraun): `12744226`, Laub (orangebraun): `13139988`
2. `fall_forest.json` updaten, Datapack neu deployen

### Slice 2: Plugin für Custom-Biomes vorbereiten
3. `ConfigManager`: `getSeasonTargetBiome()` → Optional auch `NamespacedKey` zurückgeben
4. `BiomeSpoofAdapter.captureAndApply()`: Custom-Biome via `world.getBiome(NamespacedKey)` setzen
5. `biome_spoof.yml`: `FALL: seasons:fall_forest`

### Slice 3: Build & Deploy & Test
6. Build, JAR + Config deployen, Server neustarten
7. `/season set fall` → Laub braun/orange?
8. `/season set summer` → Vanilla-Farben zurück?

### Slice 4: Dokumentation
9. Ergebnis dokumentieren
10. `custom-biome-concept.md` und Roadmap updaten