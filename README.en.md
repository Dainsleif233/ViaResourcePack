# ViaResourcePack

[English](README.en.md) | [中文](README.md)

A Fabric **client-side** mod that automatically converts resource packs to the running Minecraft version when they are loaded. Both local and server-provided packs are covered.

Supports Minecraft **1.14.4 – 26.2** with a single universal jar.

## What it does

Drop a 1.12-era resource pack into `resourcepacks/` and it just works in 1.21 — even old server-sent packs are converted before being loaded. The vanilla "made for an older version of Minecraft" warning will no longer leave you're staring at broken textures.

- **Local packs**: zip and folder packs in `resourcepacks/`. Conversion happens **only after you enable the pack and confirm** in the resource pack screen — merely browsing the screen does not trigger it (otherwise opening the screen with many old packs in the folder would stall for minutes).
- **Server packs**: packs sent by the server, including the **hash-cache hit case where nothing is re-downloaded**. On 1.20.2 and earlier it also skips vanilla's redundant downloads: those versions deduplicate by **URL**, not by content hash, so servers whose URLs carry changing tokens force a full re-download on every join.
- **World-bundled packs**: `level/resourcepacks/resources.zip` in singleplayer.
- Conversion results are cached; the same pack is converted only once.

Resource packs made for 1.21.9+ (which declare only `min_format` / `max_format`) could not even appear in the list on older clients — vanilla throws away the whole pack when `pack_format` is missing. This mod generates a placeholder entry for such not-yet-enabled packs so they show up with their real name, description and icon, and are converted once selected.

## Installation

Requires Fabric Loader ≥ 0.15.0. **Fabric API is not required.**

Drop `ViaResourcePack-<version>.jar` into `mods/` — the version adapters and the conversion library are all embedded in this single jar.

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
| `verbose`                 | Enable the conversion library's own verbose logging                                                                                   |

Caches and temporary files live under `<game dir>/config/viaresourcepack/`; you may delete the whole directory at any time (conversions will just run again next time).

## Compatibility

- **Mods that accelerate resource pack loading** (e.g. PackForge): compatible. This mod only swaps *which file* vanilla opens; it never touches the internals of `FilePackResources`, so such mods index our converted packs as usual.
- **Data packs**: out of scope.
- **Built-in packs** (vanilla, Programmer Art, High Contrast): untouched.

## Known limitations

Conversion is done by [ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter), whose converters cover up to pack_format 75 (1.21.11). **On 26.1 / 26.2, packs are converted to the 1.21.11 shape** and then `pack.mcmeta` is rewritten to declare the current format; structural changes between 1.21.11 and 26.x are not handled automatically, so individual assets may still look wrong.

When jumping across many major versions (e.g. 1.7 → 26.2), some assets simply cannot be migrated automatically.

The first conversion blocks resource loading and large packs will be noticeably slow; afterward everything comes from the cache.

## Building

Requires JDK 25 (the build emits Java 8 bytecode).

```bash
./gradlew build              # all adapters + nested library + universal jar
./gradlew verifyUniversalJar # verify the universal jar's nesting structure
./gradlew test :library:test # unit tests
```

Artifact: `build/libs/ViaResourcePack-<version>.jar`.

For a deep dive into the architecture and per-version interception points, see [docs/DESIGN.md](docs/DESIGN.md); for a quick start aimed at AI assistants and maintainers, see [AGENTS.md](AGENTS.md).

## License

GPL-3.0-or-later, see [LICENSE](LICENSE).

The bundled [ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter) (by agentdid127) is distributed under the MIT license.
