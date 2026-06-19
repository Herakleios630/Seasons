---
title: "Arbeitsauftrag: 0/X Placements – countSnowInChunk verhindert Wechsel zu growExistingSnow"
quelle: "Log-Analyse vom 2025-04-15 – alreadySnow dominiert, isFirstSnow bleibt ewig true"
created: "2025-04-15"
status: done
---

# 0/X Placements Root Cause – isFirstSnow erkennt existierenden Schnee nicht

## Ursachenanalyse (durch Log-Daten bestätigt)
Aus den Logs:
```
alreadySnow=64–80  →  der Chunk IST voll mit Schnee
noGround=0         →  findSolidGround funktioniert
plants=0–4         →  Pflanzen-Entfernung funktioniert
```

**Root Cause:** `countSnowInChunk` findet den existierenden Schnee **nicht**, deshalb bleibt `isFirstSnow` true und `placeFirstSnow` wird immer wieder aufgerufen.

**Warum?** In vielen Chunks liegt unser Schnee auf natürlichem Schnee – nicht als `getHighestBlockAt`-Resultat. Die Suche in `countSnowInChunk` geht zwar 3 Blöcke runter, aber **stoppt am ersten soliden Block** (der natürliche Schnee oder Stein ist) – und überspringt damit den sichtbaren Schnee.

## Tatsächliche Lösung (umgesetzt 2025-04-15)
Die ursprünglichen ToDo-Punkte gingen am Kernproblem vorbei. Stattdessen wurde die gesamte
per-Chunk-Logik durch eine **per-Column-Entscheidung** ersetzt:

- `countSnowInChunk`, `isFirstSnow`, `placeFirstSnow`, `growExistingSnow` komplett entfernt
- Neue `processColumn()`-Methode entscheidet pro Säule:
  1. Top = Schnee + alle 4 Nachbarn haben Schnee oder sind blockiert → `growColumnSnow()`
  2. Top = Pflanze → entfernen, Boden suchen
  3. Top = solider voller Block → Schnee-Layer 1 auf top+1
  4. Top = Zaun/Fackel/Mauer → Säule überspringen
- `isSnowCapable()` prüft auf volle Blöcke (keine Zäune, Mauern, Panes)
- `allNeighborsSnowOrBlocked()` stellt sicher dass Wachstum nur bei geschlossener Schneedecke stattfindet
- `growColumnSnow()` dient nur für Wachstum auf bereits existierenden Schnee-Säulen

## ToDo
1. [x] `countSnowInChunk` und `isFirstSnow` komplett entfernt – Entscheidung jetzt per Column
2. [x] `growExistingSnow`, `placeFirstSnow` durch `processColumn` + `growColumnSnow` ersetzt
3. [x] `isSnowCapable`-Prüfung auf volle Blöcke (Zaun/Fackel/Mauer → skip)
4. [x] Build erfolgreich