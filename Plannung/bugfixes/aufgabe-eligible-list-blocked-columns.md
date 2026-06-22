---
title: "Arbeitsauftrag: Eligible-Liste auf bebaubare Spalten einschränken – blockedColumns aktivieren"
quelle: "Ad-hoc – Analyse Gras-Blöcke ohne Schnee, obwohl unbebaut (Chat-Verlauf 2025-07-11)"
created: "2025-07-11"
status: done
---

# Arbeitsauftrag: Eligible-Liste auf bebaubare Spalten einschränken

**Quelle:** Ad-hoc – Analyse kahler Gras-Blöcke trotz freier Oberfläche, Chat-Verlauf 2025-07-11

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag

Der Placer soll nur noch Spalten abarbeiten, die der Scanner zuvor als `snowCapable` erkannt hat.
Das vorhandene, aber nie genutzte `blockedColumns`-BitSet in `ChunkCacheEntry` soll mit Leben gefüllt werden:
Permanent blockierte Spalten (Torch, Slab, Teppich auf Ground) aus der Eligible-Liste ausschließen
und das `isColumnPlaceable()` im Scanner mit dem im Placer synchronisieren (Pflanzen-Fallback).

**Keine neue Scan-Phase einführen** – der existierende `scanChunkColumns`-Lauf soll genutzt werden.
Der Placer arbeitet danach strikt den Cache ab: nur Spalten mit `snowCapable=true` und `blocked=false`
kommen in die `eligible`-Liste.

## Aktuelles Ergebnis
- **Placement läuft grundsätzlich**, aber mit gravierendem Durchsatz-Verlust
- `blockedColumns`-BitSet ist deklariert, wird nirgends befüllt, nirgends ausgewertet
- Eligible-Liste umfasst **alle** Spalten mit `height==0` (inkl. Wasser, Fackel, Stufe, …)
- Permanente Hindernisse blockieren 5/8 Versuchen pro Chunk-Durchlauf → kahle Gras-Blöcke bleiben
- Scanner-`isColumnPlaceable()` hat keinen Pflanzen-Fallback (inkonsistent mit Placer)
- `snowCapable` wird korrekt gezählt, aber vom Placer ignoriert

## Ursachenverdacht (bestätigt)
1. Scanner markiert blockierte Spalten nicht im Cache → Placer kann sie nicht ausschließen
2. Der `blockedColumns`-Mechanismus wurde konzipiert aber nie umgesetzt
3. `Scanner.isColumnPlaceable()` ≠ `SnowPlacer.isColumnPlaceable()` → inkonsistente Zählung
4. Performance: jedes Mal voller Scan der Eligible-Liste mit garantierter Fehlwiederholung

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | `blockedColumns`-BitSet (schon da) – ggf. Hilfsmethoden |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheManager.java` | `scanChunkColumns()`: `blockedColumns.set(idx)`, `isColumnPlaceable()` um Pflanzen ergänzen |
| `src/main/java/de/ajsch/seasons/weather/SnowPlacer.java` | `processChunk()`: eligible nur noch `!blocked && snowCapable==true`-Spalten |
| `src/main/resources/config.yml` | nur falls `blocked-retry-ticks` o.ä. als Config geplant (vermutlich unnötig) |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheStore.java` | `blockedColumns` in JSON persistieren (Base64-BitSet) |

## Erbetene Hilfe

1. **`isColumnPlaceable()` im Scanner vereinheitlichen** – gleiche Logik wie im Placer (inkl. `isReplaceablePlant()`)
2. **`blockedColumns` in `scanChunkColumns()` füllen** – wenn `isSnowCapable(ground)` aber `!isColumnPlaceable(aboveGround)`, dann `blockedColumns.set(idx)`
3. **`processChunk()` umbauen** – Eligible-Liste filtert:
   ```
   if (cache.pluginSnowHeight[idx] == 0 && cache.naturalSnowHeight[idx] == 0
       && !cache.blockedColumns.get(idx) && /* alternativ: cache.isColumnSnowCapable(idx) */ )
   ```
   (zusätzliche Hilfsmethode `ChunkCacheEntry.isColumnSnowCapable(idx)` falls nötig)
4. **`ChunkCacheStore` erweitern** – `blockedColumns` in JSON persistieren (Base64-kodiert wie die Height-Arrays)
5. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
6. **Deployment (nur posten):**
   ```powershell
   scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
   ```
   (Cache-Version wird für blockedColumns-Änderung inkrementiert → frischer Cache)
   ```powershell
   ssh mc@10.0.0.86 "sudo systemctl restart crafty"
   ```
7. **Funktionstest:**
   - `/season set Winter` → SnowCovered steigt viel schneller als vorher
   - Keine permanenten `blocked=N`-Meldungen mehr in den Placer-Logs
   - Auch Chunks mit vielen Fackeln werden sauber gesättigt (soweit bebaubar)
   - `blockedColumns` in `chunk_cache.json` sichtbar

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