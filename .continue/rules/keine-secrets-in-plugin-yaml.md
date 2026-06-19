---
globs: src/main/resources/*.yml
description: Gilt bei allen Änderungen an YAML-Konfigurationsdateien im
  Plugin-Resources-Verzeichnis.
alwaysApply: false
---

In Plugin-YAML-Dateien (config.yml, precipitation_categories.yml, foliage_tints.yml) NIE Secrets wie API-Keys, Passwörter oder Zugangsdaten speichern. Secrets gehören in separate, nicht versionierte Dateien außerhalb des Plugin-Ordners.