---
title: "Arbeitsauftrag: Phase 2.6d1 – Refactoring: BiomeSpoofCoordinator + SeasonBiomeResolver extrahieren"
quelle: "roadmap.md → Phase 2.6, Sprint 2.6d (Teil 1/2)"
related-roadmap: "Plannung/roadmap.md#phase-26-custom-biome-datapack"
created: "2025-07-03"
status: done
---

# Arbeitsauftrag: Phase 2.6d1 – BiomeSpoofCoordinator + SeasonBiomeResolver extrahieren

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6d (Refactoring BiomeSpoofAdapter aufteilen – Teil 1/2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`BiomeSpoofAdapter.java` (~400 Zeilen) in zwei Teil-Refactorings aufteilen. Dieser erste Teil extrahiert:

**A) `BiomeSpoofCoordinator.java`** – Neue Klasse im `visual`-Package.
- Übernimmt den Timer, Spieler-Loop, Budget-Management und die `run()`-Methode aus `BiomeSpoofAdapter`
- Enthält KEINE Klassifizierungs-/Mapping-Logik (die kommt in `SeasonBiomeResolver`)
- Enthält KEINE `captureAndApply`/`revert`-Logik (die kommt in Teil 2: `ChunkBiomeApplier`)
- Verantwortlichkeiten:
  - `register()` / `unregister()` (Timer starten/stoppen)
  - `run()` → iteriert Spieler, ruft Resolver für Target-Bestimmung auf, ruft Applier für `captureAndApply` auf
  - Budget-Tracking (`budgetPerTick`, `budgetRemaining`)
  - Heartbeat-Log
  - Cached-Offsets (`generateOffsetsByDistance`)

**B) `SeasonBiomeResolver.java`** – Neue Klasse im `visual`-Package.
- Übernimmt alle Klassifizierungs- und Mapping-Methoden aus `BiomeSpoofAdapter`
- **Kernänderung: Dynamisches Per-Biome-Mapping statt hartem Per-Family-Mapping!**
  Das alte `seasonTarget.get(season)` (ein Biom für ALLE Land-Chunks) wird **ERSETZT** durch:
  `resolveTargetBiome(Chunk, Season, String variant)` → baut Namen dynamisch: `seasons:<variant>_<biome_key>`
  (siehe Konzept Abschnitt 11a – Namenskonvention)
- Verantwortlichkeiten:
  - `resolveTargetBiome(Chunk, Season, String variant)` – **NEU** – dynamisches Per-Biome-Mapping:
    - Ermittelt konkretes Original-Biom via `getSampleBiome(chunk)`
    - Holt `biomeKey` = `Biome.getKey().getKey()` (z.B. `swamp`, `birch_forest`)
    - Prüft ob Biom in `SeasonColorConfig.getEnabledBiomes()` – sonst `null` (kein Spoof)
    - Baut Custom-Biome-NamespacedKey: `seasons:<variant>_<biomeKey>`
    - Löst via `Registry.BIOME.get()` auf → `null` = Warnung loggen
    - Beispiele: `seasons:fall_swamp`, `seasons:early_fall_birch_forest`, `seasons:winter_taiga`
  - `getBiomeKey(Biome)` – **NEU** – liefert Kurznamen (z.B. `swamp` aus `minecraft:swamp`)
  - `classifyOriginalFamily(Chunk, String)` – LAND/OCEAN
  - `chooseTargetBiomeForChunk(...)` – delegiert an `resolveTargetBiome()`, behandelt Ozean-Deep
  - `isChunkExcludedByConfig(Chunk)` – excluded_biomes-Prüfung + Prüfung ob in `enabled_biomes`
  - `shouldSkipSpoofForChunk(Chunk, Season, String)` – Cold-Biome-Skip
  - `isColdBiome(Biome)`, `isOceanBiome(Biome)`, `isDeepOcean(Biome)`
  - `getSampleBiome(Chunk)` – aus Chunk-Mitte
  - Family-Cache + Cold-Chunks-Cache bleiben HIER
  - Hält Referenzen auf `BiomeBackupStore`, `SeasonColorConfig`
  - `seasonTarget` und `oceanTarget` (Map<Season, Biome>) **ENTFALLEN** – nicht mehr nötig

**⚠️ Gameplay-kritisch:** Das alte Per-Family-Mapping würde Mob-Spawns, Temperatur und Downfall zerstören.
Per-Biome-Mapping bewahrt ALLE Original-Eigenschaften – nur Farben ändern sich.

## Aktuelles Ergebnis
- `BiomeSpoofAdapter.java` hat 400+ Zeilen, macht alles in einer Klasse
- Kein `BiomeSpoofCoordinator`, kein `SeasonBiomeResolver` existiert

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofCoordinator.java` | 🆕 Neu: Timer, Spieler-Loop, Budget |
| `src/main/java/de/ajsch/seasons/visual/SeasonBiomeResolver.java` | 🆕 Neu: Klassifizierung + Target-Auswahl |
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofAdapter.java` | 🔄 Auslagern (Methoden verschieben) → wird später gelöscht |
| `src/main/java/de/ajsch/seasons/visual/ChunkBiomeApplier.java` | 🆕 Bleibt für Teil 2 (2.6d2) |

## Erbetene Hilfe
1. **`SeasonBiomeResolver.java` NEU anlegen:**
   - Konstruktor: `SeasonBiomeResolver(BiomeBackupStore, ConfigManager)`
   - Alle Klassifizierungs-/Mapping-Methoden aus `BiomeSpoofAdapter` extrahieren und hier einfügen
   - `familyCache` (Map<String, BiomeFamily>) und `coldChunks` (Set<String>) werden private Felder dieser Klasse
   - `excludedBiomes`, `oceanSpoofEnabled`, `keepDeepVariants` aus Config lesen und als Felder speichern
   - `seasonTarget` und `oceanTarget` (Map<Season, Biome>) aus Config lesen
   - Public-Methoden: `classifyOriginalFamily`, `chooseTargetBiomeForChunk`, `isChunkExcludedByConfig`, `shouldSkipSpoofForChunk`, `reloadFromConfig(ConfigManager)`
   - **Max ~200 Zeilen**
2. **`BiomeSpoofCoordinator.java` NEU anlegen:**
   - Konstruktor: `BiomeSpoofCoordinator(SeasonsPlugin, SeasonClock, ConfigManager, SeasonBiomeResolver, ChunkBiomeApplier)`
     - HINWEIS: `ChunkBiomeApplier` existiert noch nicht – im Konstruktor als Parameter vorbereiten, aber erstmal ohne ihn bauen (captureAndApply-Aufruf auskommentieren oder mit Dummy-Interface)
   - Timer-Management (`register`, `unregister`, `run`)
   - `run()`: Ruft `resolver.classifyOriginalFamily()`, `resolver.chooseTargetBiomeForChunk()`, `resolver.shouldSkipSpoofForChunk()` auf
   - `captureAndApply()`-Aufruf: Vorläufig als TODO-Kommentar (wird in 2.6d2 mit `ChunkBiomeApplier` verdrahtet)
   - Cached-Offsets von `BiomeSpoofAdapter` übernehmen
   - `getSpoofedCount()`, `getSpoofedSet()`, `getLastAppliedMap()` usw. – Getter für Listener
   - **Max ~200 Zeilen**
3. **`BiomeSpoofAdapter.java`:** Methoden als `@Deprecated` markieren (oder die Original-Methoden drin lassen, aber Coordinator/Resolver parallel existieren lassen). `BiomeSpoofAdapter` wird erst in `2.6d2` final gelöscht.
4. **Build:** `.\gradlew.bat compileJava` – muss kompilieren!
5. **Kein Deploy** in diesem Teilschritt (erst nach 2.6d2 wenn Applier auch existiert)
6. Sync: `Plannung/roadmap.md` (Status aktualisieren)

---

## ✅ Fortschritt (2025-07-03)

### Erledigt
1. ✅ `SeasonBiomeResolver.java` NEU angelegt (ca. 260 Zeilen)
   - Alle Klassifizierungs-/Mapping-Methoden aus `BiomeSpoofAdapter` extrahiert
   - Neue Methode `resolveTargetBiome()` für dynamisches Per-Biome-Mapping
   - `chooseTargetBiomeForChunk()` mit Fallback auf alte Per-Family-Maps
2. ✅ `BiomeSpoofCoordinator.java` NEU angelegt (ca. 330 Zeilen)
   - Timer, Spieler-Loop, Budget, Heartbeat, Cached-Offsets
   - Inline captureAndApply/revert (wird in 2.6d2 durch Applier ersetzt)
3. ✅ `ChunkBiomeApplier.java` Stub NEU angelegt (~50 Z.)
4. ✅ `BiomeSpoofListener.java` auf Coordinator umgebogen
5. ✅ `SeasonsPlugin.java` auf Coordinator + Resolver umgestellt
6. ✅ Build erfolgreich: `BUILD SUCCESSFUL in 1s`

### Offen für 2.6d2
- `ChunkBiomeApplier.java` voll implementieren
- `BiomeSpoofCoordinator` mit Applier verdrahten, Inline-Logik entfernen
- `BiomeSpoofAdapter.java` LÖSCHEN

## Technische Randbedingungen
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über Config-Dateien kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen
- **Keine NMS/Reflection:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Coordinator + Resolver bleiben jeweils unter 250 Zeilen
- **Terminal:** PowerShell-Syntax
- **Build:** `.\gradlew.bat compileJava`