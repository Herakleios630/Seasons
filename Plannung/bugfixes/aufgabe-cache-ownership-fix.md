---
title: "Arbeitsauftrag: Cache Ownership Fix – Plugin-Schnee wird als Vanilla erkannt"
quelle: "Ad-hoc – Log-Analyse vom 2025-07-10"
created: "2025-07-10"
status: done
---

# Arbeitsauftrag: Cache Ownership Fix

**Quelle:** Ad-hoc – Log-Analyse nach Deployment 1.5.7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag

Im Integrationstest (Winter-Log `winter2.txt`, Frühling-Log `spring.txt`, Cache-JSON `chunk_cache_server.json`) wurden zwei schwerwiegende, zusammenhängende Fehler identifiziert:

**A) Growth = 0:** Plugin-Schnee wird nach einem Cache-Clear (`clearCache()`) beim nächsten Scan als `naturalSnow` klassifiziert. Danach ist der Chunk „saturated" (256/256 naturalSnow), der Grower läuft, findet aber physisch keinen Schnee mehr (stale=256) → `grown=0`.

**B) Melting = 0:** Der `SnowMelter` schmilzt nur Spalten mit `pluginSnowHeight > 0`. Weil nach `clearCache()` ALLER Schnee als `naturalSnow` gilt, ist `totalPluginSnowColumns = 0` → `processMeltChunk()` returned sofort.

**C) Cache-Validierung durch Temperatur:** Die `tempLevelMin`/`tempLevelMax`-Prüfung in `getOrComputeCache()` ist überflüssig (Temperaturen werden live berechnet) und wird mit dem zukünftigen Tag/Nacht-Zyklus (`day-night-amplitude`) zum Performance-Killer.

## Aktuelles Ergebnis
- Schnee-Platzierung funktioniert im ersten Tick (Cache leer → `oldEntry=null` → `pluginSnow` wird korrekt gesetzt)
- Sobald `clearCache()` läuft (Season-Wechsel, auch WINTER→WINTER, oder Server-Neustart mit geladener JSON), wird der nächste Scan ALLEN physischen Schnee als `naturalSnow` klassifizieren
- Growth schlägt fehl (`stale`-Erkennung greift, aber kein Wachstum)
- Melting schlägt fehl (`totalPluginSnowColumns = 0`)

## Ursachenverdacht

### Ursache A: `clearCache()` zerstört Ownership-Information

`SeasonChangeListener` ruft bei **jedem** Season-Wechsel `snowAccumulator.clearCache()` auf – auch WINTER→WINTER. Der In-Memory-Cache wird komplett geleert. `scanChunkColumns()` hat dann keinen `oldEntry` mehr → `oldPlugin = 0` → aller physischer Schnee wird als `naturalSnow` klassifiziert.

**Der `clearCache()` ist ein Design-Fehler.** Der Cache speichert nur physische Zustände (pluginSnowHeight, naturalSnowHeight, snowCapable, snowCovered), keine season-abhängigen Daten. Temperaturen werden bei jedem Zugriff live berechnet. Ein Complete-Clear ist nie nötig.

### Ursache B: Temperatur-Validierung im Cache überflüssig

`getOrComputeCache()` prüft bei jedem Zugriff:
```java
int currentTempLevel = (int) Math.round(temp * 10);
if (currentTempLevel >= oldEntry.tempLevelMin && currentTempLevel <= oldEntry.tempLevelMax) {
    // Cache-Hit
}
```
Diese Prüfung schützt vor keinem realen Problem:
- `maxSnowHeight` wird bei Growth/Melting **live** berechnet
- Placement prüft `temp < freezeThreshold` live
- TTL (30s) + Block-Event-Invalidierung reichen als Cache-Schutz
- Mit Tag/Nacht (`day-night-amplitude: 0.15` = ±1.5 tempLevel) wird der Cache ständig invalidiert → ständige Re-Scans

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | `clearCache()`-Aufruf entfernen oder einschränken |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheManager.java` | `getOrComputeCache()`: Temperatur-Check entfernen; optional: `scanChunkColumns()` mit Temperatur-basierter Fallback-Klassifikation |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Prüfen ob `clearCache()`-Methode noch existiert und ob sie noch benötigt wird |
| `src/main/resources/config.yml` | `cache.temp-level-tolerance` ggf. obsolet → entfernen |

## Erbetene Hilfe

1. **`SeasonChangeListener.java`: `clearCache()`-Aufruf entfernen.**
   - Zeile `snowAccumulator.clearCache()` komplett entfernen
   - Prüfen ob `snowAccumulator`-Feld noch benötigt wird, sonst auch entfernen

2. **`SnowAccumulator.java`: `clearCache()`-Methode entfernen.**
   - Falls nur noch von nirgendwo aufgerufen: Methode `clearCache()` und die delegierende `invalidate`-Logik prüfen
   - `invalidateChunk(chunk)` (Einzel-Invalidierung) bleibt erhalten – sie wird von `BlockEventListener` genutzt

3. **`ChunkCacheManager.java`: Temperatur-Check aus `getOrComputeCache()` entfernen.**
   - Nur noch TTL als Invalidierungsgrund behalten
   - `tempLevelMin`/`tempLevelMax`-Felder in `ChunkCacheEntry` bleiben vorerst (werden beim Scan noch gesetzt, aber nicht mehr zur Validierung genutzt)
   - Falls `cache-temp-level-tolerance`-Config existiert: aus `ConfigManager`, `WeatherConfig` und `config.yml` entfernen

4. **(Optional, zweite Priorität) Scanner-Fallback bei der Klassifikation:**
   - In `scanChunkColumns()`: Wenn `physicalSnow > 0` und `oldPlugin == 0`, aber die Temperatur an dieser Spalte **über** `freezeThreshold` liegt (→ Vanilla-Schnee kann hier gar nicht entstehen), dann den Schnee als `pluginSnow` klassifizieren
   - Das ist ein zusätzliches Sicherheitsnetz für den Fall, dass der Cache doch einmal verloren geht (Server-Crash, Datei-Fehler)

5. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`

6. **Deployment (nur posten, nicht selbst ausführen):**
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

7. **Funktionstest:**
   - `/season set Winter` → Schnee platziert innerhalb von 1–2 Scans
   - **Ohne Neustart:** `/season set Winter` erneut → Cache bleibt erhalten → Growth funktioniert (Schnee wächst auf >1 Layer)
   - `/season set Fruehling` → Plugin-Schnee schmilzt, Vanilla-Schnee bleibt
   - Nach Schmelze + `/season set Winter` → neuer Schnee wird platziert
   - `chunk_cache.json` enthält KEINE Einträge mit `pluginSnow=0, naturalSnow=1` (außer in Biomen mit echtem Vanilla-Schnee)
   - Server-Neustart → Cache wird korrekt geladen, Plugin-Schnee bleibt Plugin-Schnee

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