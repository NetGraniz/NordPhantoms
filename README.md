# NordPhantoms

> Release build and installation requirements: see [BUILDING.md](BUILDING.md).
> Older local paths below describe historical test fixtures, not the release build.

Small Paper 26.2 plugin implementing Nord Fjell's phantom rules:

- no phantom spawning in the Overworld;
- custom spawning over natural terrain in The End, away from player bases;
- passive and silent until attacked by a player;
- persistent aggression state across chunk unloads and restarts;
- lightweight chorus-tree avoidance and stuck recovery.

Admin command: `/nordphantoms reload` (`nordphantoms.admin`).

The behavior was inspired by MightyFinger77's open-source PassivePhantoms project:
https://github.com/MightyFinger77/PassivePhantoms
