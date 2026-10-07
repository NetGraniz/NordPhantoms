# Paper / Folia compatibility — 1.1.0

Use `NordPhantoms-1.1.0.jar` with JDK 25 on Paper 26.2 or Folia 26.2.

Player-owned spawn timers and phantom-owned movement timers replace global entity scans. EntitiesLoadEvent restores loaded End phantoms. Folia density checks use spatially indexed snapshots updated at the movement interval, so density is approximate between updates; Paper retains live nearby-entity counts. Candidates crossing unowned or unloaded chunks are skipped without forced loads or foreign reads. Normal startup and plugin reload are supported; hot-loading is not. Aggression PDC and configuration values are unchanged.

Run `mvn clean verify` first. The [isolated runtime harness](https://github.com/NetGraniz/NordChat/tree/main/test-support) exercises all eight plugins together with synthetic loopback clients and local HTTPS. Never deploy its helper JAR on a real server. Results are saved outside the repository. This is not a 1000-player load test or a future-version guarantee.

Back up configuration/player data, stop the server and replace only the JAR without duplicates. Preserve installed data and working config; repository templates are not migration scripts. No production worlds or data are migrated by this release.
