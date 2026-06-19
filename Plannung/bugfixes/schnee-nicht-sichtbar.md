---
title: "Arbeitsauftrag: Schnee nicht sichtbar + keine Akkumulation"
quelle: "roadmap.md → Phase 1 Done-Definition (Test gescheitert)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-04-07"
status: in-progress
slices-completed: 1-4
feedback-round: 1
---

# Arbeitsauftrag: Schnee nicht sichtbar + keine Akkumulation

**Quelle:** Ad-hoc – Server-Test zeigt: Regen stoppt, aber kein Schneefall sichtbar; Schnee bleibt nicht liegen.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
1. Sichtbarer, kontinuierlicher Schneefall (Partikel) für Spieler in CAN_FREEZE-Biomen im Winter
2. Schnee-Layer akkumulieren zuverlässig auf allen Oberflächen (auch initial blanken Blöcken)

## Aktuelles Ergebnis
- WeatherChangeEvent wird abgefangen, Regen für Spieler auf CLEAR gesetzt ✓
- spawnSnowParticles() feuert 10 Partikel – nur EINMAL beim Event ✗
- Kein Dauer-Schneefall-Effekt sichtbar ✗
- SnowAccumulator erhöht nur bereits existierende Schnee-Layer, legt keine neuen an ✗
- Oberflächen ohne Schnee bleiben kahl ✗

## Ursachenverdacht
1. **WeatherInterceptor:** `spawnSnowParticles()` wird nur im `onWeatherChange`-Event aufgerufen. Ein `WeatherChangeEvent` feuert einmal zu Beginn eines Regenschauers. Die 10 Partikel sind nach <1 Sekunde verschwunden. Es fehlt ein periodischer Scheduler (z.B. alle 2 Ticks), der für jeden Spieler in CAN_FREEZE+Winter Schnee-Partikel spawnt.
2. **SnowAccumulator:** `processChunk()` prüft `getHighestBlockAt()` auf `Material.SNOW`. Ist der höchste Block z.B. GRASS_BLOCK, wird er komplett ignoriert. Es fehlt der Fall: Block ist nicht SNOW → neuen Schnee-Layer setzen (wenn Season=WINTER, Block solide Oberseite, Temp < Freeze-Threshold).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/WeatherInterceptor.java` | Partikel-Scheduler einbauen, resetPlayerWeather bei Season=Frühling |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Neue Schnee-Layer auf Nicht-Schnee-Blöcken |
| `src/main/java/de/ajsch/seasons/listener/PlayerMoveListener.java` | ggf. ergänzen, wenn Biom-Wechsel-Logik angepasst wird |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Scheduler-Registrierung, SeasonChangeEvent-Handler registrieren |

## Erbetene Hilfe
1. **WeatherInterceptor – Partikel-Scheduler:** Einen periodischen BukkitRunnable einbauen, der alle 2–5 Ticks für jeden Spieler in Winter+CAN_FREEZE Schnee-Partikel spawnt
2. **WeatherInterceptor – resetPlayerWeather:** Bei Season-Wechsel nach WINTER → resetPlayerWeather für alle Spieler aufrufen (nicht nur bei Biom-Wechsel). Sonst bleibt `setPlayerWeather(CLEAR)` dauerhaft kleben und Regen kommt nie wieder
3. **SnowAccumulator.processChunk():** Auch Blöcke OHNE Schnee prüfen – wenn Season=WINTER, Temp < Freeze-Threshold, Block hat solide Oberseite → neuen Schnee-Layer setzen
4. **SeasonChangeEvent-Listener:** Einen Listener registrieren, der bei Season-Wechsel alle Spieler-Wetter resettet und die Partikel-Logik neu startet
5. Build mit `.\gradlew.bat compileJava`
6. Build mit `.\gradlew.bat shadowJar`
7. Deployment via SCP + `sudo systemctl restart crafty`

## Technische Randbedingungen
- **Phase 1: Kein NMS/Reflection** – nur Paper-API
- **Config-Werte nutzen:** freeze-threshold, max-natural-height, height-per-cold
- **Große Java-Dateien:** Mit `filesystem_read_text_file` lesen
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp "build\libs\Seasons-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md