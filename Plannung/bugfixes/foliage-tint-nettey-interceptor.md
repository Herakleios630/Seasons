---
title: "Arbeitsauftrag: Foliage-Tint-Netty-Interceptor – Chunk-Paket-Modifikation"
quelle: "foliage-tint-nicht-sichtbar.md – Registry-Patch funktioniert serverseitig, aber Client erhält keine BiomeSpecialEffects-Daten"
related-roadmap: "Plannung/roadmap.md → Phase 2, Sprint 2.4"
created: "2025-07-22"
status: in-progress
---

# Arbeitsauftrag: Foliage-Tint-Netty-Interceptor

**Quelle:** `Plannung/bugfixes/foliage-tint-nicht-sichtbar.md` – Alle serverseitigen Fixes (Key-Matching, Record-Konstruktor, Unsafe-Patch) sind abgeschlossen, aber Paper 1.21.5 serialisiert `BiomeSpecialEffects` **NICHT** im Chunk-Paket. Stattdessen wird nur die Biome-ID gesendet – der Registry-Patch hat keine Client-Wirkung.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5 (26.1.2)
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Ersetze den Registry-Patch-Ansatz durch einen **Netty-Pipeline-Interceptor**, der das Chunk-Daten-Paket (`LevelChunkWithLight`) auf Byte-Ebene modifiziert und die Biome-PalettedContainer mit saisonalen `BiomeSpecialEffects`-Daten (insbesondere `foliageColorOverride` + `grassColorOverride`) anreichert.

## Aktuelles Ergebnis
- `FoliageTintManager` berechnet korrekt Farben für 13 Biomes im Render-Bereich.
- `NmsAdapter_v1_21_5` patcht serverseitig die `BiomeSpecialEffects`-Objekte in der Registry erfolgreich (Keys matchen, Record-Konstruktor + Unsafe-Patch funktionieren).
- `refreshAllChunks()` sendet Chunk-Pakete an den Client, aber Paper serialisiert nur die Biome-ID, nicht die `BiomeSpecialEffects`-Daten.
- **Der Client sieht keine Farbänderung.**

## Ursachenverdacht
1. Paper 1.21.5 serialisiert `BiomeSpecialEffects` **NICHT** im Chunk-Paket-LevelChunkWithLight.
2. Der Registry-Patch (Mojang-Ansatz) funktioniert nur auf Vanilla, nicht auf Paper.
3. Andere Saison-Plugins (RealisticSeasons, AdvancedSeasons) verwenden einen Netty-Pipeline-Interceptor, um das Chunk-Paket auf Byte-Ebene zu modifizieren.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter_v1_21_5.java` | **Wird komplett umgebaut:** Registry-Patch raus, Netty-Channel-Handler rein |
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter.java` | Interface ggf. erweitern (neue Methoden für Channel-Handler) |
| `src/main/java/de/ajsch/seasons/visual/FoliageTintManager.java` | Unverändert (berechnet Farben, ruft `sendBiomeTint`/`flushTints`) |
| `src/main/resources/foliage_tints.yml` | Config (unverändert) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Ggf. Channel-Registrierung anpassen |

## Technische Strategie: Netty-Pipeline-Interceptor

### Konzept
Statt die Registry zu patchen, wird das ausgehende `LevelChunkWithLight`-Paket auf Byte-Ebene abgefangen und modifiziert. Dazu wird ein Channel-Handler in die Netty-Pipeline jedes Spielers eingehängt, der das Paket:
1. **Deserialisiert** (Paper/Mojang NBT/Network-Stream)
2. **Die Biome-PalettedContainer identifiziert** und die darin enthaltenen Biome-IDs extrahiert
3. **Ein neues `BiomeSpecialEffects`-Objekt mit saisonalen Farben** für jede Biome-ID erzeugt
4. **Die serialisierten `BiomeSpecialEffects`-Daten** in das Paket einfügt (oder das gesamte Paket neu serialisiert)
5. **Das modifizierte Paket** an den Client weiterleitet

### Alternativer Ansatz (einfacher): `ClientboundLevelChunkWithLight` via Reflection patchen
Paper's `ClientboundLevelChunkWithLight` enthält bereits eine Liste von `BiomeSpecialEffects`. Falls Paper doch `BiomeSpecialEffects` serialisiert (was wir vermuten dass es das nicht tut), könnten wir direkt die Liste modifizieren.
→ **Dieser Ansatz ist zu prüfen, bevor wir auf Byte-Level gehen.**

## Erbetene Hilfe – ToDo-Liste

### Schritt 1: Paper-Chunk-Paket analysieren
- [ ] Prüfen, ob `ClientboundLevelChunkWithLight` eine Liste von `BiomeSpecialEffects` enthält (per Reflection/Decompiler)
- [ ] Prüfen, ob diese Liste serialisiert wird (oder nur Biome-IDs)
- [ ] Falls Paper `BiomeSpecialEffects` serialisiert → direkt per Reflection patchen (einfach)
- [ ] Falls Paper nur Biome-IDs serialisiert → Netty-Byte-Level-Interceptor notwendig

### Schritt 2: Netty-Channel-Handler implementieren
- [ ] Channel-Handler-Klasse in `NmsAdapter_v1_21_5` oder separater Datei erstellen
- [ ] `ChannelInboundHandlerAdapter` (für ausgehende Pakete: `ChannelOutboundHandlerAdapter`)
- [ ] `write()`-Methode überschreiben, auf `ClientboundLevelChunkWithLight` prüfen
- [ ] Paket modifizieren: `BiomeSpecialEffects`-Daten einfügen

### Schritt 3: NmsAdapter umbauen
- [ ] Registry-Patch-Code entfernen (oder per Config deaktivierbar machen)
- [ ] `sendBiomeTint`/`flushTints` behalten – sie füllen die `pendingTints`-Map
- [ ] Channel-Handler in `onEnable` registrieren, in `onDisable` deregistrieren

### Schritt 4: BiomeSerialization verstehen
- [ ] MoJangs `BiomeSpecialEffects`-Serialisierungsformat recherchieren
- [ ] Network-Codec für `BiomeSpecialEffects` identifizieren (`FriendlyByteBuf`-Methoden)
- [ ] Manuelle Serialisierung der neuen `BiomeSpecialEffects`-Objekte implementieren

### Schritt 5: Edge Cases
- [ ] Chunk-Unload-Events (Pakete ohne `BiomeSpecialEffects`)
- [ ] Light-Update-Pakete (nur Light-Daten, keine Biome-Daten)
- [ ] Dimension-Change (Client erhält neue Biome-Daten)
- [ ] Reload/Reconnect (Handler muss überleben)

### Schritt 6: Build & Test
- [ ] `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
- [ ] Deployment: JAR kopieren, Server neustarten
- [ ] Test: `/season set fall` → visuell prüfen
- [ ] Test: `/season set winter` → visuell prüfen

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