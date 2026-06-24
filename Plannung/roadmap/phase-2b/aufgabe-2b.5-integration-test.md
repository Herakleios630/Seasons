---
title: "Arbeitsauftrag: Frost System – Integration & Test"
quelle: "roadmap.md → Phase 2b, Sprint 2b.5"
related-roadmap: "Plannung/roadmap.md#phase-2b-frost-system"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: Frost System – Integration & Test

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Alle Komponenten aus den Sprints 2b.1–2b.4 integrieren, finalen Build erstellen, auf den Server deployen und Funktionstest durchführen.

## Voraussetzung
- 2b.1 (FrostConfig + frost.yml) ✅
- 2b.2 (BiomeJsonGenerator erweitert) ✅
- 2b.3 (SeasonBiomeResolver erweitert) ✅
- 2b.4 (FrostEffectManager) ✅

## Aktuelles Ergebnis
- Alle Einzelkomponenten sind implementiert, aber noch nicht gemeinsam getestet
- `SeasonsPlugin` muss ggf. um Frost-Komponenten ergänzt werden (falls nicht in 2b.4 geschehen)
- `FrostConfig` muss in `ConfigManager` registriert sein

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | 🔄 Sicherstellen: FrostEffectManager + FrostConfig + BiomeJsonGenerator-Integration |
| `src/main/java/de/ajsch/seasons/visual/BiomeJsonGenerator.java` | 🔄 Sicherstellen: `generateFrostBiomes()` wird in `generate()` aufgerufen |
| `build/libs/Seasons-0.1.0-SNAPSHOT.jar` | 🆕 Finales Build-Artefakt |

## Erbetene Hilfe
1. `SeasonsPlugin.onEnable()` prüfen und ggf. ergänzen:
   - `FrostConfig` wird über `ConfigManager` geladen ✅ (aus 2b.1)
   - `FrostEffectManager` wird instanziiert und gestartet ✅ (aus 2b.4)
   - Keine fehlenden `import`-Statements

2. `BiomeJsonGenerator.generate()` prüfen:
   - Wird `generateFrostBiomes()` aufgerufen? (aus 2b.2)

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`

4. Deployment:
   a. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
   b. Geänderte Configs kopieren: `frost.yml`, ggf. `season_colors.yml`, `biome_spoof.yml`
   c. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`

5. Nach Server-Neustart:
   - `/season generate-biomes force` ausführen
   - Server erneut neustarten (Datapack wird erst nach Neustart aktiv)

6. Funktionstest:
   - `/season set winter` → Biome sollten winterlich sein
   - Nachts oder in kaltem Biom: `/season debug` → Temperatur prüfen
   - **Frost-Biom sichtbar?** Gras/Laub frostig-weiß bei Temp < 0°C?
   - **Partikel sichtbar?** `SNOWFLAKE`-Partikel um den Spieler?
   - `/season set summer` → Frost sollte verschwinden
   - Wüste besuchen → keine Frost-Partikel (excluded-biomes)

## Done‑Definition
- [x] Plugin startet sauber mit allen Frost-Komponenten
- [x] `BiomeJsonGenerator` erzeugt `frost_*.json` bei `/season generate-biomes force`
- [x] `SeasonBiomeResolver` wählt Frost-Biom bei Temperatur < `freeze-threshold`
- [x] Gras-/Laub-Farben frostig (kühles Weiß/Grau) bei Frost sichtbar
- [x] `SNOWFLAKE`-Partikel bei Frost sichtbar, Dichte konfigurierbar
- [x] Keine Frost-Effekte in `excluded-biomes`
- [x] Schnelle Tag/Nacht-Reaktion (< 40 Ticks)
- [x] Keine Konflikte mit Snow-System oder Custom-Biome-Datapack
- [x] Keine Compile-Fehler, keine Laufzeit-Exceptions im Log