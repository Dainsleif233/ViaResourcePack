# ViaResourcePack

[English](README.en.md) | [中文](README.md)

Fabric 客户端 mod：加载资源包时自动把与当前游戏版本不匹配的资源包转换成当前版本，本地包与服务器包都覆盖。

支持 Minecraft **1.14.4 – 26.2**，单个 jar 通用。

## 它做什么

把一个 1.12 时代的资源包丢进 `resourcepacks/`，在 1.21 里它照样能用；连服务器下发的旧版本资源包也会先转换再加载。原版那句「此资源包为旧版本 Minecraft 制作」的警告不会再让你只能看着材质错乱。

- **本地包**：`resourcepacks/` 目录下的 zip 与文件夹包。**在资源包界面里勾选并确认后**才转换——只是打开界面浏览不会触发，否则目录里放了很多旧包时进一次界面就要等很久。
- **服务器包**：服务器下发的包，包括**哈希命中本地缓存、不重新下载**的情况。在 1.20.2 及更早的版本上还会跳过原版的重复下载：那几代按 URL 而不是内容哈希做本地去重，URL 带 token 的服务器每次进服都要重下一遍整包。
- **单人世界捆绑包**：`level/resourcepacks/resources.zip`。
- 转换结果会缓存，同一个包只转一次。

为 1.21.9 及更新版本制作的资源包（只写 `min_format` / `max_format`）在老客户端上原本连列表都进不去——原版读不出 `pack_format` 会直接丢掉整个包。本 mod 会为这类未启用的包生成占位条目，让它带着真实的名字、说明和图标出现在列表里，勾选后再转换。

## 安装

需要 Fabric Loader ≥ 0.15.0，**不需要** Fabric API。

把 `ViaResourcePack-<version>.jar` 放进 `mods/` 即可——版本适配器和转换库都嵌在这一个 jar 里。

## 配置

首次启动后生成 `<游戏目录>/config/viaresourcepack/config.json`：

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

| 键                        | 说明                                                           |
|---------------------------|----------------------------------------------------------------|
| `enabled`                 | 总开关。关掉后所有拦截点直接放行                               |
| `convertLocalPacks`       | 是否转换 `resourcepacks/` 下的本地包                           |
| `convertServerPacks`      | 是否转换服务器 / 世界下发的包                                  |
| `onlyConvertEnabledPacks` | 只转换已启用的本地包。关掉后打开资源包界面时整个目录都会被转换 |
| `maxCacheMegabytes`       | 缓存上限，超出后按创建时间从旧到新淘汰                         |
| `verbose`                 | 打开转换库自身的详细日志                                       |

缓存与临时文件都在 `<游戏目录>/config/viaresourcepack/` 下，可以随时整个删掉（只是下次要重新转换）。

## 兼容性

- **加速资源包加载的 mod**（如 PackForge）：兼容。本 mod 只替换「原版要打开哪个文件」，不碰 `FilePackResources` 内部，因此那类 mod 照常对转换后的包建索引。
- **数据包**：不处理。
- **内置包**（vanilla、Programmer Art、高对比度）：不处理。

## 已知限制

转换由 [ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter) 完成，它的转换器覆盖到 pack_format 75（1.21.11）。**在 26.1 / 26.2 上运行时，资源包会被转换到 1.21.11 的形态**，再把 `pack.mcmeta` 改写成当前版本的格式；1.21.11 之后的资源结构变化不会被自动处理，个别资源仍可能显示异常。

跨越多个大版本（例如 1.7 → 26.2）时，部分资源本身就无法自动迁移。

首次转换会阻塞资源加载，超大包会明显变慢；之后走缓存。

## 构建

需要 JDK 25（构建产出 Java 8 字节码）。

```bash
./gradlew build              # 全部适配器 + 嵌套库 + universal jar
./gradlew verifyUniversalJar # 校验 universal jar 的嵌套结构
./gradlew test :library:test # 单元测试
```

产物：`build/libs/ViaResourcePack-<version>.jar`。

架构与各版本拦截点的详细分析见 [docs/DESIGN.md](docs/DESIGN.md)；给 AI 助手/维护者的快速上手见 [AGENTS.md](AGENTS.md)。

## 许可

GPL-3.0-or-later，见 [LICENSE](LICENSE)。

嵌套的 [ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter)（作者 agentdid127）以 MIT 许可分发。
