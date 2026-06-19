---
globs: src/main/java/**/*.java
description: Windows-Entwicklungsumgebung mit Paper-Plugin in Kotlin/Java, wenn
  Dateioperationen auf dem Projekt fehlschlagen
alwaysApply: true
---

Bei Java-Dateien in Minecraft-Plugin-Projekten (Paper, Spigot) IMMER mit `filesystem_read_text_file` lesen und mit `single_find_and_replace` editieren – niemals mit `read_file` oder `edit_existing_file`. Exakte Text-Snippets ohne Escape-Spielereien verwenden, kurze eindeutige Abschnitte ersetzen. Bei mehreren Änderungen in derselben Datei jeden Edit einzeln mit eigenem `single_find_and_replace`-Aufruf durchführen. `edit_existing_file` und `read_file` sind auf Windows-Pfaden mit Umlauten/Sonderzeichen unzuverlässig.