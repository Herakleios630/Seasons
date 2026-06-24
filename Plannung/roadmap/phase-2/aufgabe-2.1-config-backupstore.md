---
title: "Arbeitsauftrag: Phase 2.1 – Config & Datenmodell + BiomeBackupStore"
quelle: "roadmap.md → Phase 2, Sprints 2.1 + 2.2"
related-roadmap: "roadmap.md → Phase 2"
created: "2025-02-05"
status: done
---

# Arbeitsauftrag: Phase 2.1 – Config & Datenmodell + BiomeBackupStore

**Quelle:** roadmap.md → Phase 2, Sprints 2.1 + 2.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die Grundlagen für das Biome-Spoofing-System schaffen: Config-Datei `biome_spoof.yml` erstellen, die Datenmodell-Klassen `SpoofMode` und `BiomeFamily` implementieren, den `BiomeBackupStore` für Crash-sichere Persistenz der Original-Biome bauen, und `ConfigManager` für die neue Config erweitern.

## Aktuelles Ergebnis
- Phase 2-PRE ist abgeschlossen – alle alten NMS/visual-Artefakte sind entfernt
- `ConfigManager` existiert und lädt bereits `config.yml`, `precipitation_categories.yml`
- Gson ist als Abhängigkeit im Projekt verfügbar
- Keine `visual/`-Package-Struktur mehr vorhanden (muss neu angelegt werden)

## Ursachenverdacht
Nicht zutreffend – dies ist ein neues Feature.

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/SpoofMode.java` | **Neu:** Enum für Betriebsmodus (OFF, GLOBAL_RING) |
| `src/main/java/de/ajsch/seasons/visual/BiomeFamily.java` | **Neu:** Enum für Chunk-Klassifizierung (LAND, OCEAN) |
| `src/main/java/de/ajsch/seasons/visual/BiomeBackupStore.java` | **Neu:** JSON-Persistenz der Original-Biome |
| `src/main/resources/biome_spoof.yml` | **Neu:** Vollständige Config für Biome-Spoofing |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | **Edit:** biome_spoof.yml laden + Getter |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | **Edit:** BiomeBackupStore init + shutdown |

## Erbetene Hilfe

### Slice 1: Config-Datei `biome_spoof.yml`
1. `biome_spoof.yml` in `src/main/resources/` erstellen mit folgendem Inhalt:
   - `enabled: true`, `mode: GLOBAL_RING`
   - `radius_chunks: 8`, `budget_chunks_per_tick: 16`
   - `revert_on_non_winter: true`, `transition_days: 3`
   - `seasons:` Mapping für SPRING→FLOWER_FOREST, SUMMER→PLAINS, AUTUMN→WINDSWEPT_SAVANNA, WINTER→SNOWY_PLAINS
   - `oceans:` mit `enabled: true`, `keep_deep_variants: true`, Season-Mappings (SPRING→LUKEWARM_OCEAN, SUMMER→WARM_OCEAN, AUTUMN→OCEAN, WINTER→FROZEN_OCEAN)
   - `excluded_biomes:` [MUSHROOM_FIELDS, DEEP_DARK, THE_VOID]
   - `disk_backup.enabled: true`
   - `nudge.enabled: true`, `nudge.max_per_tick: 8`, `nudge.cooldown_seconds: 3`
2. Build-Check: `.\gradlew.bat compileJava` (sollte grün sein, Config wird noch nicht geladen)

### Slice 2: Datenmodelle `SpoofMode` + `BiomeFamily`
3. `SpoofMode.java` im Package `de.ajsch.seasons.visual` erstellen:
   - Enum mit Werten `OFF`, `GLOBAL_RING`
   - Statische Methode `fromString(String)` mit Fallback auf OFF
4. `BiomeFamily.java` erstellen:
   - Enum mit Werten `LAND`, `OCEAN`
   - Statische Methode `fromString(String)` mit Fallback auf LAND
5. Build: `.\gradlew.bat compileJava`

### Slice 3: `BiomeBackupStore` – Persistenz
6. `BiomeBackupStore.java` erstellen:
   - Package `de.ajsch.seasons.visual`
   - Konstruktor: `BiomeBackupStore(Path dataFolder)` – erzeugt Pfad zu `biome_backups.json`
   - Datenstruktur: `Map<String, Biome[]> backups` (Chunk-Key "cx_cz" → Biome-Array)
   - `saveFirstTouch(Chunk chunk, Biome[] originalBiomes)`: Nur speichern wenn Key noch nicht existiert
   - `loadAll()`: JSON von Platte laden (mit Gson), World-UID validieren, in Map einfügen
   - `saveAll(World world)`: Map als JSON mit World-UID serialisieren, atomar auf Platte schreiben (erst in temp-Datei, dann umbenennen)
   - `getBackup(String chunkKey)`: Backup-Array für einen Chunk-Key zurückgeben
   - `removeBackup(String chunkKey)`: Backup entfernen (bei Chunk-Unload)
   - `hasBackup(String chunkKey)`: Prüfen ob Backup existiert
   - `clear()`: Alle Backups löschen
   - JSON-Format: `{"world_uid": "...", "backups": {"12_-8": ["PLAINS", ...]}}`
   - Biome als String speichern (Biome.valueOf() zum Deserialisieren)
   - Fehlerbehandlung: falls JSON korrupt → warnen, mit leerer Map starten
7. Build: `.\gradlew.bat compileJava`

### Slice 4: `ConfigManager` erweitern
8. `ConfigManager.java` erweitern:
   - `biome_spoof.yml` laden (analog zu anderen Config-Dateien)
   - Getter für alle Werte: `isBiomeSpoofEnabled()`, `getSpoofMode()`, `getSpoofRadiusChunks()`, `getSpoofBudgetPerTick()`, `getSpoofTransitionDays()`, `getSeasonTargetBiome(Season)`, `getOceanTargetBiome(Season)`, `isOceanSpoofEnabled()`, `isKeepDeepOceanVariants()`, `getExcludedBiomes()`, `isDiskBackupEnabled()`, `isNudgeEnabled()`, `getNudgeMaxPerTick()`, `getNudgeCooldownSeconds()`
   - `getSeasonTargetBiome(Season)`: Liest `biome_spoof.seasons.<SEASON>` und gibt `Biome` via `Biome.valueOf()` zurück
   - `getOceanTargetBiome(Season)`: Analog für `biome_spoof.oceans.seasons.<SEASON>`
   - `getExcludedBiomes()`: Liest String-Liste, mappt zu `Set<Biome>`
9. `SeasonsPlugin.java`: In `onEnable()` den `BiomeBackupStore` instanziieren, `loadAll()` aufrufen. Feld speichern für spätere Phasen.
10. Build: `.\gradlew.bat compileJava shadowJar -x test` – muss grün sein

## Done‑Definition Phase 2.1
- [ ] `biome_spoof.yml` liegt in `src/main/resources/` mit vollständigem Inhalt
- [ ] `SpoofMode.java` und `BiomeFamily.java` kompilieren sauber
- [ ] `BiomeBackupStore` kann Backups speichern, laden, löschen
- [ ] `ConfigManager` lädt `biome_spoof.yml` ohne Fehler
- [ ] Build grün: `.\gradlew.bat compileJava shadowJar -x test`
- [x] Keine NMS/Reflection

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `biome_spoof.yml` mappen
- **Keine NMS/Reflection:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`
- **Sync nach jedem Slice:** `Plannung/roadmap.md` (Sprint-Status updaten)