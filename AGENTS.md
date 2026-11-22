# AGENTS.md

本文件给 AI 编程助手与维护者快速上手用：构建、测试、架构约束、常见改动流程。
设计细节与拦截点指纹以 [docs/DESIGN.md](docs/DESIGN.md) 为准，本文只收录「改代码时必须知道」的东西。

## 项目是什么

Fabric 客户端 mod：加载资源包时自动把与当前 Minecraft 版本不匹配的资源包转换到当前版本。
支持 **MC 1.14.4 – 26.3**，单个 universal jar 通用，**不依赖 Fabric API**（只用
fabric-loader + minecraft + mixin）。

包名 `top.syshub.viarp`，作者 Dainsleif，GPL-3.0-or-later。

## 构建环境

- 始终使用仓库自带的 Gradle wrapper（下文命令以 `./gradlew` 表示；Windows 下是
  `.\gradlew.bat`），不要依赖机器上全局安装的 gradle。
- **需要 JDK 25**：构建脚本在 core 上用 `-source 8 -target 8`（而不是
  `--release 8`）产出 Java 8 字节码，见红线 2。让 Gradle 运行在 JDK 25 上即可，具体
  JDK 安装位置由各开发环境自行配置。
- Gradle wrapper 与 Loom 版本（`loomVersion`）、依赖/游戏资源的镜像（maven、assets、
  version manifest）都集中在 `gradle.properties` 与 `settings.gradle.kts` 里，与仓库
  一起分发，无需每台机器单独配置。

## 构建 / 测试 / 校验命令

| 命令                              | 作用                                                                                |
|-----------------------------------|-------------------------------------------------------------------------------------|
| `./gradlew build`                 | core + 全部适配器 + universal jar                                          |
| `./gradlew universal`             | 只打包 universal jar                                                                |
| `./gradlew verifyUniversalJar`    | 校验 universal 嵌套结构（8 个嵌套 jar、fabric.mod.json、每个适配器恰好 3 个 mixin） |
| `./gradlew checkArchitecture`     | 扫描 core 常量池，禁止 `net/minecraft/`、`com/mojang/` 引用                         |
| `./gradlew validateAdapterMatrix` | `adapters.toml` ↔ 磁盘一致性 + 模拟 Loader 选适配器                                 |
| `./gradlew test`                 | core 单元测试 |

产物：`build/libs/ViaResourcePack-<version>.jar`（交付物，core + 8 个嵌套 jar）与
`build/libs/ViaResourcePack-core-<version>.jar`（core 本体，调试用）。

## 仓库结构

```
src/main/java/top/syshub/viarp/   core：跨 8 个版本族共用的 Java 8 字节码（不引 net.minecraft）
  cache/   内容缓存（ConversionCache、CacheEntryMeta）
  convert/ 调度（ConversionService）、PackFormatHistory 与内置迁移规则
           （PackPlaceholder 占位包、ServerPackShortcut 服务器包去重）
  io/      PathIo、ZipTool（可复现 zip：条目排序 + 时间戳 0）
  json/    自带 JSON 读写（core 不用 gson，避免 2.8.0–2.14.0 的 API 差异）
  pack/    pack.mcmeta 解析（PackMetadata）、当前版本探测（GameVersion）、
           已启用的本地包（PackSelection）
adapters/
  mc/<版本>/       每个版本族的适配器子项目：一个 build.gradle.kts（apply loom + viarp.mc.adapter）
                   + 一张 fabric.mod.json/mixin 配置（由插件生成，不入库）
  shared/common/   ViaRpHooks 路径桥与 LocalPackReload 菜单生命周期桥
  shared/local/<hook>/   本地资源包拦截 mixin
  shared/server/<hook>/  服务器资源包拦截 mixin（1.14.4–1.20.2 族的下载去重注入也在这）
  shared/reloadHook/<hook>/  选包确认后发起重载的 mixin
  shared/reloadUi/<bridge>/  原版进度界面桥
gradle/
  adapters.toml    版本矩阵单一数据源（[[mc]] 表）
  libs.versions.toml  依赖版本目录
buildSrc/          Kotlin 构建插件（viarp.mc.adapter）与矩阵解析器（AdapterMatrix、Toml）
docs/DESIGN.md     设计文档：拦截点、矩阵、缓存、构建细节（改拦截点先改它）
refer/             gitignore 的参考资料（原版资源包加载调用链）
```

## 不可破坏的约束（红线）

1. **core 不引用 `net.minecraft` / `com.mojang`**（`checkArchitecture` 扫描常量池强制）。
   core 可用的 Fabric 面只有 `net.fabricmc.api.ClientModInitializer` 与
   `net.fabricmc.loader.api.FabricLoader`（`getGameDir()` / `getConfigDir()`）。
2. **core 必须产出 Java 8 字节码**：用 `-source 8 -target 8`，**不是** `--release 8`
   （后者限制平台 API，引用 MC 里的 record 时会报「找不到 java.lang.Record」）。
   写法上也保持 Java 8：不用 `var`、文本块、switch 表达式等新语法。
3. **保持原版读取器**：发现与下载 hook 只替换 File/Path/List<IdAndPath>，不包装
   `PackResources` / `Pack`。菜单 hook 只把保存后的重载交给后台准备桥，不拦截全局重载入口。
4. **每个适配器恰好 3 个 mixin**（本地路径、服务器路径和选包确认），验包检查矩阵声明的精确类集合。
5. **mixin 源码只放在 `adapters/shared/{common,local/<hook>,server/<hook>,reloadHook/<hook>,reloadUi/<bridge>}`**，不在
   `adapters/mc/` 下复制；`fabric.mod.json` 与 `viarp-adapter-mc.mixins.json` 由
   `generateAdapterResources` 按矩阵生成，不要手写。
6. **版本矩阵单一数据源 `gradle/adapters.toml`**：`settings.gradle.kts` 的 include 列表和
   `buildSrc` 的 hook→mixin 映射都从它推导；绕开矩阵直接改这些会撞上
   `validateAdapterMatrix` 的校验。
7. **转换只向上**：按发行的 `PackFormat` 节点迁移，保留 major/minor，目标严格高于源声明上界。
   改变产物或源视图时更新 `ResourcePackUpgrader.RULES_VERSION`，避免沿用旧缓存。
8. **本地包「安装时」转换**：只有 `options.txt` 的 `resourcePacks` 行里出现的包才转换
   （`PackSelection` 判定）；缺少根元数据但可识别的旧纹理包走 `PackPlaceholder`
   占位；更高格式的资源包保持原样。
9. **服务器包下载去重**（仅 1.14.4 – 1.20.2）：`ServerPackShortcut` + 三个注入点
   （redirect `Pattern.matcher`、redirect `File.exists`、inject `checkHash`）。
   两个 `@Redirect` 的目标是 JDK 方法，与映射表无关；已确认全区间各恰好一次且顺序稳定，
   **不要加 `ordinal`**。1.20.3+ 的原版已按内容哈希缓存，不需要这套。
10. **缓存与临时目录**：默认 `cache/`、`work/`、`stubs/` 都在 `resourcepacks/.viaresourcepack/`，
    不写根 `pack.mcmeta`；`config/viaresourcepack/` 只写配置。临时目录只在 `ViaRpPaths.prepare()` 清理一次，
    运行期不清，保护原版仍持有的占位包。
11. **不引入新依赖**：core 只用 JDK；适配器编译期可见 core，运行期只依赖
    fabric-loader / minecraft / mixin / mixinextras。不要给 core 加 slf4j 等硬依赖
    （1.14.4–1.16.5 运行时没有 slf4j-api，`ViaRpLog` 是反射门面）。
12. **每个源文件保留 GPL-3.0 头注释**，版权 `2026 Dainsleif`；新增文件照抄现有头。

## 常见任务

### 新增 / 调整版本族
1. `gradle/adapters.toml` 加一条 `[[mc]]`（`version`/`covers`/`obfuscated`/`java`/
   `localHook`/`serverHook`/`reloadHook`/`reloadUi`）。
2. 建 `adapters/mc/<version>/build.gradle.kts`，内容照抄现有族（apply 对应的 Loom 插件 +
   `viarp.mc.adapter`；`obfuscated = false` 的族用普通 `fabric-loom`）。
3. 拦截点签名在新区间内变化时，新建 `adapters/shared/local/<新hook>` 或
   `adapters/shared/server/<新hook>`，并在 `buildSrc/src/main/kotlin/viarp/build/AdapterMatrix.kt`
   的 `LOCAL_MIXINS` / `SERVER_MIXINS` 映射里登记。
4. 跑 `./gradlew validateAdapterMatrix` 与 `./gradlew verifyUniversalJar`，
   同步更新 `docs/DESIGN.md` §2 与（如有）嵌套 jar 期望数。

### 增加迁移规则

从 [docs/DESIGN.md](docs/DESIGN.md#3-内置单向迁移) 的格式与证据入口核对首个改变结构的发行格式。
迁移只修改工作副本；在双端素材上核实路径、字段或图像变换，无法推出的内容保留并记录原因。

### 调整拦截点
先改 `docs/DESIGN.md` §1 的拦截点指纹表，用官方映射表 + intermediary v2 核对
`class_`/`method_` 与描述符，再做 mixin。混淆版本的注解里写 Mojang 名即可——Loom
`remapJar` 会重写成 intermediary 名，可用 `javap` 反编译适配器产物验证 `method_*` 值。

### 加单元测试
- core 测试在 `src/test/java/top/syshub/viarp/`（JUnit Jupiter + AssertJ），针对 `PackMetadata` /
  `PackSelection` / 缓存键这类不依赖 MC 的逻辑。
- 注意 `ViaRpPaths.get()` 会调 `FabricLoader.getInstance()`，单测里不要触发它；
  设计上与路径有关的逻辑尽量做成可注入（如把 Path 传进构造函数）。

## 编码规范
- `.editorconfig` 强制：UTF-8、LF 行尾、Java/Kotlin 4 空格缩进、JSON 2 空格。
  Windows 上不要写成 CRLF；`.md` 保留行尾空白（表格对齐）。
- core 保持 Java 8 风格：显式类型、匿名类、单一抽象，与现有代码（`PathIo`、`ConversionCache`）
  一致；Java 8 允许的 lambda/Stream 可用，但别为「更现代」引入新语法。
- 日志用 `ViaRpLog`（占位符 `{}`），不直接 `System.out`/`System.err`。
- 注释以中文为主（与 README/DESIGN 一致），公共 API 的 Javadoc 写清「为什么」。

## 版本控制
- **没有明确指令不要 `git commit` / `git push`。**
- 提交信息规范：`<type>(<scope>): <中文摘要>`，scope 用英文模块名
  （`build`/`convert`/`cache`/`adapters`/`docs`/…），summary 用中文。
- 提交前先 `git log --oneline -20` 确认既有风格。
