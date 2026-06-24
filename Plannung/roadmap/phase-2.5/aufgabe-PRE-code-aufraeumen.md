---
title: "Arbeitsauftrag 2.5-PRE: Altcode aus Phase 2 analysieren, aufräumen und vorbereiten"
quelle: "roadmap.md → Phase 2.5 – Vorbereitung"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-22"
status: done
---

# Arbeitsauftrag 2.5-PRE: Altcode aus Phase 2 analysieren, aufräumen und vorbereiten

**Quelle:** roadmap.md → Phase 2.5 – Vorbereitung (vor 2.5.1)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Vor Beginn der ProtocolLib-Integration (Phase 2.5) muss der bestehende Phase-2-Code analysiert werden:
- Welche Klassen, Methoden und Felder bleiben erhalten?
- Welche sind überflüssig und können gelöscht werden?
- Welche brauchen nur minimale Anpassungen?

Ziel: Ein sauberes Code-Fundament, auf dem Phase 2.5 aufbaut.

---

## 1. Bestandsaufnahme – Was ist aktuell im Code?

### `BiomeSpoofAdapter.java` (~480 Zeilen)

| Element | Typ | Verwendung |
|---|---|---|
| `plugin`, `clock`, `config`, `backupStore`, `logger` | Felder | ✅ Bleiben |
| `mode`, `taskId`, `radiusChunks`, `budgetPerTick` | Felder | ✅ Bleiben |
| `transitionDays`, `revertOnNonWinter`, `oceanSpoofEnabled`, `keepDeepVariants` | Felder | ✅ Bleiben |
| `seasonTarget`, `oceanTarget`, `excludedBiomes`, `disabledWorlds` | Felder | ✅ Bleiben |
| `familyCache`, `coldChunks` | Felder | ✅ Bleiben |
| `spoofed`, `lastApplied` | Felder | ✅ Bleiben (Critical für ProtocolLib) |
| `nudgeCooldownMs`, `nudgeMaxPerTick` | Felder | ❌ Löschen |
| `nudgeQueues`, `lastNudgeTime` | Felder | ❌ Löschen |
| `chunksNeedingResend` (ConcurrentHashMap) | Feld | ❌ Löschen (ersetzt durch ProtocolLib) |
| `cachedOffsets` | Feld | ✅ Bleiben |
| `seasonTransitionUntil` | Feld | ✅ Bleiben |
| `reloadFromConfig()` | Methode | ⚠️ Anpassen: Nudge-Config entfernen, `resend_*`-Felder hinzu |
| `register()` | Methode | ✅ Bleiben unverändert |
| `unregister()` | Methode | ⚠️ Anpassen: Nudge-Cleanup entfernen |
| `run()` → `runInternal()` | Methoden | ⚠️ Anpassen: `flushNudges()`-Aufruf entfernen, `flushResends()` behalten |
| `classifyOriginalFamily()` | Methode | ✅ Bleiben unverändert |
| `chooseTargetBiomeForChunk()` | Methode | ✅ Bleiben unverändert |
| `isChunkExcludedByConfig()` | Methode | ✅ Bleiben unverändert |
| `shouldSkipSpoofForChunk()` | Methode | ✅ Bleiben unverändert |
| `isColdBiome()`, `isOceanBiome()`, `isDeepOcean()`, `getDeepVariant()` | Methoden | ✅ Bleiben unverändert |
| `captureAndApply()` | Methode | ⚠️ Anpassen: `chunk.unload(true)` + `world.getChunkAt()` nach `setBiome()` |
| `revertChunk()` | Methode | ⚠️ Anpassen: Gleicher Unload/Reload-Mechanismus |
| `revertAll()` | Methode | ⚠️ Anpassen: Nudge-Cleanup entfernen |
| `nudgeViewers()` | Methode | ❌ Löschen |
| `enqueueNudge()` | Methode | ❌ Löschen |
| `flushNudges()` | Methode | ❌ Löschen |
| `flushResends()` | Methode | ⚠️ Bleibt vorerst, wird später durch ProtocolLib ersetzt |
| Getter-Methoden | Methoden | ⚠️ `getNudgeQueueSize()` löschen, Rest bleibt |
| Public-Access-Methoden (`getSpoofedSet`, `getLastAppliedMap`...) | Methoden | ✅ Bleiben (Critical für `ChunkPacketInterceptor`) |

### `BiomeSpoofListener.java` (~100 Zeilen)

| Element | Aktion |
|---|---|
| Komplette Klasse | ✅ **Unverändert** – ChunkLoad/Unload/SeasonChange-Listener brauchen keine Änderung |

### `BiomeBackupStore.java` (~150 Zeilen)

| Element | Aktion |
|---|---|
| Komplette Klasse | ✅ **Unverändert** – Persistenz-Mechanismus bleibt identisch |

### `SpoofMode.java`, `BiomeFamily.java`

| Element | Aktion |
|---|---|
| Komplette Klassen | ✅ **Unverändert** – Enums brauchen keine Änderung |

### `biome_spoof.yml`

| Element | Aktion |
|---|---|
| Bestehende Felder | ✅ Bleiben |
| `nudge`-Sektion | ❌ Löschen |
| Neue Felder `resend_enabled`, `resend_chunks_per_tick` | ➕ Hinzufügen |

### `ConfigManager.java`

| Element | Aktion |
|---|---|
| `getNudgeMaxPerTick()`, `getNudgeCooldownSeconds()`, `isNudgeEnabled()` | ❌ Löschen |
| Neue Methoden `isResendEnabled()`, `getResendChunksPerTick()` | ➕ Hinzufügen |

### `SeasonsPlugin.java`

| Element | Aktion |
|---|---|
| Felder für ChunkPacketInterceptor | ➕ Neu (in 2.5.2) |
| `onEnable()` Registrierung | ➕ Neu (in 2.5.2) |
| `onDisable()` Deregistrierung | ➕ Neu (in 2.5.2) |

### `plugin.yml`

| Element | Aktion |
|---|---|
| `softdepend: [ProtocolLib]` | ➕ Hinzufügen (in 2.5.1) |

### `build.gradle.kts`

| Element | Aktion |
|---|---|
| ProtocolLib Repository + Dependency | ➕ Hinzufügen (in 2.5.1) |

---

## 2. Lösch- und Änderungsreihenfolge

### Schritt 1: Config bereinigen
- `biome_spoof.yml`: `nudge`-Sektion komplett löschen
- `biome_spoof.yml`: `resend_enabled: true` und `resend_chunks_per_tick: 8` hinzufügen
- `ConfigManager.java`: `isNudgeEnabled()`, `getNudgeMaxPerTick()`, `getNudgeCooldownSeconds()` löschen
- `ConfigManager.java`: `isResendEnabled()`, `getResendChunksPerTick()` hinzufügen

### Schritt 2: Felder löschen
- `BiomeSpoofAdapter.java`: Diese Felder komplett entfernen:
  - `nudgeCooldownMs`, `nudgeMaxPerTick`
  - `nudgeQueues` (Map<UUID, ArrayDeque<long[]>>)
  - `lastNudgeTime` (Map<UUID, Long>)
  - `chunksNeedingResend` (ConcurrentHashMap<String, World>)

### Schritt 3: Methoden löschen
- `BiomeSpoofAdapter.java`: Diese Methoden komplett entfernen:
  - `nudgeViewers(World, int, int)`
  - `enqueueNudge(Player, World, int, int)`
  - `flushNudges()`
  - `flushResends()`
  - `getNudgeQueueSize()`

### Schritt 4: Methoden bereinigen
- `reloadFromConfig()`: Zeilen für Nudge-Config entfernen
- `reloadFromConfig()`: Neue `resend_*`-Config auslesen (optional, für später)
- `unregister()`: Nudge-Queue-Clear und `lastNudgeTime.clear()` entfernen
- `revertAll()`: `nudgeQueues.clear()`, `nudgeLast.clear()` entfernen
- `runInternal()`: `flushNudges()`-Aufruf entfernen
- `runInternal()`: `flushResends()`-Aufruf entfernen (wird durch ProtocolLib ersetzt)
- `captureAndApply()`: `chunksNeedingResend.put(chunkKey, world)`-Zeile entfernen
- `revertChunk()`: Unverändert (keine Nudge-/Resend-Logik dort)

### Schritt 5: Getter bereinigen
- `getNudgeQueueSize()` komplett löschen
- Heartbeat-Log in `runInternal()`: Nudge-Queue-Referenz entfernen (statt `nudgeQ=%d` → nichts oder `resendPending`)

---

## 3. ToDo-Liste

1. **Analyse abschließen:** Diese Arbeitskarte prüfen, ob alle zu löschenden Elemente korrekt identifiziert sind
2. **`biome_spoof.yml` bereinigen:** `nudge`-Sektion löschen, `resend_*`-Felder hinzufügen
3. **`ConfigManager.java` bereinigen:** Nudge-Methoden löschen, neue Resend-Methoden hinzufügen
4. **`BiomeSpoofAdapter.java` – Felder löschen:** Alle in Schritt 2 gelisteten Felder entfernen
5. **`BiomeSpoofAdapter.java` – Methoden löschen:** Alle in Schritt 3 gelisteten Methoden entfernen
6. **`BiomeSpoofAdapter.java` – Methoden bereinigen:** Alle in Schritt 4 gelisteten Aufrufe/Zeilen entfernen
7. **`BiomeSpoofAdapter.java` – Getter bereinigen:** `getNudgeQueueSize()` löschen
8. **Build-Kontrolle:** `Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons ; .\gradlew.bat compileJava`
9. **Optional: Heartbeat-Log anpassen** – Nudge-Referenz durch sinnvolle Metrik ersetzen

---

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers**
- **Biome nie hardcoden**
- **Season deterministisch**
- **Java-Dateien ≤ 400 Zeilen:** BiomeSpoofAdapter.java aktuell ~480 Z. → muss UNTER 400 kommen
- **Build:** Nach jeder Codeänderung erst `compileJava`, dann `shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
