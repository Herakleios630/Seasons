---
title: "Arbeitsauftrag: Integration & Test"
quelle: "roadmap.md → Phase 1.5, Sprint 1.5.7"
related-roadmap: "roadmap.md → Phase 1.5: Snow System 2.0 – Refactoring → Sprint 1.5.7"
created: "2025-07-09"
status: done (vorläufig)
---

# Arbeitsauftrag: Integration & Test

**Quelle:** roadmap.md → Phase 1.5, Sprint 1.5.7

## Status
- **Stand:** 2025-07-09 – Code-Änderungen abgeschlossen, Build erfolgreich
- **Nächster Schritt:** Deployment & Funktionstest durch Nutzer

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Nachdem alle neuen Klassen (ChunkCacheManager, SnowPlacer, SnowGrower, SnowMelter) erstellt und SnowAccumulator verschlankt wurde, müssen die Listener und die Plugin-Hauptklasse an die neue API angepasst werden. Danach Build, Deploy und Funktionstest auf dem Server.

**Anzupassende Listener:**
1. `BlockEventListener.java` – ruft aktuell `snowAccumulator.invalidateChunk(chunk)` auf. API bleibt gleich (die Methode delegiert intern an ChunkCacheManager). Keine Änderung nötig.
2. `SeasonChangeListener.java` – ruft `snowAccumulator.clearCache()` auf. API bleibt gleich. Keine Änderung nötig.
3. `PlayerJoinListener.java` – keine SnowAccumulator-Interaktion. Keine Änderung.

**Anzupassende Plugin-Klasse:**
- `SeasonsPlugin.java` – Konstruktor-Reihenfolge und Abhängigkeiten prüfen:
  1. `ChunkCacheManager` instanziieren (braucht: plugin, clock, tempCalc, weatherConfig, chunkCacheStore – aber chunkCacheStore existiert erst später → Lösung: ChunkCacheManager braucht eine `setChunkCacheStore(ChunkCacheStore)` Methode oder der Store wird später injiziert)
  2. `SnowPlacer`, `SnowGrower`, `SnowMelter` instanziieren
  3. `SnowAccumulator` instanziieren mit allen Abhängigkeiten
  4. `ChunkCacheStore` instanziieren mit `cacheManager.getCacheMap()`
  5. `chunkCacheStore` an `ChunkCacheManager`, `SnowPlacer`, `SnowGrower`, `SnowMelter` setzen (setter injection)

**Zu entfernende Config-Werte:**
Laut snow-system-2.0-concept.md Section 6 fallen diese Werte weg:
- `weather.snow.max-natural-height`
- `weather.snow.height-per-cold`
- `weather.snow.first-snow-min-layers`
- `weather.snow.first-snow-max-layers`
- `weather.snow.max-attempts-multiplier`
- `weather.snow.max-down-search`
→ Entfernen aus `ConfigManager.java` (Getter), `WeatherConfig.java` (Wrapper) und `config.yml`

**Achtung:** `getMaxNaturalHeight()` und `getHeightPerCold()` werden noch in `getMaxSnowHeight()` verwendet (in SnowGrower und ChunkCacheManager). Diese Werte müssen erhalten bleiben, NUR die obsoleten Config-Keys oben werden entfernt. Also: `max-natural-height` als `weather.snow.growth.max-natural-height` behalten? Nein – das Konzept sagt, diese Werte fallen weg. Aber `getMaxSnowHeight` nutzt sie aktuell. Also entweder das Konzept an dieser Stelle übergehen (Werte behalten) oder die Methode vereinfachen. → Entscheidung: Die Werte bleiben vorerst. Nur wirklich ungenutzte Config-Einträge entfernen:
- `first-snow-min-layers` (nur in Phase 1 genutzt)
- `first-snow-max-layers` (nur in Phase 1 genutzt)
- `max-attempts-multiplier` (nie wirksam genutzt)
- `max-down-search` (nie wirksam genutzt)

## Aktuelles Ergebnis
Nach 1.5.1–1.5.6 sind alle neuen Klassen erstellt und SnowAccumulator ist Orchestrator. Die Listener und SeasonsPlugin müssen noch an die neue Struktur gebunden werden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Konstruktor-Abhängigkeiten anpassen |
| `src/main/java/de/ajsch/seasons/listener/BlockEventListener.java` | Prüfen ob invalidateChunk noch funktioniert |
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | Prüfen ob clearCache noch funktioniert |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Obsolete Getter entfernen |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Obsolete Getter entfernen |
| `src/main/resources/config.yml` | Obsolete Einträge entfernen |

## Erbetene Hilfe
1. [x] `SeasonsPlugin.java` anpassen: **Bereits korrekt – ChunkCacheManager wird vor SnowAccumulator instanziiert, ChunkCacheStore wird per setter injiziert, keine Änderung nötig**
2. [x] Obsolete Config-Einträge entfernen: `ConfigManager.java`, `WeatherConfig.java`, `config.yml` bereinigt
3. [x] Build: `compileJava` und `shadowJar -x test` erfolgreich
4. [ ] Deployment (nur posten, nicht selbst ausführen):
   ```powershell
   scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
   ```
   Config wurde geändert, daher auch:
   ```powershell
   scp src\main\resources\config.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/config.yml"
   ```
   ```powershell
   ssh mc@10.0.0.86 "sudo systemctl restart crafty"
   ```

5. [ ] Funktionstest:
   - Server startet sauber (keine ClassNotFound, keine NullPointer)
   - `/season debug` → Jahr läuft
   - `/season set Winter` → Schneefall prüfen
   - Schnee platziert, wächst, schmilzt korrekt
   - `/season set Fruehling` → Schnee schmilzt nur Plugin-Schnee, Vanilla-Schnee bleibt
   - Logs prüfen: `[SnowAcc] summary:` mit Cache-Hits/Misses
   - `chunk_cache.json` wird geschrieben und enthält korrekte Einträge
   - Kein Cache-Drift: Nach Schmelze + Neustart sind pluginSnowHeight und physischer Schnee synchron

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine NMS/Reflection in Phase 1**
- **Java-Dateien ≤ 400 Zeilen**
- **Build nach jeder Änderung**
- **Deployment-Befehle nur posten, nicht selbst ausführen**
- **Sync nach Abschluss:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`