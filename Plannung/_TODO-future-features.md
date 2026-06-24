"# TODO – Zukünftige Features & lose Enden

> Kein Sprint, keine Phase. Offene Punkte die später aufgegriffen werden sollten.

| ID | Bereich | Beschreibung | Notizen |
|----|---------|-------------|---------|
| TODO-01 | Biome-Farben | **Himmel-/Wasser-Farben** (`sky_color`, `water_color`, `water_fog_color`, `fog_color`) in Custom-Biome-JSONs integrieren | `season_colors.yml` um entsprechende Keys erweitern, `BiomeJsonGenerator` schreibt sie mit |
| TODO-02 | Wetter | **Schneefall mit Wolken** – aktuell wird bei <0°C Regen auf `clear`+Partikel gemappt. Echter Schnee-Wetter-Typ mit Wolken nötig. | Separates Konzept-Dokument anlegen (`snowfall-weather-concept.md`) |
| TODO-03 | Biome-Farben | **Farbwerte-Vollständigkeit prüfen** – Vanilla-Biomes wie Cherry Grove, Pale Garden haben keine klassischen `grass_color`-Werte sondern spezielle `grass_color_modifier` | Bei Generator-Implementierung testen |
"| TODO-04 | Frost-System | **Frost-Hysterese** – Ein-/Ausschaltschwelle mit Puffer (z.B. 0,1 K), um Flackern in der Übergangszeit zu vermeiden | Chunk-bezogene Hysterese im `BiomeSpoofCoordinator`, ca. 20 Zeilen |
| TODO-05 | Eis-Effekt | **Fließendes-Wasser-Freezer** – Wasserfälle frieren Vanilla nie ein. Optionaler Plugin-Freezer für extreme Minusgrade. | Wurde aus Phase 3 ausgeklammert (→ Vanilla-Limitation). Kann später via `FlowingWaterFreezer.java` ergänzt werden. |
| TODO-06 | Nebel | **Dichter Partikel-Nebel** – Morgen-/Abendnebel im Herbst via `CLOUD`/`ASH`-Partikel analog `FrostEffectManager`. | Atmosphärischer Nebel kommt via `fog_color` (Phase 3). Dichter Bodennebel später via `FogEffectManager.java`. |"

---

**Legende:** Offen bis explizit in eine Roadmap-Phase aufgenommen.
"