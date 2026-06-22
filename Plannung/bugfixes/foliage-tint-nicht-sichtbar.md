---
title: "Arbeitsauftrag: Foliage-Tint wird nicht visuell sichtbar – Registry-Patch + Chunk-Reload-Analyse"
quelle: "Ad-hoc (Server-Test: /season set fall/winter zeigt keine Laubfarben-Änderung)"
related-roadmap: "Plannung/roadmap.md → Phase 2, Sprint 2.4"
created: "2025-07-22"
status: in-progress
---

# Arbeitsauftrag: Foliage-Tint wird nicht visuell sichtbar

**Quelle:** Ad-hoc – Server-Log zeigt korrekte Berechnung und `sendBiomeTint`-Aufrufe, aber Spieler sehen keine Biome-Farbänderung.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5 (26.1.2)
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Diagnose und Fix des Problems, dass saisonale Laub-/Grasfarben (`FoliageTintManager` + `NmsAdapter`) nicht beim Client ankommen – trotz korrekter Berechnung der Farben laut Log.

## Aktuelles Ergebnis
- `VisualSeasonManager` triggert bei `/season set` korrekt `FoliageTintManager.updateAllOnlinePlayers()`.
- `FoliageTintManager` berechnet für 13 Biome im Render-Bereich die Farben und ruft `nmsAdapter.sendBiomeTint()` auf.
- Im Log erscheinen die korrekten Farbwerte (z.B. `minecraft:forest -> c68a3f/c68a3f` für Herbst).
- **Der Spieler sieht keine visuelle Änderung.** Die Biomen sind weiterhin mit Vanilla-Farben dargestellt.

## Ursachenverdacht (geordnet nach Wahrscheinlichkeit)

### 1. 🔴 `sendBiomeTint` ruft pro Biome `applyGlobalBiomeOverrides` → `refreshAllChunks()` auf
   - **13 Biome = 13× `refreshAllChunks()`** – alle geladenen Chunks werden 13 Mal hintereinander unloaded + reloaded.
   - Client wird mit Kaskade von `ForgetLevelChunk` + `LevelChunkWithLight` bombardiert.
   - Wahrscheinlich bleibt am Ende der erste/leere Chunk stehen.

### 2. 🔴 Kein Debug-Logging in `applyGlobalBiomeOverrides` und `refreshAllChunks`
   - Wir sehen im Log NUR die `sendBiomeTint`-Aufrufe.
   - Ob `applyGlobalBiomeOverrides` tatsächlich etwas tut, ist unsichtbar.
   - Ob die Reflection-Felder erfolgreich gesetzt werden, ist unsichtbar.

### 3. 🟡 `refreshAllChunks()` verwendet `chunk.unload(false)` + `chunk.load(true)`
   - `unload(false)` entfernt Chunk vom Server (ohne Speichern).
   - `load(true)` lädt Chunk mit `generate=true` – könnte versuchen, Chunk NEU zu generieren, wenn Region-Datei-Eintrag fehlt.
   - Paper könnte Chunk aus internem Cache laden statt aus Region-Datei.

### 4. 🟡 Reflection schlägt still fehl
   - `BiomeSpecialEffects` ist möglicherweise ein Record (Java 16+ immutable).
   - `Field.set()` auf `final`-Feld kann mit `InaccessibleObjectException` fehlschlagen, wenn Module-System Restriktionen aktiv sind.
   - Java 25 könnte strengere Module-Enforcement haben.

### 5. 🟡 `Optional.of(tint[0])` – Typ-Problem?
   - `foliageColorOverride` Feld ist `Optional<Integer>`.
   - `Optional.of(int)` auto-boxed zu `Optional<Integer>` – korrekt.
   - Aber wenn das Feld durch ein `OptionalInt` oder einen anderen Typ ersetzt wurde, schlägt `set` fehl.

### 6. 🟢 Paper 1.21.5 serialisiert Biome-Effects NICHT im Chunk-Paket
   - Wenn Paper nur Biome-IDs (nicht `BiomeSpecialEffects`) sendet, ist der Registry-Patch wirkungslos.
   - Dann muss auf Netty-Packet-Interceptor umgestellt werden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter_v1_21_5.java` | **Kern-Baustelle:** `sendBiomeTint()`, `applyGlobalBiomeOverrides()`, `refreshAllChunks()` |
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter.java` | Interface – ggf. neue Methode `applyPendingTints()` oder `flushTints()` |
| `src/main/java/de/ajsch/seasons/visual/FoliageTintManager.java` | Rufer: sollte `flush` nach der Biome-Schleife aufrufen, nicht pro Biome |
| `src/main/resources/foliage_tints.yml` | Config (unverändert) |

## Erbetene Hilfe – ToDo-Liste

### Schritt 1: Debug-Logging einbauen ✅ DONE
- [x] In `applyGlobalBiomeOverrides`: Loggen, wie viele Einträge die Registry hat, wie viele Pending-Tints, und FÜR JEDEN gepatchten Eintrag den alten und neuen Wert loggen.
- [x] In `refreshAllChunks`: Loggen, wie viele Chunks unloaded/reloaded werden.
- [x] In `sendBiomeTint`: NICHT direkt `applyGlobalBiomeOverrides` aufrufen – nur `pendingTints.put()` und Log-Ausgabe.

### Schritt 2: Flush-Mechanismus umbauen ✅ DONE
- [x] Neue Methode `void flushTints(Player player)` in `NmsAdapter`:
  - Ruft EINMAL `applyGlobalBiomeOverrides()` auf (für alle gesammelten Pending-Tints).
  - Ruft EINMAL `refreshAllChunks()` auf.
  - Leert `pendingTints`.
- [x] `FoliageTintManager.updatePlayerTints` nach der Biome-Schleife `nmsAdapter.flushTints(player)` aufrufen.

### Schritt 3: Reflection-Erfolg verifizieren ✅ DONE
- [x] Nach `foliageColorOverrideField.set(effects, Optional.of(tint[0]))` den Wert mit `foliageColorOverrideField.get(effects)` zurücklesen und loggen (Verify im Log).

### Schritt 4: Alternativ-Ansatz prüfen
- Falls die Registry-Patch-Idee grundsätzlich nicht funktioniert (Records immutable, Paper cached, etc.): Zurück zum **Netty-Pipeline-Interceptor**-Ansatz.
- Chunk-Paket abfangen, Biome-PalettedContainer parsen, `BiomeSpecialEffects` im Datenstrom modifizieren.
- Das ist der Ansatz, den viele andere Saison-Plugins verwenden.

### Schritt 5: Build & Test
- `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
- Deployment: JAR kopieren, Server neustarten
- Test: `/season set fall` → visuell prüfen ob Birken orange, Eichen rot
- Test: `/season set winter` → visuell prüfen ob alles grau/braun
- Server-Log auf neue Debug-Ausgaben prüfen

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers**
- **Biome nie hardcoden**
- **Season deterministisch**
- **Java-Dateien ≤ 400 Zeilen**
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Config-Dateien wenn nötig
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`