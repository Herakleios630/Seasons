---
title: "Arbeitsauftrag: Snow Growth Debug – grown=0 nach Ownership-Fix"
quelle: "Ad-hoc – Log-Analyse winter.txt nach Deployment Cache-Ownership-Fix (2025-07-10)"
created: "2025-07-10"
status: in-progress
---

# Arbeitsauftrag: Snow Growth Debug – grown=0

**Quelle:** Ad-hoc – Log-Analyse `winter.txt` nach Deployment des Cache-Ownership-Fix

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag

Nach dem Cache-Ownership-Fix funktioniert die Plugin-Schnee-Attribution korrekt (alle Einträge in `chunk_cache_server.json` haben `pluginSnow ≠ 0`, `naturalSnow = 0`). Schnee wird auch platziert (`placed > 0`).

**Aber:** Der `SnowGrower` weigert sich, den Schnee zu erhöhen – `grown=0` flächendeckend über alle Chunks:

```
[SnowGrower] chunk 44,255 grown=0 stale=118 growableB4=118 | cap=118 cov=0 belowMax=256
[SnowGrower] chunk 46,256 grown=0 stale=168 growableB4=168 | cap=169 cov=0 belowMax=256
[SnowAcc] summary: placed=11087 grown=0 melted=0 | cache: 796 hits, 857 misses, 0 fullyGrown
```

## Aktuelles Ergebnis
- **Placement:** Funktioniert – Plugin-Schnee wird platziert, `pluginSnowHeight` korrekt gesetzt
- **Cache:** Attribution korrekt – kein Ownership-Verlust mehr
- **Growth:** `grown=0` immer und überall, obwohl `stale == growableB4` (jede Spalte ist growable)
- **Melting:** `melted=0` weil Winter (erwartet)

## Ursachenverdacht

1. **`SnowGrower.growSnowInChunk()` hat einen Logikfehler** bei der Berechnung der neuen Layer-Zahl: Obwohl Spalten als `growable` erkannt werden, schlägt das tatsächliche `setLayers()` fehl (z.B. `newLayers` bleibt identisch mit aktuellen Layers wegen falscher Berechnung).

2. **`maxSnowHeight` ist zu niedrig:** Wenn `getMaxSnowHeight()` für die gegebene Temperatur bereits den aktuellen Layer-Wert zurückgibt (z.B. 1), ist kein Wachstum möglich. Unwahrscheinlich bei `tempLevelMin/Max = -5`, aber möglich durch falsche Config-Werte (`max-natural-height` / `height-per-cold`).

3. **Race-Condition zwischen Scanner und Grower:** Der Scanner setzt `pluginSnowHeight`, aber der Grower liest aus dem Cache. Wenn der Cache-Eintrag zwischen Scanner und Grower aktualisiert/überschrieben wird, könnten die Höhen nicht korrekt sein.

4. **`growableB4` zählt nur Spalten VOR Wachstum, aber `stale` zählt alle:** Der Grower prüft vielleicht eine Bedingung, die für ALLE Spalten false ergibt (z.B. `pluginSnowHeight[idx] > 0` im Cache, aber der Code checkt auf `naturalSnowHeight`).

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowGrower.java` | Hauptverdächtiger – `growSnowInChunk()` analysieren |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheManager.java` | `scanChunkColumns()` – setzt `pluginSnowHeight`, was der Grower liest |
| `src/main/java/de/ajsch/seasons/weather/SnowPlacer.java` | `processChunk()` – prüfen ob `pluginSnowHeight` korrekt gesetzt wird |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | `getMaxNaturalHeight()` / `getHeightPerCold()` – Config-Werte prüfen |
| `src/main/resources/config.yml` | `max-natural-height` / `height-per-cold` – falls zu niedrig |

## Erbetene Hilfe

1. **`SnowGrower.java` lesen und `growSnowInChunk()` vollständig analysieren**
   - Finden des exakten Grundes, warum `grown` immer 0 bleibt
   - Prüfen, wie `newLayers` berechnet wird und ob `setLayers()` tatsächlich aufgerufen wird
   - Prüfen, ob der Grower `pluginSnowHeight` oder `naturalSnowHeight` aus dem Cache liest

2. **`WeatherConfig.java` & `config.yml`: `max-natural-height` und `height-per-cold` prüfen**
   - Sind die Defaults so, dass Wachstum möglich ist (z.B. `max-natural-height: 1`, `height-per-cold: 1`)?
   - Wird `getMaxSnowHeight()` mit korrekter Temperatur aufgerufen?

3. **Fix implementieren**

4. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`

5. **Deployment (nur posten, nicht selbst ausführen):**
   ```powershell
   scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
   ```
   Falls config.yml geändert:
   ```powershell
   scp src\main\resources\config.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/config.yml"
   ```
   ```powershell
   ssh mc@10.0.0.86 "sudo systemctl restart crafty"
   ```

6. **Funktionstest:**
   - `/season set Winter` → Schnee platziert
   - Nach Sättigung (alle Spalten voll) → Wachstum: Schnee steigt auf Layer 2, 3, ... bis Max
   - Log zeigt `grown > 0`

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