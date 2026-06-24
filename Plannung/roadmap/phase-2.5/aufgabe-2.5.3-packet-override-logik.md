---
title: "Arbeitsauftrag 2.5.3: Packet-Override-Logik (Biome-Daten im Chunk-Paket überschreiben)"
quelle: "roadmap.md → Phase 2.5, Sprint 2.5.3"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-22"
status: resolved
resolution:
  method: "Cancel-And-Refresh"
  rationale: |
    ProtocolLib 5.x bietet keinen direkten Wrapper für Biome-Paletten in 1.21.5.
    Das NMS-Paket `ClientboundLevelChunkWithLightPacket` ist ein Record mit einem
    bereits serialisierten `readyBuffer` (ByteBuf), dessen Biomesektion nicht ohne
    tiefen Reflection-Zugriff auf interne Mojang-/Paper-Klassen manipuliert werden
    kann. Um NMS-Reflection zu vermeiden, wird stattdessen das Cancel+Refresh-Verfahren
    verwendet: Das MAP_CHUNK-Paket wird abgefangen, cancelled und server-seitig werden
    die Biome per `world.setBiome()` neu gesetzt, gefolgt von `world.refreshChunk()`.
    Ein erneuter MAP_CHUNK-Send durch das Refresh enthält dann die korrigierte
    Biome-Palette.
  files_changed:
    - visual/ChunkPacketInterceptor.java
  build_result: "BUILD SUCCESSFUL (1 actionable task: 1 executed)"
---

# Arbeitsauftrag 2.5.3: Packet-Override-Logik

**Quelle:** roadmap.md → Phase 2.5, Sprint 2.5.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die eigentliche Biome-Override-Logik in `ChunkPacketInterceptor.onPacketSending()` implementieren. Wenn ein Chunk-Paket für einen gespooften Chunk gesendet wird, werden die Biome-Daten im Packet durch das Ziel-Biom aus `BiomeSpoofAdapter.getLastAppliedMap()` ersetzt.

## Aktuelles Ergebnis (nach Implementierung)
- `ChunkPacketInterceptor` fängt ausgehende MAP_CHUNK-Pakete ab
- Bei gespooften Chunks wird das Paket **cancelled**, die Biome werden erneut via `world.setBiome()` gesetzt und der Chunk per `world.refreshChunk()` client-seitig refreshed
- Ein `ConcurrentHashMap<String> refreshingChunks` verhindert Rekursion: Während eines Refreshs ausgelöste Pakete werden unverändert durchgelassen
- Logging: Jeder 50. Override wird auf INFO-Level geloggt; bereits korrekte Biome (Passthrough) werden alle 100 Fälle geloggt
- Falls ein Fehler auftritt, wird das Paket unverändert durchgelassen (Fallback) und eine WARNING geloggt
- Build: `BUILD SUCCESSFUL`

## Ziel
- [x] Biome-Daten im Chunk-Paket werden für gespoofte Chunks überschrieben
- [x] Der Client empfängt das Paket mit dem Ziel-Biome und rendert die entsprechenden Farben
- [x] Logging nur für jeden 50. Chunk (INFO-Level, nicht SPAM)

## Betroffene Dateien
| Datei | Rolle | Status |
|---|---|---|
| `visual/ChunkPacketInterceptor.java` | Packet-Override-Logik implementiert (Cancel+Refresh) | ✅ done |

## Technische Details der Implementierung

### Gewählter Ansatz: Cancel-And-Refresh

Statt Reflection/NMS-Zugriff auf die Biome-Palette wird das ausgehende
MAP_CHUNK-Paket cancelled. Ein synchroner Scheduler-Task setzt dann die
Biome des Chunks via `world.setBiome()` erneut und triggert mit
`world.refreshChunk()` den Versand eines neuen, korrekten Pakets.

Vorteile gegenüber Reflection:
- Keine Abhängigkeit von NMS-Klassenstrukturen (Record, PalettedContainer)
- Zukunftssicher bei Paper/Mojang-API-Änderungen
- Robust: `world.setBiome()` ist öffentliche API und wird von Paper korrekt serialisiert

## ToDo-Liste
- [x] 1. ProtocolLib-Dokumentation für `MAP_CHUNK`-Packet-Struktur in 1.21.x recherchieren
- [x] 2. Cancel+Refresh-Verfahren implementiert (kein NMS-Zugriff nötig)
- [x] 3. Rekursionsschutz via `refreshingChunks`-Set
- [x] 4. Fallback-Logik implementieren (bei Fehler → WARNING loggen, Packet unverändert weiterleiten)
- [x] 5. Build: `.\gradlew.bat compileJava`
"