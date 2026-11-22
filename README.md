# ViaResourcePack

[English](README.en.md) | [中文](README.md)

Fabric 客户端 mod：用内置迁移规则把旧纹理包和旧资源包升级到正在运行的游戏格式，原包保持不变。

一个 jar 支持 Minecraft **1.14.4–26.3**。2.0 **不向下转换**：包比客户端更新时，请换用更高版本的客户端或下载旧版包。

## 它做什么

- **本地包**：支持 ZIP 和文件夹。默认在启用并确认时升级，浏览目录不会批量转换。可识别的旧纹理包没有 `pack.mcmeta` 也会显示可选占位条目。有效缓存会直接作为兼容包显示，即使该包还未启用；仅浏览列表不会为缓存未命中的包生成新产物。
- **服务器与世界捆绑包**：在原版取得本地文件后处理，包含原版缓存命中。1.20.2 及更早客户端还能按服务器声明的内容哈希复用已升级产物，避免重复下载。
- **按资源包格式迁移**：区分现代格式的主、次版本。格式目录记录到 26.4-s10（104.0），但 26.3 的目标格式是 97.1，更新的快照包不会被降到 97.1。
- **缓存**：升级结果按源内容、目标格式和迁移规则区分。失败时保留原包并记录日志。

## 安装

需要 Fabric Loader ≥ 0.15.0，**不需要** Fabric API。只有装有 Fabric API 的资源加载模块时，ViaResourcePack 才会替换旧资源包的菜单提示。

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
| `verbose`                 | 新转换时在普通游戏日志中记录经过的格式节点                       |

转换缓存 `cache/`、临时工作目录 `work/` 和占位包 `stubs/` 默认都在 `<游戏目录>/resourcepacks/.viaresourcepack/`；`<游戏目录>/config/viaresourcepack/` 只写配置。之前的目录不会自动搬移或删除。`.viaresourcepack` 是模组私有目录，没有根 `pack.mcmeta`，不会作为资源包显示。请不要把资源包放在这里；如果该名称已被其他文件、目录或链接占用，模组不会接管或删除它。退出游戏后可删除当前 `cache/`，下次会重新转换。

旧的 `config/viaresourcepack/cache/` 不会被自动移动或删除。JVM 参数 `-Dviarp.cacheDir=<目录>` 保留原有行为：配置、临时文件和 `cache/` 都使用指定目录作为共同根。不要将该参数指向资源包扫描目录或其中的包。若要继续把缓存放在旧位置，将该参数指向 `<游戏目录>/config/viaresourcepack`（不是它下面的 `cache/`）。旧产物仍须匹配源内容、目标格式和本版迁移规则；不匹配的会重新转换。

## 兼容性

- **加速资源包加载的 mod**（如 PackForge）：本模组只替换原版打开的路径，不修改资源包读取器。具体其他模组组合尚未验证。
- **数据包**：不处理。
- **内置包**（vanilla、Programmer Art、高对比度）：不处理。

## 已知限制

只能自动迁移有明确对应关系的内容。旧纹理图集中未映射的格子、被移除的资源，以及部分自定义模型、着色器和其他模组格式，可能需要手动调整。日志会报告已发现但未处理的情况，并给出缓存条目内 `warnings.txt` 的位置；复用缓存时仍可查看此报告。

从资源包菜单点「完成」后，本地包会后台并行准备，界面显示批次进度、正在处理的包和失败名单；成功项按原顺序交给原版。启动与 F3+T 仍用同步路径；原版自身的资源重载也可能短暂停顿。若 26.x 的原版日志提示某包已兼容并移除了旧的不兼容标记，但本轮未加载，在资源包界面重新启用它，或正常退出后重启。

### 怎么确认升级结果？

查看游戏的 `logs/latest.log`：

- `Upgraded … -> …`：升级产物已写入缓存。如果只有部分素材异常，先查看同一条目中的 `warnings.txt`；没有报告不代表所有素材都已验证。
- `Not upgrading`：没有适用的向上路线。比如在 26.3 打开格式 104 的包，请改用适配该包的客户端或旧版包。
- `Failed to convert …; loading it unchanged`：整包没有转换，原版拿到原始路径。日志会记录失败原因。

## 构建

使用 JDK 25 和仓库自带的 Gradle wrapper：

```bash
./gradlew build                       # 编译、测试、打包
./gradlew universal verifyUniversalJar # 打包并检查八个嵌套适配器
./gradlew test                        # 单元测试
```

产物：`build/libs/ViaResourcePack-<version>.jar`。

详细设计与拦截点见 [docs/DESIGN.md](docs/DESIGN.md)；维护者指南见 [AGENTS.md](AGENTS.md)。

## 许可

GPL-3.0-or-later，见 [LICENSE](LICENSE)。
