# 构建和直接依赖核对（2026-11-22）

版本选择依据为上游正式发行与本项目兼容边界，而非 Maven 元数据中的 `latest`（该字段也可能指向预发行）。直接依赖盘点覆盖根脚本、`buildSrc`、两个 Loom 插件 ID 和七个适配器实际使用的版本目录项。未独立覆盖传递依赖，也未测试或修改用户游戏实例。

| 依赖 / 用途 | 原版本 → 选定版本 | 上游证据与相容理由 |
| --- | --- | --- |
| Gradle Wrapper | 9.7.0 → **9.9.2** | [Gradle 正式版本 API](https://services.gradle.org/versions/current) 标记 9.9.2 为当前正式版并给出两个校验值；[JVM 兼容表](https://docs.gradle.org/9.9.2/userguide/compatibility.html) 支持 JDK 25。分发仍走原腾讯镜像，以官方 SHA-256 校验。 |
| Fabric Loom：remap 与普通插件 | 1.17-SNAPSHOT → **1.19.4** | [Fabric Maven 索引](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml) 的 1.20.0 条目仍是 alpha；[1.19.4 模块元数据](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/fabric-loom-1.19.4.module) 标记插件 API 为 Gradle 9.8.0、JVM 25，满足本项目升级后的构建环境。 |
| Fabric Loader（编译、开发运行） | 0.19.3 → **0.20.1** | [Fabric Meta Loader 列表](https://meta.fabricmc.net/v2/versions/loader) 标记 0.20.1 为稳定；[安装器元数据](https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.json) 声明 Java 8 下限，同时给出本次使用的 Sponge Mixin 和开发用 MixinExtras 版本。 |
| Fabric Sponge Mixin（非混淆路径直接使用） | 0.17.3+mixin.0.8.7 → **0.17.5+mixin.0.8.7** | [Fabric Maven 索引](https://maven.fabricmc.net/net/fabricmc/sponge-mixin/maven-metadata.xml) 与上述 Loader 元数据一致，且 Mixin 本体仍为 0.8.7。混淆路径由 Loader 安装器元数据决定 Mixin 版本。 |
| MixinExtras Fabric（混淆 / 非混淆开发路径） | 0.3.5 / 0.5.4 → **0.5.5** | [Maven Central 索引](https://repo.maven.apache.org/maven2/io/github/llamalad7/mixinextras-fabric/maven-metadata.xml) 与 Loader 的开发依赖一致；[上游发行说明](https://github.com/LlamaLad7/MixinExtras/releases/tag/0.5.5) 说明此版本修复与新版 Fabric Mixin 编译的 `@Redirect` 的不兼容。保留两个适配器别名但共用一条版本；没有引用 MixinExtras 扩展注解，没有把它打包进适配器或加入用户运行要求。 |
| Gson（构建脚本） | 2.11.0 → **2.15.0** | [Gson 正式发行](https://github.com/google/gson/releases/tag/gson-parent-2.15.0)；[上游 JVM 下限](https://github.com/google/gson/tree/gson-parent-2.15.0#minimum-java-version) 为 Java 8，仅在 JDK 25 构建逻辑中使用，不替换 MC 的 Gson。 |
| SLF4J API（编译、测试） | 2.0.18 → **2.0.20** | [SLF4J 正式发行](https://www.slf4j.org/news.html#2.0.20)；[Maven Central 索引](https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/maven-metadata.xml) 的 2.1.0 是 alpha。仍不打包、不引入 provider，core 对游戏日志 API 的反射和回退保持不变。 |
| JUnit Jupiter / Platform Launcher（测试） | 5.10.2 / 1.10.2 → **6.1.3** | [JUnit 6.1.3 发布说明](https://docs.junit.org/6.1.3/release-notes.html) 同时标记 Jupiter 和 Platform 为 6.1.3；[运行要求](https://docs.junit.org/6.1.3/overview.html) 为 Java 17，满足测试（Java 17 字节码 / JDK 25 运行），不抬升适配器/core 的 Java 目标。6.2.0-M1 是里程碑。 |
| AssertJ Core（测试） | 3.25.3 → **3.27.7** | [AssertJ 官方文档](https://assertj.github.io/doc/) 给出 3.27.7 和 Java 8 下限；[Maven Central 索引](https://repo.maven.apache.org/maven2/org/assertj/assertj-core/maven-metadata.xml) 的 4.0.0-M1 是里程碑。 |

## 不独立升级的项目

- Loader 最低声明仍是 **0.15.0**，不是构建依赖的版本。公开的 [Loader 0.15.0 API](https://github.com/FabricMC/fabric-loader/blob/0.15.0/src/main/java/net/fabricmc/loader/api/FabricLoader.java) 已提供 core 使用的 `getInstance()` 和 `getGameDir()`；适配器和根模组不需为开发依赖升级抬高游戏安装要求。
- Minecraft 七个钉点 **1.14.4、1.16、1.19.3、1.20.2、1.20.3、1.20.5、26.1** 在 [Mojang 官方 manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 均为正式版。钉点是各适配器的最低目标，不是通用库；不能用当前正式版 26.3 替换 26.1，或用它覆盖其余六个族。前六个族的 Mojang mappings 也必须与各自 Minecraft 构建目标一致，26.1 无需此映射。保留原矩阵、游戏库和 Java 目标。
- Gradle 核心的 `java` 与 `kotlin-dsl` 插件没有项目独立定版；`kotlin-dsl` 跟随 Wrapper，Gradle 9.9.2 自带 Kotlin **2.4.21**。不将其换成单独发布的 Kotlin 插件。

## 实测

在 **Zulu JDK 25.0.4.1** 上通过仓库 Wrapper 执行。

- `bash ./gradlew --version`：退出码 0，报告 Gradle **9.9.2**、Kotlin **2.4.21**、JDK **25.0.4.1**。
- `bash ./gradlew --console=plain wrapper --gradle-distribution-url https://mirrors.cloud.tencent.com/gradle/gradle-9.9.2-bin.zip --gradle-distribution-sha256-sum 971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b`：退出码 0，七个 Loom 项目均完成配置，`BUILD SUCCESSFUL in 6m`（11 项执行）。POSIX、Windows 脚本一并由该任务再生成；Windows 脚本仅将行尾规范为仓库的 LF。
- 独立下载腾讯镜像分发包并计算 SHA-256，与上述官方分发摘要逐字节一致；再生成的 Wrapper JAR SHA-256 与官方值 `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8` 一致。
- `cmd.exe /d /c "gradlew.bat --version"`：退出码 0，Windows 启动器也报告 Gradle 9.9.2 / Zulu JDK 25.0.4.1。
- 第一次在默认输出目录执行 `bash ./gradlew --console=plain :build --rerun-tasks --no-build-cache`：退出码 1，34 项任务已执行，`:jar` 因 Windows 无法删除既有 `build/libs/ViaResourcePack-core-2.0.0.jar` 中止。该旧产物未用于校验；不关闭外部进程、不改用户实例，不把此失败当作依赖不兼容。
- 使用临时 Gradle init script（仅将各模块 `layout.buildDirectory` 改到新的临时目录，不改项目脚本）再次执行 `bash ./gradlew --console=plain --init-script <init> :build --rerun-tasks --no-build-cache`：退出码 1；成功重编 core 并产出 core JAR，七个适配器随后因工作树中正在开发的 `LocalPackReload` 缺少 `ReloadUi` 符号而编译失败。本轮不能据此宣称 universal 验证通过，也不把该源代码错误归因于依赖。
- 直接检查该轮新生成的 core JAR：102 个类都为 major **52（Java 8）**，`fabricloader` 声明仍是 `>=0.15.0`，未包含 Fabric Loader、Gson、SLF4J、JUnit 类。
- 隔离目录执行 `:test :checkArchitecture :validateAdapterMatrix --rerun-tasks --no-build-cache`：16 项全部执行，23 项核心测试、架构检查、矩阵检查全部通过。`testRuntimeClasspath` 实际解析 Jupiter/Platform 6.1.3、AssertJ 3.27.7、SLF4J 2.0.20。
- `bash ./gradlew --console=plain --init-script <init> :test :checkArchitecture :validateAdapterMatrix --rerun-tasks --no-build-cache`：退出码 0，`BUILD SUCCESSFUL in 21s`，16 项均执行；JUnit **23 项通过、0 失败、0 跳过**，架构扫描 **102** 个类并通过、适配器矩阵通过。
- `bash ./gradlew --console=plain --init-script <init> :dependencies --configuration testRuntimeClasspath`：退出码 0，实际解析的 Jupiter / Platform Launcher 为 **6.1.3**、AssertJ 为 **3.27.7**、SLF4J API 为 **2.0.20**。
