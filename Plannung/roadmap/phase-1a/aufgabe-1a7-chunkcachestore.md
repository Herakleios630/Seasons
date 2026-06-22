---
title: "Arbeitsauftrag: ChunkCacheStore – JSON-Persistenz"
quelle: "roadmap.md → Phase 1a, Sprint 1a.7"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: done
---

# Arbeitsauftrag: ChunkCacheStore – JSON-Persistenz

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.7

## Ergebnis
- `ChunkCacheStore.java` erstellt (JSON-Persistenz mit Gson, Base64 für Byte-Arrays)
- `ConfigManager` um `getCacheFile()`, `getCacheSaveIntervalSeconds()`, `getCacheVersion()` erweitert
- `SnowAccumulator.getCache()` gibt ConcurrentHashMap nach außen
- `SeasonsPlugin`: `chunkCacheStore.load()` in onEnable, `chunkCacheStore.save()` in onDisable, `startAsyncSaveTask()` für periodischen async Save
- `BlockEventListener`: markDirty(key) bei Invalidierung
- `config.yml`: neue Sektion `cache.persistence` mit `file`, `save-interval-seconds`, `version`
- Build: `BUILD SUCCESSFUL`

## Erledigt
1. [x] `ChunkCacheStore` Klasse erstellt
2. [x] `SnowAccumulator.getCache()` offengelegt
3. [x] `SnowAccumulator.invalidateChunk()` erweitert um markDirty
4. [x] `ConfigManager` um Cache-Persistence-Methoden erweitert
5. [x] `config.yml` um `cache.persistence`-Sektion ergänzt
6. [x] `SeasonsPlugin.onEnable()`: chunkCacheStore.load() + startAsyncSaveTask()
7. [x] `SeasonsPlugin.onDisable()`: chunkCacheStore.save()
8. [x] `BlockEventListener` erweitert um ChunkCacheStore.markDirty()
9. [x] Build erfolgreich (`compileJava`)