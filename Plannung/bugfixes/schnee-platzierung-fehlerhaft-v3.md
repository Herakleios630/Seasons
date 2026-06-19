"---
title: \"Arbeitsauftrag: Schnee-Platzierung v3 – countSnowInChunk findet Schnee nicht\"
quelle: \"Bugfix aus v2-Logs – alreadySnow dominiert, isFirstSnow bleibt ewig true\"
created: \"2025-04-15\"
status: in-progress
---

# countSnowInChunk findet existierenden Schnee nicht → Chunk bleibt FirstSnow

## Ursache (bestätigt durch Logs)
- `placeFirstSnow` findet massenhaft `alreadySnow` (48-80 von maxAttempts)
- Das beweist: Der Chunk HAT bereits viel Schnee
- `countSnowInChunk` nutzt `getHighestBlockAt`, das Paper-API-Schnee nicht korrekt returned
- Deshalb: `isFirstSnow` → true → `placeFirstSnow` statt `growExistingSnow` → ewig 0/X

## ToDo
1. [ ] `countSnowInChunk` robust machen: pro Column von `getHighestBlockYAt` aus max 3 Blöcke nach unten prüfen, ob Schnee da ist
2. [ ] Speed-Configs für Tests anpassen (scan-interval senken, chunks-per-tick erhöhen)
3. [ ] Defaults in config.yml anpassen
4. [ ] Bauen & Deploy-Befehle posten
"