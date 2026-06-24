---
title: "Arbeitsauftrag 2.5.2: ChunkPacketInterceptor – Grundgerüst + Registrierung"
quelle: "roadmap.md → Phase 2.5, Sprint 2.5.2"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-22"
status: done
---

# ✅ Arbeitsauftrag 2.5.2 (erledigt): ChunkPacketInterceptor – Grundgerüst + Registrierung

**Quelle:** roadmap.md → Phase 2.5, Sprint 2.5.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Neue Klasse `ChunkPacketInterceptor.java` erstellen, die ProtocolLib's `PacketListener`-Interface implementiert und sich auf `MAP_CHUNK`-Pakete registriert. Grundgerüst mit Log-Ausgabe, noch ohne Biome-Override-Logik.

## Aktuelles Ergebnis
- ProtocolLib ist als Dependency verfügbar (aus 2.5.1)
- `BiomeSpoofAdapter` hat `getSpoofedSet()` und `getLastAppliedMap()` für den Zugriff

## Ziel
- `ChunkPacketInterceptor` ist als `PacketListener` registriert
- Jedes abgefangene Chunk-Paket wird geloggt (DEBUG-Level)
- Registrierung/Deregistrierung in `SeasonsPlugin` integriert

## Betroffene Dateien
| Datei | Rolle |
|---|---|
| `visual/ChunkPacketInterceptor.java` | **NEU:** ProtocolLib-Packet-Interceptor |
| `SeasonsPlugin.java` | Registrierung in `onEnable()`, Deregistrierung in `onDisable()` |

## Technische Details

### ChunkPacketInterceptor Struktur
```java
public class ChunkPacketInterceptor implements PacketListener {
    private final BiomeSpoofAdapter adapter;
    private final java.util.logging.Logger logger;

    public ChunkPacketInterceptor(BiomeSpoofAdapter adapter, java.util.logging.Logger logger) {
        this.adapter = adapter;
        this.logger = logger;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.MAP_CHUNK) return;
        int chunkX = event.getPacket().getIntegers().read(0);
        int chunkZ = event.getPacket().getIntegers().read(1);
        String chunkKey = chunkX + "_" + chunkZ;
        if (adapter.getSpoofedSet().contains(chunkKey)) {
            logger.fine("[ChunkPacketInterceptor] Intercepted chunk=" + chunkKey);
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event) { /* nicht benötigt */ }

    @Override
    public ListeningWhitelist getSendingWhitelist() {
        return ListeningWhitelist.newBuilder()
            .types(PacketType.Play.Server.MAP_CHUNK)
            .gamePhaseBoth()
            .build();
    }

    @Override
    public ListeningWhitelist getReceivingWhitelist() {
        return ListeningWhitelist.EMPTY_WHITELIST;
    }
}
```

### Registrierung in SeasonsPlugin
```java
// In onEnable(), NACH BiomeSpoofAdapter.register():
if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
    ChunkPacketInterceptor interceptor = new ChunkPacketInterceptor(biomeSpoofAdapter, getLogger());
    ProtocolLibrary.getProtocolManager().addPacketListener(interceptor);
} else {
    getLogger().warning("ProtocolLib nicht gefunden – keine Biome-Farben auf dem Client.");
}
```

## ToDo-Liste
1. [x] `visual/ChunkPacketInterceptor.java` mit obigem Grundgerüst erstellt (mit Plugin-Parameter für getPlugin())
2. [x] `SeasonsPlugin.java`: Feld `ChunkPacketInterceptor chunkPacketInterceptor` hinzugefügt
3. [x] `SeasonsPlugin.java`: In `onEnable()` nach BiomeSpoofListener den Interceptor registriert (mit ProtocolLib-Check + Bukkit-Import)
4. [x] `SeasonsPlugin.java`: In `onDisable()` den Interceptor deregistriert via `ProtocolLibrary.getProtocolManager().removePacketListener(chunkPacketInterceptor)`
5. [x] Build: `Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons ; .\gradlew.bat compileJava` → **BUILD SUCCESSFUL** (6 Deprecation-Warnungen, alle aus BiomeSpoofAdapter)
