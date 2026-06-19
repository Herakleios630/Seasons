---
globs: "**/*.java,src/main/resources/*.yml"
description: Gilt bei allen numerischen Werten, Konstanten, Schwellwerten,
  Limits und Standardeinstellungen im Code oder in Konfigurationsdateien.
alwaysApply: true
---

Alle numerischen Werte (Jahreslänge, Temperaturamplituden, Biome-Offsets, Schnee-Stapelhöhen, Effekt-Zeiten, Scan-Intervalle, etc.) MÜSSEN über Config-Dateien steuerbar sein. Keine Magic Numbers in Java-Code. Jeder neue Wert bekommt einen Config-Eintrag mit sinnvollem Default.