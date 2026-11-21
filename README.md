# ViaResourcePack

[English](README.en.md) | [中文](README.md)

Fabric 客户端 mod：用内置迁移规则把旧纹理包和旧资源包升级到正在运行的游戏格式，原包保持不变。

一个 jar 支持 Minecraft **1.14.4–26.3**。2.0 **不向下转换**：包比客户端更新时，请换用更高版本的客户端或下载旧版包。

## 它做什么

- **本地包**：支持 ZIP 和文件夹。默认在启用并确认时升级，浏览目录不会批量转换。可识别的旧纹理包没有 `pack.mcmeta` 也会显示可选占位条目。
- **服务器与世界捆绑包**：在原版取得本地文件后处理，包含原版缓存命中。1.20.2 及更早客户端还能按服务器声明的内容哈希复用已升级产物，避免重复下载。
- **按资源包格式迁移**：区分现代格式的主、次版本。格式目录记录到 26.4-s10（104.0），但 26.3 的目标格式是 97.1，更新的快照包不会被降到 97.1。
- **缓存**：升级结果按源内容、目标格式和迁移规则区分。失败时保留原包并记录日志。

## 安装

需要 Fabric Loader ≥ 0.15.0，**不需要** Fabric API。

把 `ViaResourcePack-<version>.jar` 放进 `mods/` 即可——这个 jar 已包含各版本适配器，不需要另装转换库。

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
| `verbose`                 | 记录经过的迁移节点                                       |

缓存与临时文件都在 `<游戏目录>/config/viaresourcepack/` 下，退出游戏后可删除 `cache/`（下次会重新转换）。

## 兼容性

- **加速资源包加载的 mod**（如 PackForge）：兼容。本 mod 只替换「原版要打开哪个文件」，不碰 `FilePackResources` 内部，因此那类 mod 照常对转换后的包建索引。
- **数据包**：不处理。
- **内置包**（vanilla、Programmer Art、高对比度）：不处理。

## 已知限制

只能自动迁移有明确对应关系的内容。旧纹理图集中未映射的格子、被移除的资源，以及部分自定义模型、着色器和其他模组格式，可能需要手动调整。日志会报告已发现但未处理的情况。

首次升级在资源加载中同步执行，大包可能让游戏暂停片刻；后续加载可复用升级结果。若 26.x 的原版日志提示某包已兼容并移除了旧的不兼容标记，但本轮未加载，在资源包界面重新启用它，或正常退出后重启。

## 构建

使用 JDK 25 和仓库自带的 Gradle wrapper：

```bash
./gradlew build                       # 编译、测试、打包
./gradlew universal verifyUniversalJar # 打包并检查七个嵌套适配器
./gradlew test                        # 单元测试
```

产物：`build/libs/ViaResourcePack-<version>.jar`。

详细设计与拦截点见 [docs/DESIGN.md](docs/DESIGN.md)；维护者指南见 [AGENTS.md](AGENTS.md)。

## 许可

GPL-3.0-or-later，见 [LICENSE](LICENSE)。
