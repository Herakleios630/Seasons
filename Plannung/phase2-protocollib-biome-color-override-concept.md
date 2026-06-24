---
title: "Konzept: ProtocolLib-basiertes Biome-Color-Override (Phase 2.5)"
quelle: "Ad-hoc – Analyse BiomeVisuals, AdvancedSeasons, AeternumSeasons, RealisticSeasons"
related-roadmap: "Plannung/roadmap.md → Neue Phase 2.5"
created: "2025-07-22"
status: draft
---

# Konzept: ProtocolLib-basiertes Biome-Color-Override (Phase 2.5)

## 1. Problemstellung

Das aktuelle Biome-Spoofing (`BiomeSpoofAdapter`) ruft `world.setBiome()` + `world.refreshChunk()` auf. Das ändert das Biome **serverseitig**, aber die Biome-Daten werden **NICHT** in das an den Client gesendete Chunk-Paket (`ClientboundLevelChunkWithLightPacket`) serialisiert. Ergebnis: Spieler sehen keine Änderung der Laub-/Grasfarben.

### Was wir getestet haben
- `world.refreshChunk()` → nur Light-Update, kein Biome-Update
- `world.unloadChunk()` + `world.getChunkAt()` → Paper-API existiert nicht richtig
- `chunk.unload(true)` + `world.getChunkAt()` → funktioniert, aber flackert

### Was andere Plugins machen
| Plugin | Mechanismus | Funktioniert? |
|---|---|---|
| **BiomeVisuals** (Owen1212055, Open Source) | ProtocolLib → Registry Packet Override (`BiomeRegistrySendEvent`) | Nur bei Login/World-Wechsel, nicht auf geladenen Chunks |
| **AdvancedSeasons** (Premium) | ProtocolLib → Chunk Packet Override | ✅ Ja, per `chunkUpdates`/sec nach Season-Wechsel |
| **AeternumSeasons** | `setBiome()` + `refreshChunk()` | Nur bei ChunkLoad |
| **RealisticSeasons** | Nur Partikel | Kein Biome-Spoofing |

## 2. Lösungsansatz

> **ProtocolLib als Packet-Interceptor für `ClientboundLevelChunkWithLightPacket`**

```
┌─────────┐    ┌──────────────┐    ┌──────────────┐    ┌─────────┐
│ Server  │───▶│ ProtocolLib   │───▶│ Packet mit    │───▶│ Client  │
│ (NMS)   │    │ (Interceptor) │    │ Farb-Override │    │         │
└─────────┘    └──────┬───────┘    └──────────────┘    └─────────┘
                      │
              ┌───────▼───────┐
              │ BiomeSpoofAdapter │
              │ (Dirty-Chunk-Map) │
              └───────────────┘
```

### 2.1 Architektur-Prinzip

1. **`BiomeSpoofAdapter` bleibt erhalten** – er kümmert sich weiterhin um `setBiome()` serverseitig
2. **Neuer `ChunkPacketInterceptor`** – registriert sich via ProtocolLib auf `ClientboundLevelChunkWithLightPacket`
3. **Dirty-Chunk-Map** – der Adapter markiert Chunks, deren Biome geändert wurden
4. **Packet-Override** – wenn ein Chunk-Paket für einen markierten Chunk gesendet wird, wird das Biome im Packet durch das Ziel-Biom ersetzt
5. **Re-Send** – `chunk.unload(true)` + `world.getChunkAt()` triggert ein neues Chunk-Paket, das vom Interceptor abgefangen wird

### 2.2 Warum dieser Ansatz die anderen Probleme löst

| Problem | Lösung |
|---|---|
| `setBiome()` serialisiert nicht in Chunk-Paket | ProtocolLib überschreibt die Biome-Daten **im** Chunk-Paket |
| `refreshChunk()` sendet nur Light-Update | Nicht mehr nötig – Chunk-Re-Send sendet komplettes Paket |
| Unload/Reload verursacht Flackern | Wir raten-limitieren: max 8 Chunks/sec (wie AdvancedSeasons) |
| Registry-Update wirkt nur bei Login | Wir arbeiten auf Chunk-Ebene, nicht Registry-Ebene |

## 3. Technische Architektur

### 3.1 Abhängigkeiten

```kotlin
// build.gradle.kts
repositories {
    maven("https://repo.dmulloy2.net/repository/public/")  // ProtocolLib
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
}
```

### 3.2 Neue Packages und Klassen

```
src/main/java/de/ajsch/seasons/
├── visual/
│   ├── BiomeSpoofAdapter.java          (→ bestehend, mini-Änderungen)
│   ├── BiomeSpoofListener.java         (→ bestehend, unverändert)
│   ├── BiomeBackupStore.java           (→ bestehend, unverändert)
│   ├── ChunkPacketInterceptor.java     (→ NEU: ProtocolLib-Interceptor)
│   └── BiomeColorOverride.java         (→ NEU: Farb-Mapping pro Season)
```

### 3.3 Datenfluss

```
1. SeasonChangeEvent
   │
   ▼
2. BiomeSpoofAdapter.revertAll() + setSeasonTransitionUntil()
   │
   ▼
3. Timer (40 ticks): BiomeSpoofAdapter.run()
   ├── setBiome() für gespoofte Chunks
   ├── Markiert Chunks in dirtyChunkMap
   └── flushResends(): chunk.unload(true) + world.getChunkAt()
       │
       ▼
4. Server sendet ClientboundLevelChunkWithLightPacket
   │
   ▼
5. ChunkPacketInterceptor.onPacketSending()
   ├── Prüft ob Chunk in dirtyChunkMap
   ├── Liest Ziel-Biom aus BiomeSpoofAdapter
   ├── Ersetzt Biome-Daten im Packet
   └── Entfernt Chunk aus dirtyChunkMap
```

### 3.4 ChunkPacketInterceptor – Kernlogik

```java
public class ChunkPacketInterceptor implements PacketListener {
    // ProtocolLib registriert diesen Listener für SENDING-Events
    
    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.MAP_CHUNK) return;
        
        int chunkX = event.getPacket().getIntegers().read(0);
        int chunkZ = event.getPacket().getIntegers().read(1);
        String chunkKey = chunkX + "_" + chunkZ;
        
        // Nur modifizieren wenn der Chunk gespooft ist
        if (!adapter.getSpoofedSet().contains(chunkKey)) return;
        
        Biome targetBiome = adapter.getLastAppliedMap().get(chunkKey);
        if (targetBiome == null) return;
        
        // Biome-Daten im Packet überschreiben
        // → Zugriff auf NMS-Biome-Palette via Reflection (ProtocolLib abstrahiert)
        overwriteBiomeData(event.getPacket(), targetBiome);
    }
}
```

### 3.5 Umfang der Phase 2.5

| Aufgabe | Beschreibung | Aufwand |
|---|---|---|
| 2.5.1 | ProtocolLib-Dependency + `plugin.yml` Soft-Depend | Klein |
| 2.5.2 | `ChunkPacketInterceptor` – Grundgerüst + Registrierung | Mittel |
| 2.5.3 | Packet-Override-Logik (Biome-Daten im Chunk-Paket überschreiben) | Mittel |
| 2.5.4 | Integration mit `BiomeSpoofAdapter` (Dirty-Map, Re-Send) | Klein |
| 2.5.5 | Build, Deploy, Funktionstest | Klein |

## 4. Config-Erweiterungen

### 4.1 `biome_spoof.yml` (erweitert)

```yaml
enabled: true
mode: GLOBAL_RING
radius_chunks: 8
budget_chunks_per_tick: 16
revert_on_non_winter: true
resend_chunks_per_tick: 8          # NEU: max Chunk-Re-Sends pro Tick
resend_enabled: true                # NEU: ProtocolLib-basierten Re-Send einschalten

transition_days: 3
seasons:
  SPRING: FLOWER_FOREST
  SUMMER: PLAINS
  FALL: WINDSWEPT_SAVANNA
  WINTER: SNOWY_PLAINS
# ... Rest unverändert
```

### 4.2 Kein `foliage_tints.yml` (verschoben)

Das in Phase 2 angedachte `foliage_tints.yml` wird **NICHT** verwendet, da die Farben jetzt direkt vom Biome kommen. Der Client berechnet Grass/Foliage-Farben automatisch basierend auf dem Biome – wir müssen nur das Biome selbst korrekt übertragen.

## 5. Risiken & Fallbacks

| Risiko | Mitigation |
|---|---|
| ProtocolLib nicht installiert | `softdepend: [ProtocolLib]` in `plugin.yml` – Plugin startet auch ohne, aber ohne Biome-Farben |
| ProtocolLib API-Änderungen | Version 5.3.0 fixieren, bei Major-Updates testen |
| NMS-Änderungen in Minecraft 1.21.5 | Packet-Struktur via ProtocolLib abstrahiert – nur Reflection auf Biome-Palette nötig |
| Performance | Max 8 Chunk-Re-Sends/Tick, Dirty-Map begrenzt Größe |
| Flackern bei Unload/Reload | Nur Chunks außerhalb der View-Distance des Spielers entladen |

## 6. Abgrenzung zu späteren Phasen

- **Phase 2.5:** Nur Biome-Farben via ProtocolLib (Grass/Foliage/Water/Sky)
- **Phase 2.6 (später):** Frost-Overlay, Schnee-Partikel, Eiszapfen
- **Phase 3 (später):** Eigene Biome-Definitionen, Biome-Effekte (Partikel, Sounds)
