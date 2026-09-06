# AGENTS.md

本文件给 AI 编程助手与维护者快速上手用：构建、测试、架构约束、常见改动流程。
设计细节与拦截点指纹以 [docs/DESIGN.md](docs/DESIGN.md) 为准，本文只收录「改代码时必须知道」的东西。

## 项目是什么

Fabric 客户端 mod：加载资源包时自动把与当前 Minecraft 版本不匹配的资源包转换到当前版本。
支持 **MC 1.14.4 – 26.2**，单个 universal jar 通用，**不依赖 Fabric API**（只用
fabric-loader + minecraft + mixin）。

包名 `top.syshub.viarp`，作者 Dainsleif，GPL-3.0-or-later。

## 构建环境

- 始终使用仓库自带的 Gradle wrapper（下文命令以 `./gradlew` 表示；Windows 下是
  `.\gradlew.bat`），不要依赖机器上全局安装的 gradle。
- **需要 JDK 25**：构建脚本在 core 与 `:library` 上用 `-source 8 -target 8`（而不是
  `--release 8`）产出 Java 8 字节码，见红线 2。让 Gradle 运行在 JDK 25 上即可，具体
  JDK 安装位置由各开发环境自行配置。
- Gradle wrapper 与 Loom 版本（`loomVersion`）、依赖/游戏资源的镜像（maven、assets、
  version manifest）都集中在 `gradle.properties` 与 `settings.gradle.kts` 里，与仓库
  一起分发，无需每台机器单独配置。

## 构建 / 测试 / 校验命令

| 命令                              | 作用                                                                                |
|-----------------------------------|-------------------------------------------------------------------------------------|
| `./gradlew build`                 | core + 全部适配器 + 嵌套库 + universal jar                                          |
| `./gradlew universal`             | 只打包 universal jar                                                                |
| `./gradlew verifyUniversalJar`    | 校验 universal 嵌套结构（8 个嵌套 jar、fabric.mod.json、每个适配器恰好 2 个 mixin） |
| `./gradlew checkArchitecture`     | 扫描 core 常量池，禁止 `net/minecraft/`、`com/mojang/` 引用                         |
| `./gradlew validateAdapterMatrix` | `adapters.toml` ↔ 磁盘一致性 + 模拟 Loader 选适配器                                 |
| `./gradlew test :library:test`    | core 与 library 的全部单元测试                                                      |

产物：`build/libs/ViaResourcePack-<version>.jar`（交付物，core + 8 个嵌套 jar）与
`build/libs/ViaResourcePack-core-<version>.jar`（core 本体，调试用）。

## 仓库结构

```
src/main/java/top/syshub/viarp/   core：跨 7 个版本族共用的 Java 8 字节码（不引 net.minecraft）
  cache/   转换缓存与索引（ConversionCache、CacheIndex、CacheEntryMeta）
  convert/ 调度（ConversionService、ConverterEngine(s)）与进阶行为
           （PackPlaceholder 占位包、ServerPackShortcut 服务器包去重）
  io/      PathIo、ZipTool（可复现 zip：条目排序 + 时间戳 0）
  json/    自带 JSON 读写（core 不用 gson，避免 2.8.0–2.14.0 的 API 差异）
  pack/    pack.mcmeta 解析（PackMetadata）、当前版本探测（GameVersion）、
           已启用的本地包（PackSelection）
library/  RPC（agentdid127/ResourcePackConverter）封装为 ConverterEngine，
          gson/zip4j 一并 shade 后嵌套进主 jar（META-INF/jars/viarp-library-*.jar）
adapters/
  mc/<版本>/       每个版本族的适配器子项目：一个 build.gradle.kts（apply loom + viarp.mc.adapter）
                   + 一张 fabric.mod.json/mixin 配置（由插件生成，不入库）
  shared/common/   ViaRpHooks —— mixin 与 core 之间的唯一桥（静态方法）
  shared/local/<hook>/   本地资源包拦截 mixin
  shared/server/<hook>/  服务器资源包拦截 mixin（1.14.4–1.20.2 族的下载去重注入也在这）
gradle/
  adapters.toml    版本矩阵单一数据源（[[mc]] 表）
  libs.versions.toml  依赖版本目录（RPC 版本也在这里）
buildSrc/          Kotlin 构建插件（viarp.mc.adapter）与矩阵解析器（AdapterMatrix、Toml）
docs/DESIGN.md     设计文档：拦截点、矩阵、缓存、构建细节（改拦截点先改它）
refer/             gitignore 的参考资料（26.3 资源包加载调用链、RPC API 文档）
```

## 不可破坏的约束（红线）

1. **core 不引用 `net.minecraft` / `com.mojang`**（`checkArchitecture` 扫描常量池强制）。
   core 可用的 Fabric 面只有 `net.fabricmc.api.ClientModInitializer` 与
   `net.fabricmc.loader.api.FabricLoader`（`getGameDir()` / `getConfigDir()`）。
2. **core 必须产出 Java 8 字节码**：用 `-source 8 -target 8`，**不是** `--release 8`
   （后者限制平台 API，引用 MC 里的 record 时会报「找不到 java.lang.Record」）。
   写法上也保持 Java 8：不用 `var`、文本块、switch 表达式等新语法。
3. **拦截策略 = 替换路径**：mixin 只改原版拿到的 File/Path/List<IdAndPath> 参数，**绝不**
   包装 `PackResources` / `Pack`（与加速解压类 mod 的兼容性前提）。mixin 里只允许对
   `ViaRpHooks` 的静态调用，不直接引用其他 core 类。
4. **每个适配器恰好 2 个 mixin**（local + server，`verifyUniversalJar` 用
   `declared.size() == 2` 断言）。要新增注入点就放进已有的 mixin 类，不要加第三个 mixin 类。
5. **mixin 源码只放在 `adapters/shared/{common,local/<hook>,server/<hook>}`**，不在
   `adapters/mc/` 下复制；`fabric.mod.json` 与 `viarp-adapter-mc.mixins.json` 由
   `generateAdapterResources` 按矩阵生成，不要手写。
6. **版本矩阵单一数据源 `gradle/adapters.toml`**：`settings.gradle.kts` 的 include 列表和
   `buildSrc` 的 hook→mixin 映射都从它推导；绕开矩阵直接改这些会撞上
   `validateAdapterMatrix` 的校验。
7. **RPC 版本只在 `gradle/libs.versions.toml` 的 `rpc` 一处维护**：`processResources` 把它填进
   `rpc.properties`，`RpcConverterEngine.describe()` 读回来写进缓存元数据。换 RPC 版本自动
   失效旧缓存。**不要**在 Java 里硬编码版本号。
8. **本地包「安装时」转换**：只有 `options.txt` 的 `resourcePacks` 行里出现的包才转换
   （`PackSelection` 判定）；读不出 `pack_format` 的新 schema 包走 `PackPlaceholder`
   占位，不能直接丢弃（否则它在老客户端列表里不可见、永远无法启用）。
9. **服务器包下载去重**（仅 1.14.4 – 1.20.2）：`ServerPackShortcut` + 三个注入点
   （redirect `Pattern.matcher`、redirect `File.exists`、inject `checkHash`）。
   两个 `@Redirect` 的目标是 JDK 方法，与映射表无关；已确认全区间各恰好一次且顺序稳定，
   **不要加 `ordinal`**。1.20.3+ 的原版已按内容哈希缓存，不需要这套。
10. **数据目录 `<gameDir>/config/viaresourcepack/`**：`work/` 与 `stubs/` 只在启动时清理一次
    （`ViaRpPaths.prepare()`，由 `ViaRpClient` 调用）；运行期一律用 `ensureDirectories()`。
    中途清空 `stubs/` 会删掉正被原版持有的占位包。
11. **不引入新依赖**：core 只用 JDK；适配器编译期可见 core，运行期只依赖
    fabric-loader / minecraft / mixin / mixinextras + 嵌套库。不要给 core 加 slf4j 等硬依赖
    （1.14.4–1.16.5 运行时没有 slf4j-api，`ViaRpLog` 是反射门面）。
12. **每个源文件保留 GPL-3.0 头注释**，版权 `2026 Dainsleif`；新增文件照抄现有头。

## 常见任务

### 新增 / 调整版本族
1. `gradle/adapters.toml` 加一条 `[[mc]]`（`version`/`covers`/`obfuscated`/`java`/
   `localHook`/`serverHook`）。
2. 建 `adapters/mc/<version>/build.gradle.kts`，内容照抄现有族（apply 对应的 Loom 插件 +
   `viarp.mc.adapter`；`obfuscated = false` 的族用普通 `fabric-loom`）。
3. 拦截点签名在新区间内变化时，新建 `adapters/shared/local/<新hook>` 或
   `adapters/shared/server/<新hook>`，并在 `buildSrc/src/main/kotlin/viarp/build/AdapterMatrix.kt`
   的 `LOCAL_MIXINS` / `SERVER_MIXINS` 映射里登记。
4. 跑 `./gradlew validateAdapterMatrix` 与 `./gradlew verifyUniversalJar`，
   同步更新 `docs/DESIGN.md` §2 与（如有）嵌套 jar 期望数。

### 换 RPC 版本
只改 `gradle/libs.versions.toml` 的 `rpc`；缓存会自动失效（`describe()` 变了）。参见
`docs/DESIGN.md` §3.1。

### 调整拦截点
先改 `docs/DESIGN.md` §1 的拦截点指纹表，用官方映射表 + intermediary v2 核对
`class_`/`method_` 与描述符，再做 mixin。混淆版本的注解里写 Mojang 名即可——Loom
`remapJar` 会重写成 intermediary 名，可用 `javap` 反编译适配器产物验证 `method_*` 值。

### 加单元测试
- core 测试在 `src/test/java/top/syshub/viarp/`（JUnit 5 + AssertJ），针对 `PackMetadata` /
  `PackSelection` / 缓存键这类不依赖 MC 的逻辑。
- library 测试在 `library/src/test/.../RpcConverterEngineTest.java`，真实跑 RPC 转换。
- 注意 `ViaRpPaths.get()` 会调 `FabricLoader.getInstance()`，单测里不要触发它；
  设计上与路径有关的逻辑尽量做成可注入（如把 Path 传进构造函数）。

## 编码规范
- `.editorconfig` 强制：UTF-8、LF 行尾、Java/Kotlin 4 空格缩进、JSON 2 空格。
  Windows 上不要写成 CRLF；`.md` 保留行尾空白（表格对齐）。
- core 保持 Java 8 风格：显式类型、匿名类、单一抽象，与现有代码（`PathIo`、`CacheIndex`）
  一致；Java 8 允许的 lambda/Stream 可用，但别为「更现代」引入新语法。
- 日志用 `ViaRpLog`（占位符 `{}`），不直接 `System.out`/`System.err`。
- 注释以中文为主（与 README/DESIGN 一致），公共 API 的 Javadoc 写清「为什么」。

## 版本控制
- **没有明确指令不要 `git commit` / `git push`。**
- 提交信息规范：`<type>(<scope>): <中文摘要>`，scope 用英文模块名
  （`build`/`convert`/`cache`/`adapters`/`docs`/…），summary 用中文。
- 提交前先 `git log --oneline -20` 确认既有风格；当前 `master` 尚无提交。
