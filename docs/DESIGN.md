# ViaResourcePack 设计文档

> Fabric 客户端 mod：加载资源包时自动把与当前游戏版本不匹配的包转换成当前版本，再交给原版加载。
> 包名 `top.syshub.viarp`，作者 Dainsleif，GPL-3.0-or-later，支持 MC 1.14.4 – 26.2。

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
2. **跨版本改动面最小**。被替换的那个参数在 1.14.4 – 26.2 全程只是 `java.io.File` 或 `java.nio.file.Path`，除了 File→Path 之外没有语义变化。原版后续的 symlink 校验、zip/目录判定、`pack.mcmeta` 读取、`PackResources` 构造全都自然作用在新文件上。
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

拦截点随版本落在三处（下表的 intermediary 名与描述符已对每个版本的官方映射表 + intermediary v2 交叉验证；`localHook` 是 `gradle/adapters.toml` 里的取值）：

| MC 区间         | `localHook`      | 目标方法                                                                          | 描述符                                                     |
|-----------------|------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------|
| 1.14.4 – 1.19.2 | `createSupplier` | `class_3279.method_14432`（`FolderRepositorySource.createSupplier`）              | `(Ljava/io/File;)Ljava/util/function/Supplier;`            |
| 1.19.3 – 1.20.1 | `folderDetector` | `class_3279.method_45269`（`FolderRepositorySource.detectPackResources`，static） | `(Ljava/nio/file/Path;Z)Lclass_3288$class_7680;`           |
| 1.20.2 – 26.2   | `packDetector`   | `class_8621.method_52441`（`PackDetector.detectPackResources`）                   | `(Ljava/nio/file/Path;Ljava/util/List;)Ljava/lang/Object;` |

`PackDetector` 是 1.20.2 之后所有本地包的唯一漏斗，且签名从 1.20.2 到 26.2 **一个字符都没变**（已在 1.20.2 / 1.20.5 / 1.21.4 / 1.21.9 / 1.21.11 / 26.1 / 26.2 上逐一确认），是整个区间里最稳的一个点。

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

`save()` 排在 `reloadResourcePacks()` **之前**，这个顺序 1.14.4 – 26.2 全程未变（1.14.4 是 `ResourcePackSelectScreen` 的 Done 按钮里手写的 `options.save()` + `reloadResourcePacks()`，1.16+ 收敛进 `Options.updateResourcePacks`）。因此拦截点读 `options.txt` 一定能看到玩家刚勾上的包；反之只是浏览界面时文件不变，未启用的包自然不转换。

`PackSelection` 按 mtime + 大小缓存这一行，界面里改完选择会自动重读。包 id 认 `file/<文件名>`（1.14.4 起 `FolderRepositorySource` 固定这个前缀）与裸文件名两种形态——`Options.loadSelectedResourcePacks` 至今仍兼容后者。匹配 `resourcePacks:` 时必须精确比键名：`incompatibleResourcePacks` 以同样的子串结尾。

想让整个目录提前转好可以把 `config.json` 的 `onlyConvertEnabledPacks` 关掉。

### 1.2.2 占位包：让未安装的新版本包能被看见

「只转换已启用的包」单独实施时会撞上一个死锁。1.21.9 起资源包可以只写 `min_format` / `max_format` 而**不写 `pack_format`**，而 1.20.1 及更早的 `PackMetadataSectionSerializer` 是这么读的：

```java
int i = GsonHelper.getAsInt(jsonObject, "pack_format");   // 缺键直接抛
```

于是老客户端读这类包时抛 `JsonSyntaxException: Missing pack_format`（实测日志原文），`Pack.readPackInfo` 返回 `null`，`readMetaAndCreate` 也返回 `null`，`FolderRepositorySource.loadPacks` 干脆不把它交给 consumer——这个包**根本不进 `PackRepository`**，界面上连「不兼容」都看不到。它必须先被勾选才会转换，但玩家看不到它，永远没法勾选。

`PackPlaceholder` 的做法是：对「本版本读不出 `pack_format`、且尚未启用」的包，把路径换成一个即时生成的占位目录：

```
config/viaresourcepack/stubs/<源文件名>/
    pack.mcmeta     只有 pack_format（当前版本）+ 原样搬过来的 description
    pack.png        源包里有就复制过来
```

于是它以真实的名字、说明、图标出现在「可用」列表里且显示为兼容。玩家勾选并确认后 `options.txt` 变化，启用判据放行，下一次探测返回的就是完整转换后的包。

三个细节：

- **占位目录名必须逐字节等于源文件名**（含 `.zip` 后缀）。包 id 是 `"file/" + path.getFileName()`，换个名字玩家勾选后写进 `options.txt` 的就是另一个 id，启用判据再也匹配不上。目录带 `.zip` 后缀不影响原版判定：`PackDetector` 与 1.14.4 的 `createSupplier` 都先看 `isDirectory()`。
- **`description` 必须原样复制**，不能转成字符串：它可以是纯文本、文本组件（`{"text":...,"color":...}`）或组件数组，三种形态 `ComponentSerialization` 都认。
- **只写 `pack_format` + `description`**，其他键（尤其 `overlays`）一律丢掉——占位包里没有对应目录，声明了反而让原版去找不存在的东西。

占位包是个空壳（没有任何 `assets/`）。玩家勾选后原版先用空壳做一次重载，紧接着 `options.txt` 已更新，下一次探测就换成真货。刻意不预先转换，否则又回到「进界面即全量转换」。

对**本版本能读出 `pack_format` 的旧包**（如 `pack_format: 3`）不生成占位包：原版本来就能正常列出它并标记为「为旧版本制作」，玩家看得见也点得动。

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

拦截 `loadRequestedPacks` 而不是 `pushPack` 或下载回调，正是为了**覆盖哈希缓存命中**：`DownloadQueue` 命中缓存时不会触发下载回调，但一定会把本地文件路径塞进 `IdAndPath` 交给 `loadRequestedPacks`。在这里把 `List<IdAndPath>` 整体替换成路径已转换的新列表，新下载与缓存命中两条路都被覆盖。单人世界的捆绑包（`WorldOpenFlows.loadBundledResourcePack` → `pushLocalPack`）走的也是同一个方法。

| MC 区间 | `serverHook` | 目标方法 | 描述符 | 替换对象 |
|---|---|---|---|---|
| 1.14.4 – 1.19.2 | `setServerPack` | `class_1066.method_4638`（`ClientPackSource.setServerPack`） | `(Ljava/io/File;[Lclass_5352;])...` | `File` |
| 1.19.3 – 1.20.2 | `setServerPackDownloaded` | `class_1066.method_4638`（`net.minecraft.client.resources.DownloadedPackSource.setServerPack`） | `(Ljava/io/File;Lclass_5352;)Ljava/util/concurrent/CompletableFuture;` | `File` |
| 1.20.3 – 26.2 | `loadRequestedPacks` | `class_1066.method_55519`（`net.minecraft.client.resources.server.DownloadedPackSource.loadRequestedPacks`） | `(Ljava/util/List;)Ljava/util/List;` | `List<IdAndPath>` |

两点需要留意：

- `class_1066` 这个 intermediary 编号一路沿用，但它背后的 Mojang 类名换过两次：1.14.4 – 1.19.2 是 `ClientPackSource`，1.19.3 – 1.20.2 是 `client.resources.DownloadedPackSource`（同时 `ClientPackSource` 变成了 `class_1065`），1.20.3+ 又搬到 `client.resources.server.DownloadedPackSource`。因此三个 hook 是三份 mixin，但目标类在 intermediary 下是同一个。
- 1.14.4/1.15 的 `setServerPack` 只有一个 `File` 参数，1.16 起多了 `PackSource`。mixin 用不带描述符的 `method = "setServerPack"` 按名匹配，`argsOnly = true` + `File` 参数类型确保只改到文件参数，因此两代签名共用一份 mixin。

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

服务器在下发包时已经告诉了我们内容哈希，而我们的缓存键正是「源内容 SHA-1 + 目标 major」。于是三个注入点合起来把「这份内容我早转好了」告诉原版：

| 注入点                          | 方式                      | 作用                                                                          |
|---------------------------------|---------------------------|-------------------------------------------------------------------------------|
| `Pattern.matcher(CharSequence)` | `@Redirect`               | 记下 `SHA1.matcher(hash)` 里那个声明哈希，然后原样把 `Matcher` 造出来还给原版 |
| `File.exists()`                 | `@Redirect`               | 缓存命中时返回 `true`，原版据此跳过 HTTP 下载                                 |
| `checkHash(String, File)`       | `@Inject` + `cancellable` | 被顶替时直接返回 `true`：那个 URL 键文件根本不存在，真去算它的哈希只会失败    |

两个被 `@Redirect` 的都是 JDK 方法（`(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;`、`()Z`），与映射表无关；`checkHash` 的签名 `(String, File) -> boolean` 在 1.15.2 – 1.20.2 逐版本核对过官方映射表，一字未变。两个调用点在方法体内**各只出现一次**，且 `matcher` 的字节码偏移恒小于 `exists`——已在 1.14.4 / 1.15.2 / 1.16.5 / 1.17.1 / 1.18.2 / 1.19 / 1.19.2 / 1.19.3 / 1.19.4 / 1.20.1 / 1.20.2 共 11 个版本的**混淆 jar** 上逐一反汇编确认（方法体边界排除了 lambda 合成方法），所以 `@Redirect` 不需要 `ordinal`。

传递方式刻意用了两种：`matcher` 与 `exists()` 在同一次调用、同一线程内，用 `ThreadLocal` 最省事；而 `checkHash` 与 `setServerPack` 跑在 `thenCompose` 的回调里，线程已换成 Downloader，ThreadLocal 传不过去——那两步唯一稳定的身份是**那个 URL 键文件路径**，所以改用以它为键的映射表（`ServerPackShortcut.SUBSTITUTES`）。表里的记录由 `setServerPack` 消费掉；异常路径下积到 32 条会整体清空，代价只是那些连接退化成正常下载。

### 1.4 时序与线程

- 本地包：`detectPackResources` 在 `PackRepository.reload()` 里同步执行（启动、进资源包界面、F3+T）。进界面时靠 §1.2.1 的启用判据挡掉未安装的包，所以实际转换只发生在启动与「勾选后确认」这两个时刻。
- 服务器包：`loadRequestedPacks` 在主线程 `runAllUpdates()` → `startReload()` 内同步执行。

转换是**阻塞**的（解压 → 转换 → 重打包），与原版本来要在这里做的 IO 同量级，但大包首次会明显变慢。缓解：

1. 命中缓存时先比「路径 + 大小 + mtime」，全中就完全不算哈希（§4）。
2. 只在日志里输出进度，不碰 GUI——各版本的 GUI API 差异太大，为一个进度条引入 7 份 UI 适配不值得。

---

## 2. 版本适配矩阵

按「拦截点签名指纹」分族。指纹的算法：官方映射表（Mojang 名 → obf）→ intermediary v2（obf → `class_`/`method_`），描述符也一并映射后逐版本比对，相邻版本指纹相同则合并。

| 适配器目录 | 覆盖版本         | localHook        | serverHook                | 混淆 | Java |
|------------|------------------|------------------|---------------------------|------|------|
| `1.14.4`   | 1.14.4 – 1.15.2  | `createSupplier` | `setServerPack`           | 是   | 8    |
| `1.16`     | 1.16 – 1.19.2    | `createSupplier` | `setServerPack`           | 是   | 8    |
| `1.19.3`   | 1.19.3 – 1.20.1  | `folderDetector` | `setServerPackDownloaded` | 是   | 17   |
| `1.20.2`   | 1.20.2           | `packDetector`   | `setServerPackDownloaded` | 是   | 17   |
| `1.20.3`   | 1.20.3 – 1.20.4  | `packDetector`   | `loadRequestedPacks`      | 是   | 17   |
| `1.20.5`   | 1.20.5 – 1.21.11 | `packDetector`   | `loadRequestedPacks`      | 是   | 21   |
| `26.1`     | 26.1 – 26.2      | `packDetector`   | `loadRequestedPacks`      | 否   | 25   |

七个适配器共用同一个 mod id `viarp-adapter-mc`，版本号 `<modVersion>-<mcVersion>`，`depends.minecraft` 写 `>=<mcVersion>`。Fabric Loader 在同 id 候选中挑「满足依赖且版本最高」的一个，因此跑在 1.21.4 时只有 `1.20.5` 适配器被选中（`26.1` 因 `minecraft >= 26.1` 不满足而淘汰）。`:validateAdapterMatrix` 会把这套选择逻辑模拟一遍。

mixin 源码不放在各适配器目录下，而是集中在 `adapters/shared/{common,local/<hook>,server/<hook>}`，由 `viarp.mc.adapter` 插件按矩阵挂进对应适配器的 sourceSet。这样「1.20.3 与 1.20.5 的 hook 完全相同」这件事在源码层面就是同一份文件，不会漂移。

`26.1+` 的游戏 jar 已带 Mojang 名，不需要映射表，构建用 `fabric-loom` + `jar`；1.21.11 及以下用 `fabric-loom-remap` + `remapJar` + `officialMojangMappings()`。

---

## 3. 转换引擎

### 3.1 依赖与打包

用 [agentdid127/ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter)（MIT），JitPack 坐标 `com.github.agentdid127.ResourcePackConverter:{Library,Forwards,Backwards}:63ba2cd402`。传递依赖 `gson 2.11.0` 与 `zip4j 2.11.3`；`jopt-simple` 只被它的 Console 模块用，在构建里 exclude 掉。

**钉在提交 `63ba2cd402` 而不是 tag `2.2.6`**：2.2.6 的转换器只到 pack_format 64（1.21.8），这个提交补齐了 1.21.10（69）与 1.21.11（75，新增 `CelestialSlicerUpdater1_21_11`，把 `textures/environment/{sun,moon_phases,end_flash}.png` 搬进 `environment/celestial/`）。版本号只写在 `gradle/libs.versions.toml` 的 `rpc` 一处，`library` 的 `processResources` 把它填进 `top/syshub/viarp/library/rpc.properties`，`RpcConverterEngine.describe()` 读回来——`describe()` 会写进缓存元数据，所以换 RPC 版本后旧的转换产物自动失效。

**必须 shade 且 relocate gson**：RPC 用了 `com.google.gson.Strictness` 与 `JsonReader.setStrictness`（gson ≥ 2.11），而 MC 1.14.4 只带 gson 2.8.0 —— 直接用游戏自带的会 `NoSuchMethodError`。zip4j 游戏完全不带，同样必须打包。

```
com.google.gson    -> top.syshub.viarp.shaded.gson
net.lingala.zip4j  -> top.syshub.viarp.shaded.zip4j
```

RPC 自身的包名 `com.agentdid127.resourcepack` **不** relocate：它靠 `PackConverter.class.getResourceAsStream("/protocol.json")`、`/forwards/*.json` 这类**绝对资源路径**加载数据文件，relocate 类名不会同步改资源路径。

打包形态：独立的 `:library` 子项目用 Shadow 产出 relocate 后的 fat jar，附一份 `fabric.mod.json`（mod id `viarp-library`），再作为 **jar-in-jar** 嵌进主 jar 的 `META-INF/jars/`。

core 与 `:library` 的耦合刻意做成**单向且弱**：core 只定义 `ConverterEngine` 接口，实现类 `top.syshub.viarp.library.RpcConverterEngine` 由 `ConverterEngines` 在运行期反射按名加载。嵌套库缺失或链接失败时 core 仍能正常加载，只是转换功能停用（日志报错，所有资源包原样放行）。

### 3.2 API 用法与工作目录短路

```java
int from = Util.getVersionProtocol(gson, "1.12");   // 注意：是 protocol 编号，不是 pack_format
new ForwardsPackConverter(gson, from, to, "none", inputDir, false, printStream).runPack(pack);
new BackwardsPackConverter(gson, from, to, inputDir, false, printStream).runPack(pack);
```

RPC 原本的语义是「原包在 `path`，工作副本在 `<path>_converted`」：`Pack.Handler.setup()` 复制过去，`ZipPack.Handler.finish()` 再打包回 zip。ViaResourcePack 自己管解压、打包与缓存（要控制输出位置，也要产出可复现的 zip），所以用 `WorkingDirectoryPack` 把两端都短路：`getWorkingPath()` 直接返回传入的目录，`setup()` 只做 BOM 清理（RPC 的 JSON 读取遇到 BOM 会失败，这一步必须留），`finish()` 什么都不做。

RPC 的 `Logger` 是全局静态 `PrintStream`，默认往 `System.out` 刷屏，转换大包能刷上千行。`ConverterLogStream` 把它按行缓冲后转到 mod 日志的 debug 级别。

### 3.3 版本号 ↔ pack_format

运行期不自带版本表，而是反查 RPC 内置的 `protocol.json`：遍历 `Util.getSupportedVersions()`，对每个版本取 `pack_format` 与 `protocol_version`，构建「pack_format major → 该 major 下**最高**的 protocol」映射。取最高是有意的：同一个 pack_format 常对应多个游戏版本（format 9 = 1.19/1.19.1/1.19.2），取最高才能让所有该跑的转换器都被 `shouldConvert` 命中。

当前游戏的 pack_format 从 **MC jar 自带的 `/version.json`** 读取（原版 `DetectedVersion` 自己也是这么读的，1.14.4 – 26.2 全都有这个文件），因此 core 完全不需要引用任何 `net.minecraft` API。三种 schema 都要处理：

| MC 区间         | `pack_version` 形态                                  |
|-----------------|------------------------------------------------------|
| 1.14.4 – 1.16.5 | `4`（单个整数，资源包与数据包共用）                  |
| 1.17 – 1.21.8   | `{ "resource": 32, "data": 41 }`                     |
| 1.21.9 – 26.2   | `{ "resource_major": 88, "resource_minor": 0, ... }` |

**目标版本超出 RPC 覆盖范围时，转换到它能转到的最高版本**：RPC 覆盖到 1.21.11 = format 75，而 26.1 = 84、26.2 = 88。此时把 `to` 夹到 75（即按 1.21.11 跑完全部转换器），再由我们把 `pack.mcmeta` 改写成当前版本的格式。1.21.11 → 26.2 之间的资源结构变化 RPC 无法处理，属已知限制（§6）。`from` 同理夹取到引擎下界。夹取发生时会打一条 info 日志说明实际转换到了哪个格式。

### 3.4 是否需要转换

读源包 `pack.mcmeta` 的 `pack` 段，按「新键优先」合并出一个受支持区间（`PackMetadata`）：

1. 1.21.9+：`min_format` / `max_format`（int 或 `[major, minor]`）、`formats`（int / `[min,max]` / `{min_inclusive,max_inclusive}`）。
2. 1.20.2+：`pack_format` + 可选 `supported_formats`（同样三种形态）。
3. 更早：只有 `pack_format`。

当前 major 落在该区间内 → 放行，零开销。否则以区间下界为 `from`、当前格式为 `to` 触发转换。

比较只看 **major**：minor 在 1.21.9+ 的语义是「同 major 内新增了可选特性」，不影响能否加载。

写回时**只写 `pack_format` 一个键**，并显式删掉 `supported_formats` / `min_format` / `max_format` / `formats`。原因：转换产物只针对当前这一个 major，保留区间声明会让缓存产物被误判为也支持别的版本。

`pack` 段以外的内容（`description`、`overlays`、`language`、`filter` 等）原样保留。

JSON 读写用自带的 `top.syshub.viarp.json.Json` 而非 Gson：core 要在 gson 2.8.0（1.14.4）到 2.14.0（26.2）之间通用，而这段区间里 Gson 的静态入口有过增删（`JsonParser.parseString` 直到 2.8.6 才有）。解析是 lenient 的（容忍 BOM、`//` 与 `/* */` 注释、末尾逗号），与原版读 `pack.mcmeta` 的宽松程度一致。

### 3.5 日志

core 不能直接用 SLF4J：MC 1.14.4 – 1.16.5 的运行期**只有 log4j**，`org.slf4j.Logger` 根本不在类路径上（1.17 才引入 slf4j-api）。而 core 是七个版本族共用的同一份字节码，任何硬引用都会让老版本 `NoClassDefFoundError`。

`ViaRpLog` 因此按 slf4j → log4j2 → `System.out` 的顺序反射解析一次后缓存。这三者恰好同形（`Factory.getLogger(String)` + `debug/info/warn/error(String, Object...)`，占位符都是 `{}`，末尾 Throwable 都打堆栈），所以一套反射代码覆盖两个框架。

---

## 4. 缓存与索引

缓存根目录 `<gameDir>/config/viaresourcepack/`（可用 `-Dviarp.cacheDir` 覆盖）。放在 `config/` 下是 Fabric 生态的约定，整合包与启动器的备份/同步规则也按这个目录来。

```
config/viaresourcepack/
  config.json                          缺失时自动写默认值
  cache/
    <sourceSha1>-<targetMajor>/
      pack.zip | pack/                 转换产物（源是 zip 则 zip，源是目录则目录）
      meta.json                        源哈希、from/to、引擎名、创建时间、产物大小
  index.json                           绝对路径 + size + mtime -> 缓存键
  stubs/<源文件名>/                     占位包（§1.2.2），启动时清空
  work/                                转换临时目录，启动时清空
```

`work/` 与 `stubs/` 的清理只在 `ViaRpClient` 启动时做一次（`ViaRpPaths.prepare`）。运行期只调 `ensureDirectories()`：资源包发现流程一旦开始，`stubs/` 下的占位包就正被原版持有，中途清空会让它们凭空消失。

缓存键里带目标 major：同一个源包在不同游戏版本下的产物互不覆盖，玩家在 1.20 与 1.21 之间来回切换时两份都留着。

命中流程：

1. 查 `index.json`：绝对路径 + 大小 + mtime 全中 → 直接返回产物，**完全不算哈希**。
2. 未命中 → 算 SHA-1 → 查 `cache/<sha1>-<major>/` → 命中则回填索引（覆盖「同一个包换了路径或 mtime」）。
3. 仍未命中 → 转换 → 落盘 → 写索引 → 按上限裁剪缓存。

裁剪按 `meta.json` 的 `createdAt` 从旧到新淘汰，默认上限 4 GiB（`config.json` 的 `maxCacheMegabytes`）。刻意不用文件访问时间：很多文件系统默认关闭 atime 更新。

转换产出的 zip 是**可复现**的：条目按路径排序、时间戳固定为 0（`ZipTool.pack`）。这样同样的输入永远得到同样的 SHA-1，缓存键与服务器包的哈希校验都稳定。

递归防护：我们返回的缓存路径也会流过拦截点（原版重新打开它），`ViaRpPaths.isOwnOutput` 检测到路径在缓存根下就直接放行。

---

## 5. 构建

- Gradle wrapper 9.7.0（腾讯镜像），Maven 走阿里云，Loom 的游戏资源走 BMCLAPI。
- **JDK 25 编译 Java 8 字节码**：core 与 `:library` 用 `-source 8 -target 8`，**不是** `--release 8`。`--release 8` 会把可见的平台 API 也限制到 JDK 8，于是引用 MC 里的 record 类型（class-file 65/69）时会报「找不到 `java.lang.Record`」；`-source/-target` 只约束我们自己产出的字节码版本，已实测可以正常引用 class-file 69 的 API 并产出 major=52 字节码。
- 各适配器的 Java 目标提到该族的**最低 JRE**（见 §2），mixin `compatibilityLevel` 同步。原因：Mixin 会校验「mixin 类字节码版本 vs 目标类字节码版本」，把注入 Java 21/25 类的 mixin 声明成 `JAVA_8` 有踩坑风险。core 仍是 Java 8 字节码，它才是决定最低运行门槛的那一部分。
- 版本矩阵单一数据源 `gradle/adapters.toml`，由 `settings.gradle.kts`（它自己那几行小解析器）与 buildSrc 的 `viarp.mc.adapter` 插件共同消费。
- 各适配器的 `fabric.mod.json` 与 mixin 配置由 `generateAdapterResources` 从矩阵生成，不入库——七份手写副本只会互相漂移。
- 校验任务：
  - `checkArchitecture`：扫描 core 的 class 常量池，禁止出现 `net/minecraft/` 与 `com/mojang/`（白名单 `net/fabricmc/api/`、`net/fabricmc/loader/api/`）。
  - `validateAdapterMatrix`：矩阵 ↔ 磁盘目录一致性 + 模拟 Loader 选择。
  - `verifyUniversalJar`：嵌套 jar 数量、`depends` 完整性、每个适配器恰好声明 2 个 mixin 且对应 class 存在。
- `universal`：core jar + 嵌套 `library` + 7 个适配器 → `build/libs/ViaResourcePack-<version>.jar`。

不依赖 Fabric API：只用 fabric-loader（`FabricLoader.getGameDir()` + `ClientModInitializer`）、minecraft 与 mixin。

---

## 6. 已知限制

1. RPC（`63ba2cd402`）的转换器只到 pack_format 75（1.21.11）。在 **26.1 / 26.2** 上运行时，资源包会被转换到 1.21.11 的形态，再把 `pack.mcmeta` 改写成当前格式；**1.21.11 → 26.2 之间的资源结构变化不会被处理**。
2. 转换阻塞资源重载线程，超大包首次加载明显变慢（有缓存，只发生一次）。
3. 只处理 `resourcepacks/` 目录下的本地包与服务器/世界下发包。内置包（vanilla、programmer art、高对比度）不转换。
4. 数据包不在范围内。
5. 转换质量取决于 RPC 本身；跨越多个大版本（如 1.7 → 26.2）时部分资源无法自动迁移。
