---
title: "Arbeitsauftrag: Phase 2 Biome-Spoofing – Biome-Farben auf Client nicht sichtbar"
quelle: "Ad-hoc (Server-Log Analyse: Season-Wechsel sichtbar im Log, aber keine Laubfarben-Änderung am Client)"
related-roadmap: "Plannung/roadmap.md → Phase 2 (abgeschlossen, aber visuell wirkungslos)"
created: "2025-07-22"
status: veraltet
---

# Arbeitsauftrag: Phase 2 Biome-Spoofing – Biome-Farben auf Client nicht sichtbar

**Quelle:** Ad-hoc – Server-Log zeigt korrekte Season-Wechsel, BiomeSpoofAdapter läuft, aber Spieler sehen keine Änderung der Laub-/Grasfarben.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Paper 1.21.5 (Build 26.1.2-69-main@76d2ac7)
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Diagnose und Behebung des Problems, dass Biome-Spoofing (`world.setBiome()` + `world.refreshChunk()`) keine sichtbaren Farbänderungen auf dem Client bewirkt. Die saisonalen Biome-Wechsel müssen für Spieler sichtbar sein (Herbst = braune/orange Laubfarben, Winter = weiß/grau, Frühling = blühendes Grün).

## Aktuelles Ergebnis
### Was funktioniert:
- `BiomeSpoofAdapter` startet korrekt (Log: `Config geladen: mode=GLOBAL_RING radius=8 budget=16 oceanSpoof=true deepVariants=true excluded=3`)
- 40-Tick-Timer läuft (`40-Tick-Timer gestartet (taskId=6)`)
- `SeasonChangeEvent` wird korrekt gefeuert und `BiomeSpoofListener` revertiert Spoofs
- `SnowAccumulator`, `SnowMelter` etc. funktionieren (Schnee-Platzierung, Schmelzen sichtbar)

### Was NICHT funktioniert:
- **Keine sichtbare Änderung der Laub-/Grasfarben** beim Wechsel zwischen den Seasons
- `/season set fall` → keine herbstlichen Brauntöne sichtbar
- `/season set winter` → keine winterlichen Weiß-/Grautöne sichtbar
- `/season set spring` → kein blühendes Grün sichtbar

## Ursachenverdacht

### Hypothese 1 (Hauptverdacht): `world.setBiome()` + `world.refreshChunk()` reicht in Paper 1.21.5 NICHT aus
Paper's `world.setBiome(x, y, z, biome)` ändert das Biome **serverseitig** im `WorldChunkCache`, aber der **Client erfährt davon nur bei einem vollständigen Chunk-Re-Send**. `world.refreshChunk()` sendet möglicherweise nur ein `ClientboundLevelChunkWithLightPacket` OHNE die Biome-Daten zu aktualisieren, oder das Chunk-Paket enthält das neue Biome nicht korrekt serialisiert.

**Test:** Nach einem `/season set fall` müsste der Spieler den Chunk verlassen und neu betreten (z.B. >128 Blöcke weggehen und zurückkommen), damit ein frisches Chunk-Paket mit den neuen Biome-Daten gesendet wird. Falls DAS funktioniert, liegt es an `refreshChunk()`.

### Hypothese 2: `world.refreshChunk()` funktioniert nicht in Paper 1.21.5
Möglicherweise ist `refreshChunk()` deprecated, renamed oder hat einen Bug in dieser Paper-Version. Prüfen ob die Methode existiert und korrekt arbeitet.

### Hypothese 3: Nudge-System (BARRIER-Block) triggert keinen vollständigen Re-Render
Das aktuelle Nudge-System sendet `sendBlockChange(BARRIER)` und stellt den Original-Block 1 Tick später wieder her. Dies könnte einen Client-seitigen Chunk-Re-Request auslösen – muss getestet werden ob das tatsächlich passiert.

### Hypothese 4: Spoof wird nie effektiv angewendet, weil der Spieler nicht genug Zeit im Chunk verbringt
Aus dem Log: Spieler ist nur ~41 Sekunden online. Der 40-Tick-Timer (2s) läuft ~20 mal. Bei radius=8 (17x17=289 Chunks zu prüfen) und budget=16/Tick werden nur 16 Chunks pro Tick bearbeitet. Aber der Spieler steht an einer festen Position – der Timer sollte die nahegelegenen Chunks schnell abdecken.

Das Problem ist eher: Der Spieler sieht NIE eine Änderung, selbst nach 20+ Timer-Durchläufen.

### Hypothese 5: Paper 1.21.5 serialisiert Biome-Daten anders
Minecraft 1.21.5 verwendet möglicherweise ein anderes Chunk-Paket-Format oder Biome-Encoding, bei dem `world.setBiome()` nicht automatisch in das Paket übernommen wird.

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofAdapter.java` | **Hauptverdächtiger:** `captureAndApply()` ruft `world.setBiome()` + `world.refreshChunk()` + `nudgeViewers()` auf. Die Kombination reicht möglicherweise nicht. |
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofListener.java` | Revert-Logik bei Season-Wechsel (funktioniert laut Log korrekt) |
| `src/main/java/de/ajsch/seasons/visual/BiomeBackupStore.java` | Backup-Store (funktioniert) |
| `src/main/resources/biome_spoof.yml` | Config mit Biome-Mappings (wird geladen) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Init/Shutdown (funktioniert) |

## Erbetene Hilfe

### Phase A: Diagnose (Ursachenfindung)

1. **Paper-API recherchieren: `world.refreshChunk()` in 1.21.5**
   - Existiert die Methode in Paper 1.21.5?
   - Was macht sie genau? Sendet sie ein neues Chunk-Paket an alle Viewer?
   - Gibt es eine Alternative? (z.B. `Chunk#setForceLoaded()` dann entladen, oder `world.regenerateChunk()`)
   - Prüfe Paper-Dokumentation und JavaDoc

2. **Paper-API recherchieren: `world.setBiome()` Wirkung auf Chunk-Pakete**
   - Werden Biome-Änderungen durch `setBiome()` automatisch in das nächste Chunk-Paket serialisiert?
   - Oder muss der Chunk komplett neu generiert werden?

3. **Manuellen Test auf dem Server durchführen**
   ```
   /season set fall
   # Dann: Spieler geht >128 Blöcke weg und kommt zurück
   # → Ändern sich die Farben?
   ```
   Falls ja → `refreshChunk()` ist das Problem
   Falls nein → `setBiome()` serialisiert nicht korrekt

4. **Debug-Logging in `captureAndApply()` einbauen**
   - Nach jedem `setBiome()`-Aufruf: prüfen ob `world.getBiome(x,y,z)` tatsächlich das neue Biome zurückgibt
   - Nach `refreshChunk()`: Log-Ausgabe ob der Aufruf erfolgreich war
   - Zählen wie viele Chunks pro Timer-Durchlauf tatsächlich gespooft werden

### Phase B: Fix-Implementierung

5. **Option A: `refreshChunk()` durch Chunk-Unload/Reload ersetzen**
   ```java
   // Statt world.refreshChunk():
   chunk.unload(); // oder chunk.setForceLoaded(false)
   // Chunk wird automatisch neu geladen wenn Spieler in der Nähe
   ```
   **Nachteil:** Spieler sehen kurz eine Lücke (Chunk wird unsichtbar)

6. **Option B: NMS/Packet-basierter Chunk-Re-Send (NUR wenn Paper-API nicht ausreicht)**
   - `ClientboundLevelChunkWithLightPacket` manuell konstruieren und an Spieler senden
   - Dies wäre NMS-Code – nur als Fallback wenn Paper-API nicht funktioniert
   - Neue Klasse `visual/ChunkRefresher.java` mit NMS-Code (Adapter-Pattern wie in alter Phase 2)

7. **Option C: `world.regenerateChunk()` testen**
   - Paper-spezifische Methode? Prüfen ob verfügbar
   - Regeneriert den Chunk komplett (zerstört Spieler-Bauten!) – wahrscheinlich ungeeignet

8. **Option D: Player-Teleport als Workaround**
   - Spieler kurz teleportieren (1 Block) um Chunk-Re-Send zu triggern
   - Hacky, aber könnte funktionieren

### Phase C: Saubere Lösung

9. **Finale Implementierung der gewählten Option**
   - Saubere Fehlerbehandlung
   - Performance beachten (nicht zu viele Chunk-Pakete auf einmal)
   - Config-Eintrag für die gewählte Methode (z.B. `refresh_method: REFRESH_CHUNK | UNLOAD_RELOAD | NMS_PACKET`)

10. **Build & Test**
    - `.\gradlew.bat compileJava ; .\gradlew.bat shadowJar -x test`
    - Deployment auf Server
    - Test-Matrix: Alle 4 Seasons durchschalten, Farbänderungen visuell prüfen
    - Mit mehreren Spielern testen

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `biome_spoof.yml` Mappings steuern
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen
- **NMS/Reflection nur als letzter Ausweg:** Erst Paper-API-Lösungen ausschöpfen
- **Java-Dateien ≤ 400 Zeilen:** `BiomeSpoofAdapter.java` hat aktuell ~390 Zeilen – bei Erweiterungen ggf. Helper auslagern
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Config-Änderungen separat kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`