---
title: "Arbeitsauftrag: NMS-Biome-Color-Override fehlt – Foliage-Tints nicht sichtbar"
quelle: "Ad-hoc (Server-Test Phase 2.4: /season set fall/winter zeigt keinen visuellen Unterschied)"
related-roadmap: "Plannung/roadmap.md → Phase 2, Sprint 2.4"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: NMS-Biome-Color-Override fehlt – Foliage-Tints nicht sichtbar

**Quelle:** Ad-hoc – Server-Test nach Phase 2.4: `/season set fall` und `/season set winter` zeigen keine sichtbare Laubfarben-Änderung.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Den fehlenden Biome-Color-Override im NMS-Adapter implementieren, sodass Saison-abhängige Laub-/Grasfarben für Spieler sichtbar werden. Der `FoliageTintManager` berechnet korrekt Farben und der `VisualSeasonManager` triggert Updates – aber die NMS-Schicht setzt die Farben nicht in den Chunk-Paketen.

## Aktuelles Ergebnis
- `VisualSeasonManager` startet, `FoliageTintManager.updateAllOnlinePlayers()` wird bei Season-Wechsel und periodisch aufgerufen.
- `NmsAdapter_v1_21_5.modifyBiomeColors()` ist ein **TODO-Stub** (Zeile ~176): Das Chunk-Paket wird unverändert durchgereicht. Keine Biome-Farben werden überschrieben.
- Spieler sehen Vanilla-Farben, unabhängig von der gesetzten Season.

## Ursachenverdacht
1. **Primär:** `modifyBiomeColors()` in `NmsAdapter_v1_21_5` parst das `ClientboundLevelChunkWithLightPacket`, findet den `PalettedContainer<Biome>`, aber setzt keine neuen `BiomeSpecialEffects` mit den Override-Farben ein. Der Code endet mit einem TODO-Kommentar.
2. **Sekundär:** Die Netty-Pipeline-Injection könnte ebenfalls fehlschlagen (kein `[NmsAdapter]`-Log gesehen). Das muss separat diagnostiziert werden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter_v1_21_5.java` | **Kern-Baustelle:** `modifyBiomeColors()` fertigstellen. Entweder per Reflection auf `BuiltInRegistries.BIOME` (serverseitig, Option A) oder durch Parsen/Modifizieren des `PalettedContainer` im Packet (client-seitig, Option B). |
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter.java` | API ggf. erweitern um `refreshChunk()` oder `invalidateTints()`. |
| `src/main/java/de/ajsch/seasons/visual/FoliageTintManager.java` | Falls Option A gewählt wird: Nach Tint-Update `world.refreshChunkAt()` aufrufen. |
| `src/main/java/de/ajsch/seasons/visual/VisualSeasonManager.java` | Bei Option A: Transition-Logik bleibt gleich, aber Refresh-Mechanismus anpassen. |
| `src/main/resources/foliage_tints.yml` | Unverändert (wird bereits korrekt geladen). |

## Erbetene Hilfe

1. **Diagnose: Server-Log auf NMS-Injection prüfen**
   - Enthält das Log nach Start die Zeile `[NmsAdapter] Successfully injected into server pipeline for 1.21.4/1.21.5`?
   - Wenn nicht: Injection-Debugging vorziehen (Field-Namen prüfen: `connection`/`serverConnection`/`q`/`p`, `channels`/`g` für Paper 1.21.5).

2. **Entscheidung Options A vs. B treffen und umsetzen**
   - **Option A (empfohlen):** `Registry<Biome>` per Reflection hacken – `net.minecraft.core.registries.BuiltInRegistries.BIOME` abrufen, für jeden Biome-Eintrag ein neues `Biome`-Objekt mit überschriebenen `BiomeSpecialEffects` (foliageColorOverride, grassColorOverride) erstellen und in der Registry ersetzen. Nach jeder Änderung `world.refreshChunkAt()` für alle geladenen Chunks aufrufen. Alle Spieler sehen die gleiche Season.
   - **Option B:** `PalettedContainer` im Packet parsen, `Holder<Biome>`-Array extrahieren, jeden Holder mit einer modifizierten Biome-Referenz ersetzen, dann das geänderte Packet weitersenden. Sehr fragil, aber echter client-seitiger Override.

3. **Implementierung**
   - Neue Hilfsklasse `BiomeColorOverrideHelper` oder direkte Erweiterung in `NmsAdapter_v1_21_5`
   - `sendBiomeTint()` speichert `biomeKey → {foliage, grass}` in `pendingTints`
   - Bei `modifyBiomeColors()`: Für jeden Biome-Eintrag im Container prüfen ob `pendingTints` Eintrag existiert → `BiomeSpecialEffects` ersetzen
   - `onDisable()`: Registry-Änderungen rückgängig machen (Option A) oder Pending-Tints leeren (Option B)

4. **Build & Test**
   - `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
   - Deployment, Server-Neustart
   - `/season set fall` → Birken orange, Eichen rot, Dark Forest gelb
   - `/season set winter` → alles grau/braun
   - `/season set spring` → Cherry rosa

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `foliage_tints.yml` Overrides steuern – keine Enum-Switches
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **NMS/Reflection erst ab Phase 2 erlaubt:** Package `nms/` klar kapseln, Adapter-Pattern nutzen
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