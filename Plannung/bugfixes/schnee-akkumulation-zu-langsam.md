---
title: "Arbeitsauftrag: Schnee-Akkumulation zu langsam – neue Strategie"
quelle: "Ad-hoc – Server-Test Feedback Mhakari"
related-roadmap: "Plannung/bugfixes/schnee-nicht-sichtbar-v2.md"
created: "2025-04-14"
status: in-progress
---

# Arbeitsauftrag: Schnee-Akkumulation zu langsam – neue Strategie

**Quelle:** Ad-hoc – Server-Test Feedback Mhakari nach Build schnee-nicht-sichtbar-v2

## Projektrahmen
- **Projekt:** Minecraft Paper Plugin "Seasons"
- **Quellsprache:** Java 21
- **Build-Tool:** Gradle (Kotlin DSL)

## Auftrag
Schnee-Akkumulation ist viel zu langsam. Auch mit tick rate 200–500 verteilt sich Schnee nicht schnell genug. Der Nutzer wünscht:
- Innerhalb von 1–2 Minecraft-Tagen soll die Landschaft sichtbar weiß sein
- Erst flächendeckend 1 Layer Schnee, dann 2. und 3. Layer
- Bei extremer Kälte mehr Layer
- Zuerst neue Platten verteilen (Flächenbildung), dann vorhandene verdicken
- **Pflanzen-Verdrängung (aus v2 aktiv halten):** Gras, Farne, kleine Blumen werden durch Schnee ersetzt. DoublePlants (Tall Grass, Sunflower, etc.) werden vollständig entfernt (beide Hälften). Im Frühling wachsen sie per BoneMeal nach (bereits implementiert in SnowListener + SeasonChangeListener).

## Aktuelles Ergebnis
- `scanInterval = 600` ticks (30 Sekunden) → viel zu langsam
- `maxChunksPerTick = 16`, `layersPerScan = 3`, `maxAttempts = 24`
- Reine Zufallsplatzierung ohne Strategie
- Viele Fehlversuche in Chunks mit Wasser/Pflanzen
- Schnee bildet keine geschlossenen Flächen, sondern verteilte Einzelflecken

## Ursachenverdacht
1. **Scan-Intervall zu hoch (600 ticks = 30s):** In 2 Minecraft-Tagen (40 min) läuft das nur ~80x
2. **Keine zweiphasige Strategie:** Aktuell wird nicht zwischen Erstschnee und Verdickung unterschieden
3. **Zufallsplatzierung ineffizient:** Keine Bevorzugung von Nachbar-Blöcken existierender Schneeflächen
4. **Ozean/Fluss-Chunks:** `getHighestBlockAt` liefert Wasseroberfläche, kein solider Boden → 0/3 Platzierung
5. **maxAttempts zu niedrig (24):** Für schwierige Chunks (viele Pflanzen, Wasser) reicht das nicht

## Betroffene Schichten & Dateien
| Datei | Änderung |
|---|---|
| `src/main/resources/config.yml` | `scan-interval: 100`, `first-snow-min-layers`, `first-snow-max-layers`, `max-attempts-multiplier` |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Neue Getter für First-Snow, Attempts-Multiplier |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Neue Getter weiterleiten |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | **Hauptumbau:** Zweiphasen-Strategie, Flächenwachstum, bessere Bodenfindung |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Ggf. Konstruktor-Parameter anpassen |

## Erbetene Hilfe / ToDo

### Phase A: Config & Config-Getter
1. [x] `config.yml`: `scan-interval-ticks` von 600 auf 100 senken
2. [x] `config.yml`: Neue Werte unter `weather.snow`:
   - `first-snow-min-layers: 3` (wie viele Platten bei Erstschnee mindestens)
   - `first-snow-max-layers: 5` (maximal)
   - `max-attempts-multiplier: 16` (statt festem 8)
3. [x] `ConfigManager.java`: Getter `getFirstSnowMinLayers()`, `getFirstSnowMaxLayers()`, `getMaxAttemptsMultiplier()`
4. [x] `WeatherConfig.java`: Getter weiterleiten

### Phase B: SnowAccumulator-Umbau
5. [x] **Neue Methode `countSnowInChunk(Chunk)`:** Zählt wie viele Schnee-Blöcke im Chunk existieren
6. [x] **Neue Methode `isFirstSnow(Chunk, minSnowBlocks)`:** Prüft ob Chunk weniger als `first-snow-min-layers * 10` Schneeblöcke hat
7. [x] **Neue Methode `placeFirstSnow(Chunk, targetLayers)`:** 
   - Sucht gezielt solide Blöcke mit AIR/ersetzbarem Block darüber
   - Ignoriert Wasser/Lava als `above`
   - Verwendet `getHighestBlockYAt` + prüft ob solide, sonst runtergehen bis solider Boden
   - **Pflanzen-Verdrängung:** Wenn `above` eine Pflanze ist (Gras, Farn, kleine Blume), diese durch Schnee ersetzen. Bei DoublePlants beide Hälften entfernen (`removeDoublePlantAbove` via `canPlaceSnowAbove`)
   - Platziert `targetLayers` Schnee-Blöcke (neu, nicht Layer 1 auf existierendem)
8. [x] **Neue Methode `growExistingSnow(Chunk, targetLayers)`:** 
   - Findet existierende Schnee-Blöcke
   - **Erst Nachbarn füllen (Flächenwachstum)**, erst wenn alle Nachbarn belegt sind → Layer verdicken
   - Max 2–3 Layer unter normalen Bedingungen (`max-natural-height`), mehr nur bei extremer Kälte (`height-per-cold`)
9. [x] **`processChunk` umbauen:** `isFirstSnow` → `placeFirstSnow`, sonst `growExistingSnow`
10. [x] **`maxAttempts` auf `layersPerScan * getMaxAttemptsMultiplier()` ändern**
11. [x] **Verbesserte Bodenfindung:** Wenn `getHighestBlockYAt` nicht solide ist, über `while`-Loop abwärts suchen bis solider Block gefunden (max 10 Blöcke). **Pflanze `above` automatisch mit ersetzen** (via `canPlaceSnowAbove`: `above.isEmpty() || !above.getType().isSolid()` + DoublePlant-Check)

### Phase C: Verifikation Pflanzen-Verdrängung
12. [x] Sicherstellen, dass `removeDoublePlantAbove()` in beiden neuen Methoden (`placeFirstSnow`, `growExistingSnow`) aufgerufen wird (via `canPlaceSnowAbove`)
13. [x] Sicherstellen, dass die `above`-Prüfung aus v2 (`above.isEmpty() || !above.getType().isSolid()`) korrekt in die neue Bodenfindung integriert ist (in `canPlaceSnowAbove`)
14. [x] Kein Regression: `SnowListener` (BoneMeal bei Schneeschmelze) und `SeasonChangeListener` (Frühling BoneMeal) bleiben unverändert aktiv

### Phase D: Build & Deploy
15. [x] Build mit `.\gradlew.bat compileJava`
16. [x] Build mit `.\gradlew.bat shadowJar`
17. [ ] Deployment (Nutzer führt aus)

## Technische Randbedingungen
- **Phase 1: Kein NMS/Reflection**
- **Config-Werte nutzen:** Alle numerischen Werte über config.yml steuerbar
- **Deploy nur durch Nutzer**
- **Max 400 Zeilen pro Java-Datei:** SnowAccumulator.java aktuell ~130 Zeilen, Platz für Erweiterung
- **Keine Magic Numbers:** Jeder neue Wert als Config-Eintrag

## Deployment
```powershell
scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"
scp "src\main\resources\config.yml" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/config.yml"
ssh mc@10.0.0.86 "sudo systemctl restart crafty"
```