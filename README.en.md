# ViaResourcePack

[English](README.en.md) | [中文](README.md)

A Fabric **client-side** mod that upgrades old texture packs and resource packs with built-in migration rules. Original packs are not modified.

A single jar supports Minecraft **1.14.4–26.3**. Version 2.0 **does not downgrade packs**. If a pack is newer than your client, use a newer client or download an older version of the pack.

## What it does

- **Local packs:** Supports ZIP and folder packs. By default, upgrade runs when you enable a pack and confirm, not when you browse the list. Recognized texture packs without `pack.mcmeta` get a selectable placeholder.
- **Server and world-bundled packs:** Upgrades run after vanilla has the file, including vanilla cache hits. On clients through 1.20.2, a previously upgraded pack can also be reused directly by the server's declared content hash.
- **Migrations by pack format:** Modern major and minor versions are distinct. The catalog includes 26.4-s10 (104.0), but the 26.3 target is 97.1. Newer snapshot packs are not downgraded to it.
- **Caching:** Results are separated by source contents, target format, and migration rules. Failed upgrades leave the original unchanged and are logged.

## Installation

Requires Fabric Loader ≥ 0.15.0. **Fabric API is not required.**

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
| `verbose`                 | Log the pack format migration route                                                                                   |

Caches and temporary files live under `<game dir>/config/viaresourcepack/`; you can delete `cache/` after exiting Minecraft; the next launch will re-create needed outputs.

## Compatibility

- **Mods that accelerate resource pack loading** (e.g. PackForge): compatible. This mod only swaps *which file* vanilla opens; it never touches the internals of `FilePackResources`, so such mods index our converted packs as usual.
- **Data packs**: out of scope.
- **Built-in packs** (vanilla, Programmer Art, High Contrast): untouched.

## Known limitations

Only assets with known equivalents can be migrated automatically. Unmapped cells in old texture atlases, removed resources, and some custom models, shaders and mod-specific formats may need manual work. The log lists detected issues and points to `warnings.txt` next to the cached pack, where the report remains available on cache hits.

The first upgrade runs during resource loading and may pause the game for large packs. Later loads can reuse the cached result. If Minecraft 26.x logs that it removed a stale incompatibility flag but does not load the pack in that pass, re-enable it in the pack screen or exit normally and restart.

## Building

Use JDK 25 and the repository's Gradle wrapper:

```bash
./gradlew build                       # compile, test and package
./gradlew universal verifyUniversalJar # package and check seven nested adapters
./gradlew test                        # unit tests
```

Artifact: `build/libs/ViaResourcePack-<version>.jar`.

See [docs/DESIGN.md](docs/DESIGN.md) for design and version-specific hooks, or [AGENTS.md](AGENTS.md) for maintenance guidance.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
