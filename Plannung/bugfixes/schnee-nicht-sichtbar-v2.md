---
title: "Arbeitsauftrag: Schnee nicht sichtbar + keine Akkumulation – Runde 2"
quelle: "Ad-hoc – Server-Test zeigt Partikel zu niedrig, Schnee-Platten spawnen kaum (Plains: Tall Grass blockiert)"
related-roadmap: "Plannung/roadmap.md Phase 1"
created: "2025-04-14"
status: done
---

# Arbeitsauftrag: Schnee nicht sichtbar + keine Akkumulation – Runde 2

**Quelle:** Ad-hoc – Server-Test mit Build vom 2025-04-14

## Testergebnisse (Mhakari auf Server)
- `/season set Winter` → `/weather rain` → Partikel sichtbar, aber zu niedrig (Augenhöhe, nicht von oben fallend)
- **Schnee-Platten spawnen kaum bis gar nicht** in Plains-Chunks:
  - Log: "Only placed 0/3 layers in chunk -144,79 after 24 attempts. Biome: plains, temp: -0.50"
  - Log: "Only placed 1/3 layers in chunk -143,78 after 24 attempts"
- SnowAccumulator läuft, aber Platzierung scheitert massenhaft.

## Ursachenverdacht
1. **Partikel-Höhe:** `dy = random.nextDouble() * 8 + 2` → 2–10 Blöcke über Spieler. Dadurch spawnen Partikel auf Kopf-/Augenhöhe. Sie sollten 15–30 Blöcke über Spieler spawnen und dann zu Boden fallen.
2. **Schnee-Platzierung scheitert an Pflanzen:** `getHighestBlockAt` liefert den Grasblock. `getRelative(0,1,0)` ist dann Tall Grass / Short Grass / Fern, nicht AIR. Bedingung `above.getType() == Material.AIR` greift nicht → Schnee wird nie platziert.
3. **Keine umfassende Prüfung auf ersetzbare Blöcke:** Vanilla ersetzt auch kleine Pflanzen mit Schnee. Unser Code tut das nicht.

## Betroffene Schichten & Dateien
| Datei | Änderung |
|---|---|
| `src/main/resources/config.yml` | `particle-y-min`, `particle-y-max` ergänzt (✓); `snow-melt-bonemeal`, `spring-regeneration-bonemeal` (✓) |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Getter für y-min/y-max (✓); Getter für BoneMeal-Flags (✓) |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Getter für y-min/y-max (✓); BoneMeal-Flags (✓) |
| `src/main/java/de/ajsch/seasons/weather/WeatherInterceptor.java` | Partikel-Höhe aus Config (✓) |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `above`-Prüfung lockern (✓); hohe Pflanzen (DoublePlant) vollständig entfernen (✓) |
| `src/main/java/de/ajsch/seasons/listener/SnowListener.java` | **Erweitert:** BoneMeal bei vollständiger Schneeschmelze auf darunterliegendem GRASS_BLOCK (✓) |
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | **Erweitert:** Saisonwechsel → Frühling: Scan aller geladenen Chunks, BoneMeal auf Grasblöcken mit AIR darüber (✓) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | `SnowListener`- und `SeasonChangeListener`-Konstruktoren an neue Signaturen angepasst (✓) |

## ToDo
1. [x] `config.yml`: `particle-y-min: 15`, `particle-y-max: 30` ergänzen
2. [x] `ConfigManager.java`: `getParticleYMin()`, `getParticleYMax()`
3. [x] `WeatherConfig.java`: `getParticleYMin()`, `getParticleYMax()`
4. [x] `WeatherInterceptor.java`: `dy` aus Config-Werten berechnen
5. [x] `SnowAccumulator.java`: `above`-Bedingung: `(above.isEmpty() || !above.getType().isSolid()) && above.getType() != Material.WATER && above.getType() != Material.LAVA`
6. [x] `SnowAccumulator.java`: Beim Ersetzen eines Pflanzenblocks durch Schnee prüfen, ob Block darüber Teil einer DoublePlant ist. Wenn ja, oberen Teil auf AIR setzen.
7. [x] `SnowListener.java`: Bei vollständiger Schneeschmelze (`currentLayers <= meltSpeed`) BoneMeal auf darunterliegenden GRASS_BLOCK (Config-Flag `snow-melt-bonemeal`)
8. [x] `SeasonChangeListener.java`: Saisonwechsel → Frühling: Scan aller geladenen Chunks, BoneMeal auf GRASS_BLOCK-Blöcken mit AIR darüber (Config-Flag `spring-regeneration-bonemeal`)
9. [x] Build mit `.\gradlew.bat compileJava`
10. [x] Build mit `.\gradlew.bat shadowJar`
11. [ ] Deployment (Nutzer)

## Technische Randbedingungen
- **Phase 1: Kein NMS/Reflection**
- **Config-Werte nutzen:** Alle neuen Werte in config.yml
- **Deploy nur durch Nutzer**
- **BoneMeal nur auf GRASS_BLOCK (nicht auf normale Erde)**

## Deployment
1. `scp` der JAR und der config.yml auf den Server
2. Server neustarten
3. Testen: `/season set Winter` → Regen → Schneepartikel von oben fallend, Schnee auf Gras mit Pflanzen
4. Testen: `/season set Spring` → BoneMeal auf freien Grasblöcken in geladenen Chunks (Log prüfen)