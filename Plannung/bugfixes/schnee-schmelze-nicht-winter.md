---
title: "Arbeitsauftrag: Schnee-Markierung – nur Plugin-Schnee schmelzen, natürlichen Schnee ignorieren"
quelle: "Nutzer-Feedback 2025-04-15 – Im Frühling/Sommer/Herbst soll nur Plugin-Schnee schmelzen"
created: "2025-04-15"
status: open
---

# Schneeschmelze in Nicht-Winter-Saisons – Natürlichen Schnee nicht anfassen

## Auftrag
In den Saisons Fruehling, Sommer und Herbst soll saemtlicher von uns platzierter Schnee schmelzen. Naturerlicher Vanilla-Schnee (in Schneebiomen, Bergen) soll liegen bleiben.

## Aktueller Zustand
`processMeltChunk` schmilzt ALLE Schneebloecke unabhngig ihrer Herkunft. Es gibt keine Unterscheidung zwischen Plugin-platziertem und natrlichem Schnee.

## Loesungsansatz
Plugin-Schnee, den wir setzen, bekommt eine unsichtbare Kennzeichnung (z.B. ein BlockState-Tag "seasons:placed" per PersistentDataContainer des Chunks oder Schnee-Blocks). Alternativ: Wir speichern Chunk-weise, welche Columns Plugin-Schnee enthalten.

## ToDo
1. [ ] Konzept entscheiden: Tagging-Strategie (PersistentDataContainer am Chunk, Flag am Schnee, oder Schnee-Set pro Chunk)
2. [ ] `placeFirstSnow` und `growExistingSnow`: platzierten Schnee markieren
3. [ ] `processMeltChunk`: nur markierten Schnee schmelzen
4. [ ] Config-Option fr "natuerlichen Schnee ignorieren"
5. [ ] Bauen & Deploy-Befehle posten