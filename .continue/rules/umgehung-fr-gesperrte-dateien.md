---
alwaysApply: true
---

Wenn read_file oder edit_existing_file bei einer Datei versagen (gesperrt, kein Zugriff), sofort auf filesystem_read_file / filesystem_read_text_file / filesystem_write_file / filesystem_edit_file ausweichen. read_file nicht wiederholt auf derselben Datei aufrufen, wenn es einmal versagt hat.