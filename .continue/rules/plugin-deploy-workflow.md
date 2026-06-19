---
description: "Anwenden nach jeder Code-Änderung, wenn das Plugin auf den
  Minecraft-Server deployed werden soll. Deckt Java-Code und
  YAML-Config-Änderungen ab."
alwaysApply: false
---

1. IMMER: JAR neu bauen mit .\gradlew.bat shadowJar -x test
2. Plugin-JAR kopieren: scp "build\libs\Seasons-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
3. NUR wenn YAML-Configs geändert wurden, zusätzlich kopieren z.B.: scp "src\main\resources\config.yml" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/config.yml"
4. IMMER nach Plugin-Deploy: ssh mc@10.0.0.86 "sudo systemctl restart crafty" (KEIN Plugin-Reload verwenden).
