---
description: Verhindert falsche JAR-Namen und kaputte scp-Pfade beim Deployment.
  Wird bei jedem Deployment-Befehl angewendet.
alwaysApply: true
---

Das Plugin-JAR heißt **Seasons-0.1.0-SNAPSHOT.jar** (nicht VillagerAI). Der Build-Pfad ist `build\libs\Seasons-0.1.0-SNAPSHOT.jar`. Deployment-Befehle in PowerShell mit **relativen Pfaden ohne Anführungszeichen**. Server-Ziel: `mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`. Config-Ziel: `mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/config.yml"`.