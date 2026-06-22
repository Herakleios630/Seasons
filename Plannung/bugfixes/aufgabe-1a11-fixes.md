---
title: "Bugfixes aus Phase-1a-Funktionstest"
quelle: "aufgabe-1a11-build-deploy-test.md"
created: "2026-06-19"
status: done (zweiter Fix deployed)
---

# Bugfixes aus Phase‑1a‑Funktionstest

**Quelle:** Phase-1a-Build‑&‑Deploy‑Test (aufgabe‑1a11), Server‑Live‑Test vom 19.06.2026

## Übersicht der gefundenen Probleme

| # | Problem | Schwere |
|---|---------|---------|
| 1 | ✅ Bereits in Bugfix 1a10.4 behoben – Partikel laufen wieder | gelöst |
| 2 | Blumen und Leaf Litter werden nicht durch Schnee ersetzt | mittel |
| 3 | Grow‑Phase wird nie erreicht (`grown=0`) | schwer |

---

## Bug 2: Pflanzen/Leaf Litter bleiben unter Schnee liegen

### Ergebnis
- Schneeplatten werden platziert, aber Blumen (WILDFLOWERS, PINK_PETALS) und
  Laub‑Layer (LEAF_LITTER) werden nicht entfernt. Schnee liegt dann *neben*
  diesen Blöcken, nicht *statt* ihnen.

### Ursachenverdacht
- `SnowAccumulator.REPLACEABLE_PLANTS` enthält nicht die neuen 1.21.5‑Materialien:
  `LEAF_LITTER`, `PINK_PETALS`, `WILDFLOWERS`.
- `isReplaceablePlant()` filtert auf dieses EnumSet → unbekannte Materialien
  werden nicht als ersetzbar erkannt.

### Betroffene Schichten
| Datei | Rolle |
|---|---|
| `weather/SnowAccumulator.java` | `REPLACEABLE_PLANTS` EnumSet + `isReplaceablePlant()` |

### Fix
1. `LEAF_LITTER`, `PINK_PETALS`, `WILDFLOWERS` in das EnumSet aufnehmen.
2. Ggf. weitere 1.21.5‑Pflanzen (`CACTUS_FLOWER`?) recherchieren und ergänzen.

### ToDo
- [x] Neue 1.21.5‑Materialien zu `REPLACEABLE_PLANTS` hinzufügen
- [x] Build + Deploy + erneut testen

---

## Bug 3: Grow‑Phase wird nie erreicht (`grown=0`)

### Ergebnis
- Summary‑Log zeigt `placed=47 grown=0` – es wurde nur platziert, nie gewachsen.
- `isFullyGrown`-Chunks: 72 (korrekt)
- `cache: 377 hits, 295 misses` (korrekt)

### Ursachenverdacht
- `isSaturated()` prüft auf `snowBelowMax == 0`. Dieses Feld wird nur im
  `scanChunkColumns()` initial befüllt und dann nur in `growSnowInChunk()`
  dekrementiert – **nicht** aber in `processChunk()`.
- `processChunk()` setzt `pluginSnowHeight[idx]=1`, ohne `snowBelowMax` zu
  verringern. Dadurch bleibt `snowBelowMax` auf dem initialen Scan‑Wert
  (meist >0) und ein Chunk wird nie `isSaturated()` → `growSnowInChunk()`
  wird nie aufgerufen.
- Zusätzlicher Faktor: `maxNaturalHeight=2` + Kältebonus ergibt
  `maxHeight >= 2` für die meisten Biomes. `snowBelowMax` zählt alle Spalten
  mit `currentSnow < maxHeight` – das sind fast alle Spalten mit nur 1 Layer
  Plugin‑Schnee. Also bleibt `snowBelowMax` lange >0.

### Betroffene Schichten
| Datei | Rolle |
|---|---|
| `weather/ChunkCacheEntry.java` | `isSaturated()`-Logik + `snowBelowMax`-Feld |
| `weather/SnowAccumulator.java` | `processChunk()` fehlt snowBelowMax‑Update |

### Fix (zwei mögliche Ansätze)

**Ansatz A (konservativ):** `SnowAccumulator.processChunk()` aktualisiert
`snowBelowMax` nach jedem Platzieren – prüft, ob `maxHeight > 1` ist und
dekrementiert entsprechend.

**Ansatz B (besser, einfacher):** `isSaturated()` umdefinieren:
```java
public boolean isSaturated() {
    return snowCapable > 0 && snowCapable == totalPluginSnowColumns;
}
```
Sobald alle schneefähigen Spalten mindestens 1 Plugin‑Layer haben, gilt der
Chunk als gesättigt und Grow beginnt. Dies ist die ursprünglich intendierte
Semantik (Phase‑1a‑Design: erst alle Spalten belegen, dann stapeln).

### ToDo
- [x] `isSaturated()` in `ChunkCacheEntry.java` umdefinieren (Ansatz B) – auf `snowCovered >= snowCapable` statt `totalPluginSnowColumns >= snowCapable`
- [x] `snowBelowMax`-Feld bleibt erhalten (wird in scanChunkColumns/growSnowInChunk noch genutzt)
- [x] `growSnowInChunk()`: Sammelt jetzt ALLE Spalten mit Schnee (nicht nur pluginSnowHeight>0); Spalten mit nur Natural-Schnee werden beim ersten Grow als plugin-managed adoptiert
- [x] Build + Deploy + erneut testen

### Zweiter Fix (2025-06-19): Chunks mit Vanilla-Schnee wachsen jetzt
- **Root Cause 2:** `growSnowInChunk()` sammelte nur Spalten mit `pluginSnowHeight > 0`. Chunks, die nur Vanilla-Schnee hatten, erreichten nie den gesättigten Zustand (weil `isSaturated()` auch `snowCovered` prüft), und wenn sie es doch taten, fand `growSnowInChunk()` keine growable Spalten.
- **Fix:** `growSnowInChunk()` sammelt jetzt ALLE Spalten mit `totalCurrent > 0`. Spalten mit `pluginSnowHeight==0` (nur natürlicher Schnee) werden beim ersten Grow adoptiert: `pluginSnowHeight=1`, `totalPluginSnowColumns++`. Das nachfolgende `pluginSnowHeight++` wird in diesem Fall unterdrückt.

---

## Sync nach Abschluss

- `Plannung/roadmap.md` → Bugfixes vermerken
- `docs/handover.md` → Status aktualisieren
- `README.md` ggf. Known Issues streichen