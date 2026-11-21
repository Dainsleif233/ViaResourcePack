import viarp.build.AdapterMatrix
import viarp.build.Catalog
import viarp.build.Loom

plugins {
    java
}

version = rootProject.version

// 这个适配器钉住的所有版本都来自 `gradle/adapters.toml`，以项目目录名（= 该族最低
// Minecraft 版本）为键。叶子构建脚本只负责应用正确的 Loom 插件，故意不提供
// `adapter { }` 配置块，这样矩阵就无法与构建脚本脱节。
val adapter = AdapterMatrix.load(rootDir).mc(project.name)

// 叶子脚本唯一还能自己选的东西：MC 26.1+ 无混淆，没有 `remapJar`、没有
// `officialMojangMappings()`、也没有 `mod*` 配置，必须用普通 `fabric-loom`。
require(pluginManager.hasPlugin("net.fabricmc.fabric-loom-remap") == adapter.obfuscated) {
    "${project.path}: adapters.toml says obfuscated=${adapter.obfuscated}, so this project must " +
        "apply ${adapter.loomPluginId}"
}

val loaderVersion = Catalog.version(project, "fabric-loader")

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.aliyun.com/repository/gradle-plugin/")
    maven("https://maven.aliyun.com/repository/public/")
}

// 各适配器共享同一份 mixin 源码：拦截点的方法名与描述符里不含任何 net.minecraft
// 类型（见 docs/DESIGN.md §1），所以同一份源码能同时编译到混淆与非混淆版本。
// 具体挂哪几份由 adapters.toml 的 localHook / serverHook 决定。
val sharedRoots = listOf(
    rootDir.resolve("adapters/shared/common/src/main/java"),
    rootDir.resolve("${adapter.localHookDir}/src/main/java"),
    rootDir.resolve("${adapter.serverHookDir}/src/main/java")
)
sharedRoots.forEach { root ->
    require(root.isDirectory) { "${project.path}: shared mixin source root missing: $root" }
}

sourceSets.named("main") {
    java.srcDirs(*sharedRoots.toTypedArray())
}

dependencies {
    if (adapter.obfuscated) {
        add("mappings", Loom.officialMojangMappings(project))
    }

    // core（根项目）编译期可见即可，运行期由 Fabric 的类加载器提供。
    // 适配器通过共享 ViaRpHooks 调用 core；迁移规则不属于适配器。
    add("compileOnly", project(":"))

    if (adapter.obfuscated) {
        // ── 混淆路径（1.14.4 – 1.21.11）：Loom 通过 mod* 配置做重映射 ──
        add("modRuntimeOnly", Catalog.notation(project, "mixinextras-fabric"))
        add("modImplementation", Catalog.notation(project, "fabric-loader", loaderVersion))
    } else {
        // ── 非混淆路径（26.1+）：mod* 配置不存在 ──
        // sponge-mixin 与 mixinextras 打在 fabric-loader 的 jar 里而不在其 POM 里，
        // 所以这里显式加上。
        add("implementation", Catalog.notation(project, "mixinextras-fabric-plain"))
        add("implementation", Catalog.notation(project, "sponge-mixin"))
        add("implementation", Catalog.notation(project, "fabric-loader", loaderVersion))
    }

    add("minecraft", "com.mojang:minecraft:${adapter.version}")
}

// fabric.mod.json 与 mixin 配置都由矩阵生成——内容完全由 adapters.toml 决定，
// checked-in 七份副本只会互相漂移。
val generatedResources = layout.buildDirectory.dir("generated/resources")

val generateAdapterResources = tasks.register("generateAdapterResources") {
    group = "build"
    description = "Generate fabric.mod.json + mixin config for this adapter from gradle/adapters.toml"

    val modVersion = project.version.toString()
    val mcVersion = adapter.version
    val covers = adapter.covers
    val compatLevel = adapter.mixinCompatibilityLevel
    val mixins = adapter.mixins
    val minLoader = Catalog.version(project, "fabric-loader-min")
    val outDir = generatedResources

    inputs.property("modVersion", modVersion)
    inputs.property("mcVersion", mcVersion)
    inputs.property("covers", covers)
    inputs.property("compatLevel", compatLevel)
    inputs.property("mixins", mixins)
    inputs.property("minLoader", minLoader)
    outputs.dir(outDir)

    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()

        // 手写 JSON 而不是引 gson：内容只有几个已知字段，且要精确控制缩进。
        dir.resolve("fabric.mod.json").writeText(
            buildString {
                appendLine("{")
                appendLine("""  "schemaVersion": 1,""")
                appendLine("""  "id": "viarp-adapter-mc",""")
                appendLine("""  "version": "$modVersion-$mcVersion",""")
                appendLine("""  "name": "ViaResourcePack Minecraft Adapter",""")
                appendLine("""  "description": "Resource pack loading hooks for Minecraft $covers.",""")
                appendLine("""  "authors": [""")
                appendLine("""    "Dainsleif"""")
                appendLine("""  ],""")
                appendLine("""  "license": "GPL-3.0-or-later",""")
                appendLine("""  "environment": "client",""")
                appendLine("""  "mixins": [""")
                appendLine("""    "viarp-adapter-mc.mixins.json"""")
                appendLine("""  ],""")
                appendLine("""  "depends": {""")
                appendLine("""    "minecraft": ">=$mcVersion",""")
                appendLine("""    "fabricloader": ">=$minLoader",""")
                appendLine("""    "viarp": "$modVersion"""")
                appendLine("""  }""")
                appendLine("}")
            }
        )

        dir.resolve("viarp-adapter-mc.mixins.json").writeText(
            buildString {
                appendLine("{")
                appendLine("""  "required": true,""")
                appendLine("""  "minVersion": "0.8",""")
                appendLine("""  "package": "top.syshub.viarp.adapter.mixin",""")
                appendLine("""  "compatibilityLevel": "$compatLevel",""")
                appendLine("""  "client": [""")
                mixins.forEachIndexed { index, mixin ->
                    val comma = if (index < mixins.lastIndex) "," else ""
                    appendLine("""    "$mixin"$comma""")
                }
                appendLine("""  ],""")
                appendLine("""  "injectors": {""")
                appendLine("""    "defaultRequire": 1""")
                appendLine("""  }""")
                appendLine("}")
            }
        )
    }
}

sourceSets.named("main") {
    resources.srcDir(generateAdapterResources.map { generatedResources })
}

java.withSourcesJar()

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // 用 -source/-target 而不是 --release：--release N 会把可见的平台 API 也限制到
    // JDK N，于是引用 MC 里的 record（class-file 65/69）时会报「找不到
    // java.lang.Record」。-source/-target 只约束我们自己产出的字节码版本。
    options.compilerArgs.addAll(listOf("-source", adapter.java.toString(), "-target", adapter.java.toString(), "-nowarn"))
    options.isFork = true
    sourceCompatibility = adapter.java.toString()
    targetCompatibility = adapter.java.toString()
}
