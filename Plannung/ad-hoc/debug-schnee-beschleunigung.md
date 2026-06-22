# Arbeitsanweisung: Debug-Schnee-Beschleunigung

| Feld | Wert |
|------|------|
| **Quelle** | Ad-hoc (direkt vom Nutzer) |
| **Auftrag** | Wenn `/season debug` aktiv ist (`season.debug-mode: true`), sollen Platzierung und Schmelzen von Schnee deutlich schneller ablaufen (~10-20x), damit man nicht lange warten muss, um im Winter alles schneebedeckt zu sehen oder im Frühling/Sommer alles schmelzen zu sehen. |
| **Status** | done |
| **Erstellt** | 2025-07-11 |

---

## Aktuelles Ergebnis

- `season.debug-mode: true` verkürzt nur die Jahreslänge auf 20 Tage
- Schnee-Raten (`layers-per-scan=8`, `growth.layers-per-scan=2`, `melt.layers-per-chunk=4`, `max-snow-chunks-per-tick=32`, `melt-chunks-per-tick=8`) bleiben unverändert
- Mit `/tick rate 200` (200 TPS) und Vanilla-Regen kann man Schnee provozieren, aber es dauert trotzdem sehr lange, bis eine Landschaft komplett weiß ist

---

## Ursachenverdacht

Es gibt keine Verbindung zwischen `season.debug-mode` und den Schnee-Raten-Parametern. Die Raten werden 1:1 aus der Config gelesen, ohne Debug-Multiplier.

---

## Betroffene Schichten/Dateien

| Datei | Rolle |
|-------|-------|
| `src/main/resources/config.yml` | Neue Debug-Override-Werte einfügen |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Debug-Multiplier-Getter + Anwendung in bestehenden Gettern |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Delegiert an ConfigManager (keine Änderung nötig, wenn Multiplier in ConfigManager angewendet) |

---

## ToDo-Liste

- [ ] 1. `config.yml` um `season.debug-overrides` Block erweitern: `snow-placement-multiplier`, `snow-growth-multiplier`, `snow-melt-multiplier`, `max-snow-chunks-multiplier`, `melt-chunks-multiplier`
- [ ] 2. `ConfigManager.java`: Neue Getter für die Multiplier; bestehende Getter (`getLayersPerScan`, `getGrowthLayersPerScan`, `getMeltLayersPerChunk`, `getMaxSnowChunksPerTick`, `getMeltChunksPerTick`) mit Debug-Multiplier versehen
- [ ] 3. `bin/main/config.yml` (Build-Ordner) synchron halten
- [ ] 4. Bauen und Deployment-Befehl bereitstellen

---

## Hinweise

- Multiplier als `double` speichern, Ergebnis auf `int` runden mit `Math.max(1, (int) Math.round(raw * multiplier))`
- Keine Änderung an `SnowPlacer`, `SnowGrower`, `SnowMelter` oder `SnowAccumulator` nötig – die lesen ihre Werte bereits via `WeatherConfig` → `ConfigManager`
- `season.debug-mode` bleibt als Boolean erhalten (steuert 20-Tage-Jahr + Multiplier)