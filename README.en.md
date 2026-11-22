# ViaResourcePack

[English](README.en.md) | [中文](README.md)

A Fabric **client-side** mod that upgrades old texture packs and resource packs with built-in migration rules. Original packs are not modified.

A single jar supports Minecraft **1.14.4–26.3**. Version 2.0 **does not downgrade packs**. If a pack is newer than your client, use a newer client or download an older version of the pack.

## What it does

- **Local packs:** Supports ZIP and folder packs. By default, upgrade runs when you enable a pack and confirm, not when you browse the list. Recognized texture packs without `pack.mcmeta` get a selectable placeholder. A valid cached result appears as compatible even while the pack is disabled. Browsing the list does not rebuild a cache miss.
- **Server and world-bundled packs:** Upgrades run after vanilla has the file, including vanilla cache hits. On clients through 1.20.2, a previously upgraded pack can also be reused directly by the server's declared content hash.
- **Migrations by pack format:** Modern major and minor versions are distinct. The catalog includes 26.4-s10 (104.0), but the 26.3 target is 97.1. Newer snapshot packs are not downgraded to it.
- **Caching:** Results are separated by source contents, target format, and migration rules. Failed upgrades leave the original unchanged and are logged.

## Installation

Requires Fabric Loader ≥ 0.15.0. **Fabric API is not required.** ViaResourcePack replaces the menu warning for older resource packs only when the Fabric API resource loader is installed.

Drop `ViaResourcePack-<version>.jar` into `mods/` — the jar includes all version adapters. No separate converter is needed.

## Configuration

After the first launch a config file is generated at `<game dir>/config/viaresourcepack/config.json`:

```json
{
  "enabled": true,
  "convertLocalPacks": true,
  "convertServerPacks": true,
  "onlyConvertEnabledPacks": true,
  "maxCacheMegabytes": 4096,
  "verbose": false
}
```

| Key                       | Description                                                                                                                           |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `enabled`                 | Master switch. When off, every interception point passes packs through untouched                                                      |
| `convertLocalPacks`       | Convert local packs in `resourcepacks/`                                                                                               |
| `convertServerPacks`      | Convert server- / world-provided packs                                                                                                |
| `onlyConvertEnabledPacks` | Convert only local packs that are enabled. Turning this off makes the whole directory convert when the resource pack screen is opened |
| `maxCacheMegabytes`       | Cache size limit; old entries are evicted by creation time when exceeded                                                              |
| `verbose`                 | Show the format migration route in the normal game log when a pack is converted                                                       |

By default, `cache/`, temporary `work/`, and placeholder `stubs/` all live under `<game dir>/resourcepacks/.viaresourcepack/`. Only configuration is written under `<game dir>/config/viaresourcepack/`. Existing directories are not moved or deleted automatically. The mod's private `.viaresourcepack` directory has no root `pack.mcmeta`, so it does not show up as a pack. Do not put packs there: if that name is already used by another file, directory, or link, the mod will neither take it over nor delete it. You can delete the active `cache/` after exiting Minecraft; needed outputs will be rebuilt.

The old `config/viaresourcepack/cache/` is never moved or deleted automatically. The JVM option `-Dviarp.cacheDir=<directory>` keeps its previous behavior: it sets the common root for configuration, temporary files, and `cache/`. Do not point the option at the scanned resource-pack directory or one of its children. To keep the old cache location, point it at `<game dir>/config/viaresourcepack` (not its `cache/` subdirectory). Cached results still need to match the source contents, target format, and current migration rules.

## Compatibility

- **Mods that accelerate resource pack loading** (e.g. PackForge): this mod replaces the path vanilla opens rather than changing the pack reader. Specific combinations with other mods have not been verified.
- **Data packs**: out of scope.
- **Built-in packs** (vanilla, Programmer Art, High Contrast): untouched.

## Known limitations

Only assets with known equivalents can be migrated automatically. Unmapped cells in old texture atlases, removed resources, and some custom models, shaders and mod-specific formats may need manual work. The log lists detected issues and points to `warnings.txt` next to the cached pack, where the report remains available on cache hits.

Clicking Done in the pack screen prepares local packs on background workers. The screen shows batch progress, the pack being processed, and any failed pack names. Successfully prepared packs retain their vanilla order. Startup and F3+T still use the synchronous path, and Minecraft’s own reload may still pause briefly. If Minecraft 26.x logs that it removed a stale incompatibility flag but does not load the pack in that pass, re-enable it in the pack screen or exit normally and restart.

### How can I check the result?

Look in the game’s `logs/latest.log`:

- `Upgraded … -> …`: a converted copy was written to the cache. If only some assets look wrong, check `warnings.txt` next to the cached copy. No report does not mean every asset was verified.
- `Not upgrading`: there is no supported upgrade path. For example, use a client that supports format 104 or an older release of that pack instead of trying to downgrade it to 26.3.
- `Failed to convert …; loading it unchanged`: conversion failed and vanilla received the original path. The log includes the reason.

## Building

Use JDK 25 and the repository's Gradle wrapper:

```bash
./gradlew build                       # compile, test and package
./gradlew universal verifyUniversalJar # package and check eight nested adapters
./gradlew test                        # unit tests
```

Artifact: `build/libs/ViaResourcePack-<version>.jar`.

See [docs/DESIGN.md](docs/DESIGN.md) for design and version-specific hooks, or [AGENTS.md](AGENTS.md) for maintenance guidance.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
