---
title: "Arbeitsauftrag: Verdrängte Pflanzen persistent im ChunkCache speichern & Pflanzenlisten aus Config laden"
quelle: "Ad-hoc – Verlust von Pflanzen bei Server-Neustart im Winter"
created: "2025-07-11"
status: done
---

# Arbeitsauftrag: Verdrängte Pflanzen persistent im ChunkCache speichern & Pflanzenlisten aus Config laden

**Quelle:** Ad-hoc – Analyse Pflanzen-Restauration nach Winter

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag

Zwei gekoppelte Ziele:

### A – Pflanzen-Persistenz
Pflanzen, die beim Platzieren von Schnee entfernt werden, sollen **direkt im `ChunkCacheEntry`** persistent gespeichert werden – nicht nur in einer flüchtigen `List<Block>`. Im Frühling sollen die Pflanzen aus dem Cache restauriert werden können.

### B – Pflanzenlisten in Config auslagern
Die hartkodierten `EnumSet<Material>` in `SnowPlacer.java` und `ChunkCacheManager.java` sollen durch eine Config-Datei `replaceable_plants.yml` ersetzt werden. Neue Minecraft-Pflanzen sollen ohne Code-Änderung hinzufügbar sein.

## Aktuelles Ergebnis
- `SnowPlacer.tryPlaceColumn()` ruft `removePlantAt(top)` auf → Pflanze wird zu Air
- Die entfernte Position wird in `removedPlants`-Liste abgelegt (RAM, flüchtig)
- `SnowMelter` iteriert über `removedPlants` und restauriert `Material.SHORT_GRASS`
- **Kein Persistenz-Mechanismus** → nach Server-Neustart sind alle verdrängten Pflanzen verloren
- Pflanzenlisten sind **hart im Code** (zweimal dupliziert) → Regelverstoß

## Ursachenverdacht
1. `removedPlants` ist als `List<Block>` an den Tick-Zyklus gebunden, nicht an den Cache
2. Kein persistierbares Datenfeld in `ChunkCacheEntry` für verdrängte Pflanzen
3. Kein Serialisierungs-Code in `ChunkCacheStore` für diesen Datentyp
4. Keine Config-Datei für austauschbare Pflanzen – nur EnumSet-Hardcoding

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/resources/replaceable_plants.yml` | **NEU** – Config mit zwei Listen: `replaceable` und `double_plants` |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Neue Getter `getReplaceablePlants()` / `getDoublePlants()` |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Delegierende Getter |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | Neues `BitSet displacedPlants`-Feld, Hilfsmethoden |
| `src/main/java/de/ajsch/seasons/weather/SnowPlacer.java` | Pflanzen-Sets aus Config laden; `displacedPlants.set(idx)` nach `removePlantAt()` |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheManager.java` | Eigene Pflanzen-Sets entfernen, aus WeatherConfig beziehen |
| `src/main/java/de/ajsch/seasons/weather/SnowMelter.java` | Statt externer `removedPlants`-Liste aus Cache lesen; nach Restauration `displacedPlants.clear(idx)` |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `removedPlants`-Listen-Logik entfernen, Delegation anpassen |
| `src/main/java/de/ajsch/seasons/persistence/ChunkCacheStore.java` | `displacedPlants` in JSON persistieren (Base64-BitSet) |
| `src/main/resources/config.yml` | `cache.version` inkrementieren |

## Erbetene Hilfe

### Phase A – Pflanzen-Config auslagern (zuerst, unabhängig)
0.1 **`replaceable_plants.yml` erstellen** – Zwei Listen: `replaceable` (einzelne Blöcke wie GRASS, FERN…) und `double_plants` (zweiteilige wie SUNFLOWER, LILAC…)
0.2 **`ConfigManager` erweitern** – `getReplaceablePlants()` / `getDoublePlants()` via `Material.matchMaterial()`
0.3 **`WeatherConfig` erweitern** – delegierende Getter
0.4 **Pflanzen-Sets ersetzen** – `SnowPlacer` und `ChunkCacheManager` laden Sets aus Config statt hartem EnumSet

### Phase B – Pflanzen-Persistenz
1. **`ChunkCacheEntry` erweitern** – `BitSet displacedPlants` (256 bit), Hilfsmethoden `markDisplacedPlant(idx)` / `hasDisplacedPlant(idx)` / `clearDisplacedPlant(idx)`
2. **`SnowPlacer.tryPlaceColumn()` umbauen** – nach erfolgreichem `removePlantAt()`: `cache.displacedPlants.set(idx)`
3. **`SnowMelter` umbauen** – `removedPlants`-Parameter entfernen, stattdessen `displacedPlants` aus Cache auslesen; nach erfolgreicher Restauration: `cache.displacedPlants.clear(idx)`
4. **`SnowAccumulator` aufräumen** – `removedPlants`-Listen weg, `processChunk`-Signatur vereinfachen
5. **`ChunkCacheStore` erweitern** – `displacedPlants` Base64-kodiert in JSON (analog `blockedColumns`)
6. **`cache.version` in config.yml** von 2 auf 3 inkrementieren
7. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
8. **Deployment (nur posten):** SCP-JAR + YAML-Dateien + SSH-Restart
9. **Funktionstest:** Winter → Server-Neustart → Frühling → Pflanzen zurück

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API, Packet-Overrides erst ab Phase 2
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Wenn YAML-Configs geändert: zusätzlich die geänderten Config-Dateien kopieren (Ziel `plugins/Seasons/`)
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`