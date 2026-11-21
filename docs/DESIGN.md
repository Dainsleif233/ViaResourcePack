# ViaResourcePack 设计文档

> Fabric 客户端 mod：加载资源包时自动把与当前游戏版本不匹配的包转换成当前版本，再交给原版加载。
> 包名 `top.syshub.viarp`，作者 Dainsleif，GPL-3.0-or-later，支持 MC 1.14.4 – 26.3。

---

## 1. 拦截点

### 1.1 为什么替换「路径」而不是包装「资源包对象」

| 方案                                 | 位置                                                          | 问题                                                                                                                                                                                                                                                                                                      |
|--------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A. 包装 `PackResources`              | `Pack.ResourcesSupplier.open*()` 的返回值                     | 要为每个版本实现一整套 `PackResources` 代理。这个接口在区间内大改过 5 次（1.14.4 的 `getResource(String)` → 1.19.3 的 `IoSupplier` → 1.20.2 的 `SharedZipFileAccess` → 1.20.5 的 `PackLocationInfo` → 1.21.9 的 `PackFormat`），而且会与「在 `FilePackResources` 内部做索引/句柄池」的加速类 mod 正面冲突 |
| B. 替换 `PackRepository` 里的 `Pack` | `RepositorySource.loadPacks` 回调                             | 要自己构造 `Pack`，其构造签名在区间内变了 6 次；且服务器包走 `DownloadedPackSource` 另一条路，得再写一套                                                                                                                                                                                                  |
| **C. 替换资源包文件路径**（采用）    | 原版**拿到了**「资源包文件/目录路径」但**还没打开它**的那一刻 | 只改一个 `Path`/`File` 参数                                                                                                                                                                                                                                                                               |

选 C 的三个理由：

1. **兼容加速解压类 mod**。PackForge 这类 mod 的 hook 全在 `FilePackResources` 与 `FilePackResources$SharedZipFileAccess` 内部（`getResource` 里的 `ZipFile.getEntry`、`listResources` 里的 `entries()`、ZIP 读句柄池）。我们不碰这些类，只改「打开哪个文件」，因此它们照常对我们产出的转换后 zip 建索引——两者叠加生效，而不是互斥。
2. **跨版本改动面最小**。本地拦截替换的参数在 1.14.4 – 26.3 全程只是 `java.io.File` 或 `java.nio.file.Path`，除了 File→Path 之外没有语义变化。原版后续的 symlink 校验、zip/目录判定、`pack.mcmeta` 读取、`PackResources` 构造全都自然作用在新文件上。
3. **天然覆盖服务器包的哈希缓存命中**。见 §1.3。

### 1.2 本地资源包（`resourcepacks/`）

原版发现链：

```
PackRepository.reload()
 └─ FolderRepositorySource.loadPacks(Consumer<Pack>)
     └─ discoverPacks(folder, ...)                      遍历目录
         └─ detectPackResources(path, ...)              判定 zip / 目录   ← 拦截点
             ├─ createZipPack(path)       → FilePackResources.FileResourcesSupplier
             └─ createDirectoryPack(path) → PathPackResources.PathResourcesSupplier
     └─ Pack.readMetaAndCreate(...) → output.accept(pack)
```

拦截点随版本落在三处（混淆版本用官方映射表 + intermediary v2 交叉验证；26.1+ 直接使用游戏 jar 中的类名和方法名；`localHook` 是 `gradle/adapters.toml` 里的取值）：

| MC 区间         | `localHook`      | 目标方法                                                                          | 描述符                                                     |
|-----------------|------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------|
| 1.14.4 – 1.19.2 | `createSupplier` | `class_3279.method_14432`（`FolderRepositorySource.createSupplier`）              | `(Ljava/io/File;)Ljava/util/function/Supplier;`            |
| 1.19.3 – 1.20.1 | `folderDetector` | `class_3279.method_45269`（`FolderRepositorySource.detectPackResources`，static） | `(Ljava/nio/file/Path;Z)Lclass_3288$class_7680;`           |
| 1.20.2 – 1.21.11 | `packDetector`   | `class_8621.method_52441`（`PackDetector.detectPackResources`）                   | `(Ljava/nio/file/Path;Ljava/util/List;)Ljava/lang/Object;` |
| 26.1 – 26.3      | `packDetector`   | `net.minecraft.server.packs.repository.PackDetector.detectPackResources`        | `(Ljava/nio/file/Path;Ljava/util/List;)Ljava/lang/Object;` |

`PackDetector` 是 1.20.2 起本地包探测的共用入口。Mojang 名和擦除后的描述符在 1.20.2 / 1.20.5 / 1.21.4 / 1.21.9 / 1.21.11 / 26.1 / 26.2 / 26.3 上核对一致；26.1 – 26.3 的 `PackDetector` 和调用方 `FolderRepositorySource` 的参考源码内容相同（版本来源见 §2）。

**旧目录的预筛选**：File 家族先用 `class_3279.method_14431(Ljava/io/File;)Z` 过滤候选（目录必须存在根 `pack.mcmeta`），然后才计算原始 `file/<文件名>` 与调用 `createSupplier`。同一个 local mixin 在该静态筛选方法入口只替换参数：对没有元数据、可识别且可升版的旧目录，交给原版谓词检查占位路径。目录遍历仍返回原 File，包 ID 不变，真正的升版仍在 `createSupplier` 判定是否启用后才发生。已交叉核对 1.14.4 / 1.15.2 / 1.16 / 1.18.2 / 1.19.2 的原版字节码与 intermediary 指纹。Path 家族的拦截点位于探测入口，不需要这次预筛选替换。

**类型守卫**：`PackDetector` 不知道自己在探测资源包还是数据包。判据是**目录归属**——只有 `path.getParent()` 恰好等于客户端 `resourcepacks` 目录时才转换（`ViaRpPaths.isInResourcePacksDir`）。世界数据包（`<world>/datapacks`）、`ServerPacksSource`、内置包因此自然被排除，不需要读调用栈或猜 `PackType`。

### 1.2.1 「安装时」而不是「列出时」

上面这个拦截点会在三种场合被触发：启动、F3+T、以及**打开资源包选择界面**（`PackSelectionModel.findNewPacks` → `PackRepository.reload`，1.14.4 的 `ResourcePackSelectScreen.init` 里也是直接 `packRepository.reload()`）。如果在拦截点无条件转换，玩家只是进界面看一眼，`resourcepacks/` 里所有版本不匹配的包就会被全部转换一遍——目录里放了十几个旧包时第一次进界面能卡上几分钟，而且绝大多数根本不会被启用。

判据必须来自 core（不能引用 `net.minecraft`），能用的信号是 **`options.txt` 的 `resourcePacks` 行**。玩家点「完成」时原版的顺序是：

```
PackSelectionScreen.onClose
 └─ PackSelectionModel.commit → OptionsScreen.applyPacks
     └─ Options.updateResourcePacks(repo)
         ├─ 按 repo.getSelectedPacks() 重写 resourcePacks / incompatibleResourcePacks
         ├─ save()                      ← 先落盘
         └─ Minecraft.reloadResourcePacks()
              └─ PackRepository.reload() → 拦截点     ← 读到的已经是新列表
```

`save()` 排在 `reloadResourcePacks()` **之前**，这个顺序在 1.14.4 – 26.3 全程未变（1.14.4 是 `ResourcePackSelectScreen` 的 Done 按钮里手写的 `options.save()` + `reloadResourcePacks()`，1.16+ 收敛进 `Options.updateResourcePacks`）。因此拦截点读 `options.txt` 一定能看到玩家刚勾上的包；反之只是浏览界面时文件不变，未启用的包自然不转换。

`PackSelection` 按 mtime + 大小缓存这一行，界面里改完选择会自动重读。包 id 认 `file/<文件名>`（1.14.4 起 `FolderRepositorySource` 固定这个前缀）与裸文件名两种形态——`Options.loadSelectedResourcePacks` 至今仍兼容后者。匹配 `resourcePacks:` 时必须精确比键名：`incompatibleResourcePacks` 以同样的子串结尾。

想让整个目录提前转好可以把 `config.json` 的 `onlyConvertEnabledPacks` 关掉。

### 1.2.2 占位包：让旧纹理包可被启用

旧纹理包没有根部 `pack.mcmeta`，原版不会列出。ViaResourcePack 只对可识别且尚未启用的旧纹理包提供占位目录 `stubs/<源文件名>/`，写目标格式的空壳元数据和提示。保存选择后，下一次仓库探测返回完整的升级产物。占位目录名与源文件名一致（包括 `.zip`）。

有有效元数据的旧资源包由原版列出；较新格式包保持原样，不用占位伪装为兼容。

### 1.3 服务器资源包

1.20.3+ 的原版链：

```
ClientCommonPacketListenerImpl.handleResourcePackPush
 └─ DownloadedPackSource.pushPack(id, url, hash)
     └─ ServerPackManager.pushPack → tick() → updateDownloads()
         └─ PackDownloader.download → DownloadQueue.runDownload
              ↑ 命中哈希缓存时直接复用 downloads/ 下已有文件，不走网络回调
     └─ triggerReloadIfNeeded() → reloadConfig.scheduleReload(callbacks)
         └─ DownloadedPackSource.startReload(callbacks)
             └─ loadRequestedPacks(List<PackReloadConfig.IdAndPath>)   ← 拦截点
                 └─ new FilePackResources.FileResourcesSupplier(path)
```

拦截 `loadRequestedPacks` 而不是 `pushPack` 或下载回调，正是为了**覆盖哈希缓存命中**：`DownloadQueue` 命中缓存时不会触发下载回调，但一定会把本地文件路径塞进 `IdAndPath` 交给 `loadRequestedPacks`。在这里把 `List<IdAndPath>` 整体替换成路径已转换的新列表，新下载与缓存命中两条路都被覆盖。单人世界的捆绑包（`WorldOpenFlows.loadBundledResourcePack` → `pushLocalPack`）走的也是同一个方法。26.3 正式客户端从 `saves/<world>/resourcepacks/resources.zip` 读取，不是存档根部。

| MC 区间 | `serverHook` | 目标方法 | 描述符 | 替换对象 |
|---|---|---|---|---|
| 1.14.4 – 1.19.2 | `setServerPack` | `class_1066.method_4638`（`ClientPackSource.setServerPack`） | `(Ljava/io/File;[Lclass_5352;])...` | `File` |
| 1.19.3 – 1.20.2 | `setServerPackDownloaded` | `class_1066.method_4638`（`net.minecraft.client.resources.DownloadedPackSource.setServerPack`） | `(Ljava/io/File;Lclass_5352;)Ljava/util/concurrent/CompletableFuture;` | `File` |
| 1.20.3 – 1.21.11 | `loadRequestedPacks` | `class_1066.method_55519`（`net.minecraft.client.resources.server.DownloadedPackSource.loadRequestedPacks`） | `(Ljava/util/List;)Ljava/util/List;` | `List<IdAndPath>` |
| 26.1 – 26.3 | `loadRequestedPacks` | `net.minecraft.client.resources.server.DownloadedPackSource.loadRequestedPacks` | `(Ljava/util/List;)Ljava/util/List;` | `List<IdAndPath>` |

两点需要留意：

- 混淆版本中 `class_1066` 这个 intermediary 编号一路沿用，但它背后的 Mojang 类名换过两次：1.14.4 – 1.19.2 是 `ClientPackSource`，1.19.3 – 1.20.2 是 `client.resources.DownloadedPackSource`（同时 `ClientPackSource` 变成了 `class_1065`），1.20.3+ 又搬到 `client.resources.server.DownloadedPackSource`。因此三个 hook 是三份 mixin，但目标类在 intermediary 下是同一个。
- 1.14.4/1.15 的 `setServerPack` 只有一个 `File` 参数，1.16 起多了 `PackSource`。mixin 用不带描述符的 `method = "setServerPack"` 按名匹配，`argsOnly = true` + `File` 参数类型确保只改到文件参数，因此两代签名共用一份 mixin。

26.3 的 `DownloadedPackSource.loadRequestedPacks` 仍用 `IdAndPath.path()` 构造 `FileResourcesSupplier`；`PackReloadConfig.IdAndPath` 仍是 `(UUID id, Path path)`，构造器和访问器不需适配。

`setServerPack` 与 `loadRequestedPacks` 都在**哈希校验之后**，所以「哈希命中 → 直接加载本地已下载包」这条路径同样被替换。

### 1.3.1 跳过重复下载（仅 1.14.4 – 1.20.2）

1.14.4 – 1.20.2 的 `downloadAndSelectResourcePack` 是这样决定要不要下载的：

```java
String key = sha1(url.toString());          // URL 的哈希，不是内容的哈希
File file = new File(serverPackDir, key);
if (file.exists()) { /* 复用 */ } else { /* HTTP 下载 */ }
checkHash(declaredHash, file);              // 之后才校验内容
```

去重键是 **URL**。很多服务器（CraftEngine、ItemsAdder 之类的插件）下发的 URL 带 token 或时间戳，URL 一变缓存必然落空，同一份内容被反复整包下载。实测在一个测试实例的 `server-resource-packs/` 里躺着 4 个文件名不同、内容 SHA-1 完全相同的包。

1.20.3+ 换成了 `HttpUtil.downloadFile`，缓存文件名直接用**声明的内容哈希**（`cachedFilePath(dir, hashCode)` + `checkExistingFile`），URL 变了照样命中，所以那一代不需要这套短路。

服务器在下发包时已经告诉了我们内容哈希，而我们的缓存键正是「源 SHA-1 + ZIP 形态 + 完整目标格式 + 规则版本」。于是三个注入点合起来把「这份内容我早转好了」告诉原版：

| 注入点                          | 方式                      | 作用                                                                          |
|---------------------------------|---------------------------|-------------------------------------------------------------------------------|
| `Pattern.matcher(CharSequence)` | `@Redirect`               | 记下 `SHA1.matcher(hash)` 里那个声明哈希，然后原样把 `Matcher` 造出来还给原版 |
| `File.exists()`                 | `@Redirect`               | 缓存命中时返回 `true`，原版据此跳过 HTTP 下载                                 |
| `checkHash(String, File)`       | `@Inject` + `cancellable` | 被顶替时直接返回 `true`：那个 URL 键文件根本不存在，真去算它的哈希只会失败    |

两个被 `@Redirect` 的都是 JDK 方法（`(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;`、`()Z`），与映射表无关；`checkHash` 的签名 `(String, File) -> boolean` 在 1.15.2 – 1.20.2 逐版本核对过官方映射表，一字未变。两个调用点在方法体内**各只出现一次**，且 `matcher` 的字节码偏移恒小于 `exists`——已在 1.14.4 / 1.15.2 / 1.16.5 / 1.17.1 / 1.18.2 / 1.19 / 1.19.2 / 1.19.3 / 1.19.4 / 1.20.1 / 1.20.2 共 11 个版本的**混淆 jar** 上逐一反汇编确认（方法体边界排除了 lambda 合成方法），所以 `@Redirect` 不需要 `ordinal`。

传递方式刻意用了两种：`matcher` 与 `exists()` 在同一次调用、同一线程内，用 `ThreadLocal` 最省事；而 `checkHash` 与 `setServerPack` 跑在 `thenCompose` 的回调里，线程已换成 Downloader，ThreadLocal 传不过去——那两步唯一稳定的身份是**那个 URL 键文件路径**，所以改用以它为键的映射表（`ServerPackShortcut.SUBSTITUTES`）。表里的记录由 `setServerPack` 消费掉；异常路径下积到 32 条会整体清空，代价只是那些连接退化成正常下载。

### 1.4 时序与线程

- 本地包：`detectPackResources` 在 `PackRepository.reload()` 里同步执行（启动、进资源包界面、F3+T）。进界面时靠 §1.2.1 的启用判据挡掉未安装的包，所以默认仅已启用的本地包会升级。
- 服务器包：`loadRequestedPacks` 在主线程 `runAllUpdates()` → `startReload()` 内同步执行。

转换是**阻塞**的（解压 → 转换 → 重打包），与原版本来要在这里做的 IO 同量级，但大包首次会明显变慢。缓解：

1. 按源内容命中缓存时，跳过解包、迁移和打包（§4）。
2. 只在日志里输出进度，不碰 GUI——各版本的 GUI API 差异太大，为一个进度条引入 7 份 UI 适配不值得。

---

## 2. 版本适配矩阵

按「拦截点签名指纹」分族。混淆版本用官方映射表（Mojang 名 → obf）→ intermediary v2（obf → `class_`/`method_`），描述符也一并映射后逐版本比对；26.1+ 直接比对类名、方法名和描述符。指纹和构建目标兼容的相邻版本共用一个适配器。

| 适配器目录 | 覆盖版本         | localHook        | serverHook                | 混淆 | Java |
|------------|------------------|------------------|---------------------------|------|------|
| `1.14.4`   | 1.14.4 – 1.15.2  | `createSupplier` | `setServerPack`           | 是   | 8    |
| `1.16`     | 1.16 – 1.19.2    | `createSupplier` | `setServerPack`           | 是   | 8    |
| `1.19.3`   | 1.19.3 – 1.20.1  | `folderDetector` | `setServerPackDownloaded` | 是   | 17   |
| `1.20.2`   | 1.20.2           | `packDetector`   | `setServerPackDownloaded` | 是   | 17   |
| `1.20.3`   | 1.20.3 – 1.20.4  | `packDetector`   | `loadRequestedPacks`      | 是   | 17   |
| `1.20.5`   | 1.20.5 – 1.21.11 | `packDetector`   | `loadRequestedPacks`      | 是   | 21   |
| `26.1`     | 26.1 – 26.3      | `packDetector`   | `loadRequestedPacks`      | 否   | 25   |

七个适配器共用同一个 mod id `viarp-adapter-mc`，版本号 `<modVersion>-<mcVersion>`，`depends.minecraft` 写 `>=<mcVersion>`。Fabric Loader 在同 id 候选中挑「满足依赖且版本最高」的一个，因此跑在 1.21.4 时只有 `1.20.5` 适配器被选中（`26.1` 因 `minecraft >= 26.1` 不满足而淘汰）。`:validateAdapterMatrix` 会把这套选择逻辑模拟一遍。

26.3 复用 `26.1` 适配器：Minecraft 参考仓库的 26.3 版本提交 `634debadbbb7d440370f6ad29a86d1587058ce76` 中，两处目标及路径参数的用法均未改变，原有 `>=26.1` 已匹配，无需新增适配器。[Mojang 的 26.3 版本元数据](https://piston-meta.mojang.com/v1/packages/ead839cbccf0ddf013c3f935f2840080b5cb0d26/26.3.json) 确认 Java 25（`java-runtime-epsilon`），且未发布 `client_mappings`。

mixin 源码不放在各适配器目录下，而是集中在 `adapters/shared/{common,local/<hook>,server/<hook>}`，由 `viarp.mc.adapter` 插件按矩阵挂进对应适配器的 sourceSet。这样「1.20.3 与 1.20.5 的 hook 完全相同」这件事在源码层面就是同一份文件，不会漂移。

`26.1+` 的游戏 jar 已带 Mojang 名，不需要映射表，构建用 `fabric-loom` + `jar`；1.21.11 及以下用 `fabric-loom-remap` + `remapJar` + `officialMojangMappings()`。

---

## 3. 内置单向迁移

### 3.1 格式识别与路由

- 当前客户端：读取原版 `/version.json`（旧整数、中期 `resource`、现代 `resource_major` / `resource_minor`）。不引入原版运行时类型。
- 输入包：从根部 `pack.mcmeta` 解析主格式和兼容区间。没有根元数据时，仅按已确认的 atlas 或离散纹理布局识别。内部 0.0、0.1 表示这两个形态，不是原版的对外格式号。
- 路由：只在目标严格高于源包声明上界时升级；使用 `PackFormatHistory` 中实际发行的节点，区分 major/minor，跳过未发行格式。客户端目标上限是 26.3（97.1），目录记录到 26.4-s10（104.0）。

### 3.2 迁移与发布

格式范围描述的是识别与可达性，不是素材保真率。只要格式已知，就可以识别是否需要升版；只有符合规则前置条件的素材会被改写。

1. 解包或复制到私有工作树；原包只读。解压拒绝歧义路径、重名文件、校验失败和超过预算的包。
2. 源基准取源包声明闭区间内最新的已发行资源包格式。按该格式物化匹配的客户端 overlay 到 `assets/`；声明靠后的 overlay 覆盖先前内容，只匹配更旧格式的层不再参与。产物移除 overlay 声明。
3. 逐个经过目标节点：旧纹理、经典、过渡、现代规则分别处理已验证的改名、JSON 或图像变换。没有迁移规则的节点不修改素材。无法确认对应关系的素材尽量保留并报告已检测到的问题。待改写 JSON 必须可严格解码为 UTF-8、键不重复且在读取预算内；不满足时保留原文件。根格式声明有歧义时整包交还原版。
4. 最后写入目标声明：旧格式用 `pack_format`，现代格式用精确的 `min_format`、`max_format` 数组。工作树失败不发布产物，回退原路径。

规则证据见 [旧纹理](research/legacy-texture-packs.md)、[旧图集共享坐标](research/legacy-atlas-expansion.md)、[经典格式](research/classic-pack-formats.md)、[经典实体纹理](research/classic-entity-textures.md)、[经典声音](research/classic-sounds.md)、[经典方块状态](research/classic-blockstates.md)、[独立模型](research/classic-model-overrides.md)、[旧 Unicode 字体](research/classic-unicode-fonts.md)、[经典 GUI 图集](research/classic-gui-sheets.md)、[经典控件](research/classic-widget-details.md)、[早期反色后处理](research/old-post-effect-upgrades.md)、[现代后处理](research/post-effect-upgrades.md)、[物品分图集诊断](research/item-atlas-split.md)、[现代格式](research/modern-pack-formats.md)。新增会改变产物的规则时，更新 `ResourcePackUpgrader.RULES_VERSION`。

## 4. 缓存

缓存根目录为 `<gameDir>/config/viaresourcepack/`，可用 `-Dviarp.cacheDir` 覆盖。条目路径含源 SHA-1、ZIP/目录形态、完整目标格式和内置规则指纹，`meta.json` 必须与键匹配。命中时还检查目录产物是否有非空根声明；ZIP 产物另查文件长度和可读的目录索引。这些检查不过就忽略旧产物并重新转换，不逐个核对资源内容。ZIP 按原始字节计算 SHA-1，以兼容旧版服务器声明；目录散列排序后的类型、相对路径和定长文件摘要。缓存命中不依赖文件的 mtime。检测到迁移问题时，把有限的 UTF-8 `warnings.txt` 与产物放在同一缓存条目中，再写完才允许命中的 `meta.json`。报告不进包，日志只预览前 8 条并标出报告路径。

ZIP 产物按路径排序且时间戳固定；目录仍输出为目录。同一运行中仅在首次使用缓存前按大小上限淘汰最早创建的条目，避免删掉原版已拿到的产物。退出游戏后删 `cache/` 不影响源包。

`work/` 与 `stubs/` 只在启动时清理；运行期只补建目录。位于工作根目录下的输出不会再次被拦截升级。

---

## 5. 构建

- Gradle wrapper 9.7.0（腾讯镜像），Maven 走阿里云，Loom 的游戏资源走 BMCLAPI。
- **JDK 25 编译 Java 8 字节码**：core 用 `-source 8 -target 8`，**不是** `--release 8`。`--release 8` 会把可见的平台 API 也限制到 JDK 8，于是引用 MC 里的 record 类型（class-file 65/69）时会报「找不到 `java.lang.Record`」；`-source/-target` 只约束我们自己产出的字节码版本，已实测可以正常引用 class-file 69 的 API 并产出 major=52 字节码。
- 各适配器的 Java 目标提到该族的**最低 JRE**（见 §2），mixin `compatibilityLevel` 同步。原因：Mixin 会校验「mixin 类字节码版本 vs 目标类字节码版本」，把注入 Java 21/25 类的 mixin 声明成 `JAVA_8` 有踩坑风险。core 仍是 Java 8 字节码，它才是决定最低运行门槛的那一部分。
- 版本矩阵单一数据源 `gradle/adapters.toml`，由 `settings.gradle.kts`（它自己那几行小解析器）与 buildSrc 的 `viarp.mc.adapter` 插件共同消费。
- 各适配器的 `fabric.mod.json` 与 mixin 配置由 `generateAdapterResources` 从矩阵生成，不入库——七份手写副本只会互相漂移。
- 校验任务：
  - `checkArchitecture`：扫描 core 的 class 常量池，禁止出现 `net/minecraft/` 与 `com/mojang/`（白名单 `net/fabricmc/api/`、`net/fabricmc/loader/api/`）。
  - `validateAdapterMatrix`：矩阵 ↔ 磁盘目录一致性 + 模拟 Loader 选择。
  - `verifyUniversalJar`：嵌套 jar 数量、`depends` 完整性、每个适配器恰好声明 2 个 mixin 且对应 class 存在。
- `universal`：core jar + 7 个嵌套适配器 → `build/libs/ViaResourcePack-<version>.jar`。

不依赖 Fabric API：只用 fabric-loader（`FabricLoader.getGameDir()` + `ClientModInitializer`）、minecraft 与 mixin。

### 5.1 客户端验收范围

- **1.14.4 / Java 8 / Fabric Loader 0.19.5**：实际运行确认已启用的旧图集目录、格式 3 ZIP 升到 4；第二次运行复用缓存，资源重载仍生成图集。未启用的旧纹理目录和 ZIP 仅占位，未知目录未占位。
- **26.3 / Java 25 / Fabric Loader 0.19.5**：实际运行确认本地目录、ZIP 以原 ID 重载；格式 104 不降级。离线单人世界的格式 46 捆绑 ZIP 升到 97.1；JFR 确认原版分别从实际转换 ZIP 中读取元数据与 `sounds.json`，随后成功进入世界。
- 上述是隔离实例检查。中间客户端的适配范围主要依赖构建、版本矩阵与拦截点指纹校验；真实多人服务器下载、所有素材的视觉等价以及其他模组组合不据此视为已验收。

---

## 6. 已知限制

- 首次升级同步阻塞资源重载；后续可复用缓存。26.1–26.3 原版 `Options.loadSelectedResourcePacks` 遇到已经兼容但仍有旧不兼容标记的包时，会移除标记并跳过该轮选择；重新启用或保存配置后重启即可。模组不改写原版选包配置。
- 内置包和数据包不在处理范围内。
- 部分旧资源没有现代等价物；扩展格式、被移除内容或缺乏可验证映射的素材需要人工处理。
