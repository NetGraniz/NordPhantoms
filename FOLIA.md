# Paper / Folia compatibility — 1.1.0

Use `NordPhantoms-1.1.0.jar` with JDK 25 on Paper 26.2 or Folia 26.2.

Player-owned spawn timers and phantom-owned movement timers replace global entity scans. EntitiesLoadEvent restores loaded End phantoms. Folia density checks use spatially indexed snapshots updated at the movement interval, so density is approximate between updates; Paper retains live nearby-entity counts. Candidates crossing unowned or unloaded chunks are skipped without forced loads or foreign reads. Normal startup and plugin reload are supported; hot-loading is not. Aggression PDC and configuration values are unchanged.

Run `mvn clean verify` before packaging the release JAR.

Back up configuration/player data, stop the server and replace only the JAR without duplicates. Preserve installed data and working config; repository templates are not migration scripts. No production worlds or data are migrated by this release.
