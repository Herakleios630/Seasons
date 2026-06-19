---
title: "Arbeitsauftrag: Schnee-Platzierung fehlerhaft – 6 Bugs"
quelle: "Ad-hoc – Server-Test Feedback Mhakari 2025-04-14"
related-roadmap: "Plannung/bugfixes/schnee-akkumulation-zu-langsam.md"
created: "2025-04-14"
status: in-progress
---

# Arbeitsauftrag: Schnee-Platzierung fehlerhaft – 6 Bugs

**Quelle:** Ad-hoc – Server-Test Feedback Mhakari nach Deployment

## Projektrahmen
- **Projekt:** Minecraft Paper Plugin "Seasons"
- **Quellsprache:** Java 21
- **Build-Tool:** Gradle (Kotlin DSL)
- **Projektstandort:** `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Sechs konkrete Bugs in der Schnee-Platzierung beheben:
1. **Schnee taucht bei Clear-Wetter auf** – `accumulateSnow` prüft nicht `world.hasStorm()`
2. **Pflanzen (Gras, Farne, Blumen) werden nicht durch Schnee ersetzt** – `canPlaceSnowAbove` löscht die Pflanze nicht, sondern überschreibt nur mit `setType`
3. **DoublePlants werden nicht korrekt entfernt** – `removeDoublePlantAbove` entfernt nur obere Hälfte, nicht die untere (die den Block besetzt hält)
4. **`countSnowInChunk` zählt falsch** – wenn Schnee auf nicht-solidem Block liegt (Pflanze), wird nichts gezählt → Chunk bleibt ewig im "FirstSnow"-Modus
5. **`findSolidGround` scheitert zu oft** – startet auf höchstem Block (oft Pflanze, Laub, Zaun), findet in 10 Schritten keinen soliden Boden → gibt null zurück → 0/X-Platzierungen
6. **Flächenwachstum über Chunk-Grenzen nicht möglich** – `growExistingSnow` beschränkt Nachbarn strikt auf selben Chunk

## Aktuelles Ergebnis
- Log zeigt massenhaft `Only placed 0/X`-Warnungen (ca. 80% der First-Snow-Versuche scheitern)
- 500 Scans: 2111 platziert vs. 3191 Ocean-Skips (Zahlenverhältnis 2:3)
- Pflanzen werden nicht ersetzt – Gras/Blumen bleiben stehen, Schnee liegt nur auf blankem Boden
- Schnee erscheint auch bei `/weather clear` (accumulateSnow prüft keinen Wetter-Zustand)
- Keine geschlossenen Schneeflächen erkennbar

## Ursachenverdacht
1. **accumulateSnow() fehlt `world.hasStorm()`-Check** – läuft immer, egal ob Regen oder Clear
2. **`canPlaceSnowAbove` räumt Pflanzen nicht weg** – `!above.getType().isSolid()` erkennt Gras/Blumen, ruft `removeDoublePlantAbove` (nur für DoublePlants wirksam) und returned true, aber das `setType(Material.SNOW)` scheitert leise bei besetzten Blöcken
3. **`removeDoublePlantAbove(Block bottom)`** prüft `bottom.getRelative(0,1,0)` auf DoublePlant – aber `bottom` ist der SOLIDE Block (Erde), also `bottom+1` ist die UNTERE Pflanzenhälfte (TALL_GRASS), `bottom+2` wäre die Obere. Die Methode prüft `bottom+1` – das ist die UNTERE Hälfte, die NICHT im DOUBLE_PLANTS-Set ist (nur UPPER-Hälften sind dort)! Deshalb wird NIE etwas gelöscht.
4. **`countSnowInChunk`** zählt nur wenn `below.isSolid()` – aber wenn Schnee auf einer Pflanze liegt (die nicht gelöscht wurde), ist `below` nicht solid → zählt 0
5. **`findSolidGround`** hat zu kurze Suchtiefe (10) und startet auf `getHighestBlockYAt` (kann Baumkrone, Zaun, etc. sein) → oft kein solider Block in 10 Schritten
6. **`growExistingSnow`** Nachbar-Check `Math.abs(nx - cx) >= 16` verhindert Flächenwachstum über Chunk-Grenzen – Schneeflächen können sich nicht ausbreiten

## Betroffene Schichten & Dateien
| Datei | Änderung |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Hauptumbau: 6 Bugfixes |
| `src/main/resources/config.yml` | Ggf. `max-down-search` als Config-Wert |

## Erbetene Hilfe / ToDo

### Bug 1: Clear-Wetter-Check

1. [x] `accumulateSnow()`: Bei `!world.hasStorm()` → return (kein Schnee bei Clear). Wenn nicht → nur in Cold-Biomen mit Temperatur < -0.5 schneien lassen? Oder ganzen Chunk skippen. **Entscheidung:** Schnee NUR bei `world.hasStorm()` UND `world.isThundering()` ist nicht zwingend. Bei `hasStorm()==false` → kein Schnee.

### Bug 2: Pflanzen-Ersatz in canPlaceSnowAbove
2. [ ] `canPlaceSnowAbove`: Wenn `above` nicht solide und nicht leer, den Block AKTIV löschen (`above.setType(Material.AIR, false)`) BEVOR true zurückgegeben wird. Kein reines Überschreiben durch `setType(Material.SNOW)`.

### Bug 3: DoublePlant-Entfernung reparieren
3. [ ] `removeDoublePlantAbove(Block ground)`: Muss `ground.getRelative(0,2,0)` (OBERE Hälfte) prüfen UND löschen, UND `ground.getRelative(0,1,0)` (UNTERE Hälfte) löschen. Oder einfacher: Direkt in `canPlaceSnowAbove` den `above`-Block prüfen ob er Teil einer DoublePlant ist, und dann beide Hälften entfernen.

### Bug 4: countSnowInChunk reparieren
4. [ ] `countSnowInChunk`: Auch zählen wenn `below`-Block nicht solid ist (Schnee auf Nicht-Solid-Block soll trotzdem zählen). Jeder `Material.SNOW` Block zählt mindestens 1.

### Bug 5: findSolidGround robuster machen
5. [x] `findSolidGround`: Nutzt Config-Wert `max-down-search` (Default 32), Fallback auf tiefsten erreichbaren Block. (neuer Config-Wert, default 32) erhöhen. Wenn nach Durchlauf kein solider Block gefunden → NICHT null, sondern tiefsten erreichten Block nehmen (der ist solid genug für Schnee). Nur Wasser/Lava führt zu null.

6. [ ] `growExistingSnow`: Nachbar-Check entschärfen – nicht auf selben Chunk beschränken, sondern prüfen ob Nachbar-Chunk geladen ist (`world.isChunkLoaded(nx>>4, nz>>4)`). Wenn ja, Platzierung erlauben.

## Technische Randbedingungen
- **Phase 1: Kein NMS/Reflection**
- **Config-Werte nutzen:** Alle numerischen Werte über config.yml steuerbar
- **Deploy nur durch Nutzer**
- **Max 400 Zeilen pro Java-Datei**
- **Keine Magic Numbers**

## Deployment
```powershell
scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
scp src\main\resources\config.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/config.yml"
ssh mc@10.0.0.86 "sudo systemctl restart crafty"
```
```