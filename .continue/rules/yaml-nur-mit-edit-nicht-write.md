---
globs: "**/*.yml"
description: Gilt bei allen Änderungen an YAML-Konfigurationsdateien im Projekt
  (config.yml, precipitation_categories.yml, foliage_tints.yml, plugin.yml).
alwaysApply: false
---

YAML-Dateien (.yml) NIE mit filesystem_write_file schreiben. Das Tool interpretiert den content-Parameter als JSON-String und schreibt die äußeren Anführungszeichen mit in die Datei, was zu YAML-Parser-Fehlern führt. Stattdessen IMMER filesystem_edit_file mit oldText/newText verwenden, um gezielt Zeilen zu ersetzen oder anzuhängen.