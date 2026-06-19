---
description: Anwenden nach jeder Code-Änderung, wenn das Plugin auf den
  Minecraft-Server deployed werden soll. Deckt Java-Code und
  YAML-Config-Änderungen ab.
alwaysApply: false
---

Deployment-Befehle für das Seasons-Plugin: User: mc, Server: 10.0.0.86, Basis-Pfad: /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/. JAR-Name: Seasons-0.1.0-SNAPSHOT.jar. Config-Pfad: src/main/resources/config.yml. Vor jedem Deploy: compileJava + shadowJar. Nach dem Deploy: Server neustarten (ssh mc@10.0.0.86 "sudo systemctl restart crafty" oder ähnlich – der Nutzer kennt den exakten Restart-Befehl).