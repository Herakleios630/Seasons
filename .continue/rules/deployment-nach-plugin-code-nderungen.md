---
globs: src/main/**/*.java
description: Gilt für alle Deployment-Schritte, bei denen ausschließlich
  Java-Code des Plugins geändert wurde und keine Config-Dateien (YAML).
alwaysApply: false
---

Wenn nur Java-Code geändert wurde (keine YAML-Configs), Plugin-JAR per SCP kopieren und dann Crafty mit `sudo systemctl restart crafty` neu starten. Keinen Plugin-Reload verwenden.