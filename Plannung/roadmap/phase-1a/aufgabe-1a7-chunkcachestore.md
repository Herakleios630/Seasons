---
title: "Arbeitsauftrag: ChunkCacheStore – JSON-Persistenz"
quelle: "roadmap.md → Phase 1a, Sprint 1a.7"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: ChunkCacheStore – JSON-Persistenz

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die Klasse `ChunkCacheStore` zur asynchronen JSON-Persistenz des Chunk-Caches erstellen.
- Key: `worldUID:chunkKey`
- Speicherung als Base64-kodierte Byte-Arrays für `pluginSnow` und `naturalSnow`
- Asynchrones Speichern: dirty-Flag, Batch alle 5s
- Laden bei Server-Start mit `cacheVersion`-Check

## Vorbedingungen
- Chunk-Cache (`ConcurrentHashMap`) existiert
- `ChunkCacheEntry` mit Byte-Arrays für plugin/natural snow

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/persistence/ChunkCacheStore.java` | **NEU** – JSON-Persistenz für Chunk-Cache |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | `ChunkCacheStore` instanziieren, laden/speichern |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `getCache()` bereitstellen |

## Erbetene Hilfe

1. **`ChunkCacheStore` Klasse:**
   - Konstruktor: `ChunkCacheStore(JavaPlugin plugin, ConcurrentHashMap<String, ChunkCacheEntry> cache)`
   - `load()`: `chunk_cache.json` lesen, Base64 dekodieren, Cache befüllen, `cacheVersion` prüfen
   - `save()`: synchron für onDisable
   - `markDirty(String key)`: dirty-Flag setzen
   - Async-Task alle `saveIntervalSeconds` (Config): dirty-Einträge in JSON schreiben

2. **JSON-Format:**
   ```json
   {
     "cacheVersion": 1,
     "chunks": {
       "worldUID:chunkKey": {
         "snowCapable": 220,
         "snowCovered": 218,
         "snowBelowMax": 45,
         "tempLevelMin": -3,
         "tempLevelMax": -1,
         "updated": 1713123456789,
         "pluginSnow": "base64...",
         "naturalSnow": "base64..."
       }
     }
   }
   ```

3. **Integration:**
   - `SeasonsPlugin.onEnable()`: `chunkCacheStore.load()`
   - `SeasonsPlugin.onDisable()`: `chunkCacheStore.save()`
   - `BlockEventListener`: bei Invalidierung `chunkCacheStore.markDirty(key)`

4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine NMS/Reflection**
- **Asynchron:** `CompletableFuture.runAsync()` oder Bukkit-Scheduler
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (Persistenz-Format)
- `docs/handover.md`
- `Plannung/roadmap.md` (1a.7 abhaken)