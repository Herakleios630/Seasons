---
globs: "**/*.java"
description: Gilt bei jeder Java-Codeänderung im Seasons-Projekt.
alwaysApply: false
---

Immer mit Java 21 bauen. Nach jeder Codeänderung zuerst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`. Build-Fehler sofort beheben bevor weitergearbeitet wird. Das deploybare Artefakt ist `build/libs/Seasons-0.1.0-SNAPSHOT.jar`, nicht das `-plain.jar`.