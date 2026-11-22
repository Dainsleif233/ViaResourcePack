# ViaResourcePack

[English](README.en.md) | [中文](README.md)

A Fabric client mod that lets Minecraft load resource packs made for earlier versions. Supports **1.14.4–26.3**.

## Compatibility

- **Resource-pack loading mods** (such as PackForge): compatibility with individual mods has not been verified.
- **Data packs**: not supported.
- **Built-in packs** (vanilla, Programmer Art, High Contrast): not modified.

## Known limitations

Only assets with known equivalents can be migrated automatically. Unmapped cells in old texture atlases, removed resources, and some custom models, shaders, or other mod-specific formats may need manual changes. The game log reports detected issues and points to the cache entry’s `warnings.txt` report. The report remains available when the cached pack is reused.

### How can I check the result?

Open the game’s `logs/latest.log`:

- `Upgraded … -> …`: a converted copy was saved to the cache. If only some assets look wrong, check `warnings.txt` in the same cache entry. The absence of a report does not guarantee that every asset works.
- `Not upgrading`: there is no supported upgrade path. For example, to use a format 104 pack, choose a compatible client or download an older release of the pack rather than trying to downgrade it to 26.3.
- `Failed to convert …; loading it unchanged`: the pack was not converted. Minecraft will try to load it as-is; the log includes the reason.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
