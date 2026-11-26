# NordPhantoms 1.1.1

Phantom spawning and behavior for Paper 26.2 and Folia 26.2. One JAR supports both platforms on Java 25.

## Behavior

- Prevents phantom spawning in the Overworld.
- Adds custom spawning over natural End terrain, away from player bases.
- Keeps phantoms passive and silent until a player attacks them.
- Saves aggression across chunk unloads and server restarts.
- Avoids chorus trees and helps stuck phantoms recover.

## Configuration

Edit `plugins/NordPhantoms/config.yml`, then run `/nordphantoms reload`.

## Permissions

| Permission | Allows | Default |
| --- | --- | --- |
| `nordphantoms.admin` | `/nordphantoms reload` | Operators |

Normal spawning and phantom behavior do not require player permissions.

## Build and installation

Use Maven 3.9+ and JDK 25. See [BUILDING.md](BUILDING.md) for build instructions and [FOLIA.md](FOLIA.md) for platform support. Install the release JAR on a stopped server.

## License

Current development on `main` is licensed under [GNU GPL v3.0 only](LICENSE). The latest published release remains available under the MIT License stated on its release page.
