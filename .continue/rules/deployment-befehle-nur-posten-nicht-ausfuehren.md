---
globs: "**/*"
description: Verhindert, dass SSH- oder SCP-Befehle automatisch ausgeführt
  werden, da diese Passwortabfragen benötigen und den Agent blockieren könnten.
alwaysApply: true
---

Deployment-Befehle (scp/ssh) NIEMALS selbst ausführen. Nur die Befehle im Klartext posten. Der Nutzer führt sie manuell aus.