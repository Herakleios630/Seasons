---
description: Gilt für alle Deployment-Vorgänge. Verhindert, dass SSH/SCP-Befehle
  wegen Passwortabfragen hängen bleiben.
alwaysApply: false
---

Lokale Build- und Test-Befehle (gradlew, python compileall etc.) direkt selbst ausführen. Remote-Befehle wie scp und ssh immer nur als Codeblock bereitstellen, nie selbst ausführen (wg. Passwörtern).