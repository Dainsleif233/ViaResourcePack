pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.aliyun.com/repository/gradle-plugin/")
        maven("https://maven.aliyun.com/repository/public/")
        gradlePluginPortal()
    }
    val loomVersion: String = providers.gradleProperty("loomVersion").get()
    plugins {
        id("net.fabricmc.fabric-loom-remap") version loomVersion
        id("net.fabricmc.fabric-loom") version loomVersion
    }
}

rootProject.name = "ViaResourcePack"

// 适配器子项目来自 gradle/adapters.toml —— 版本矩阵的单一数据源。
// settings 脚本在 buildSrc 编译之前运行，所以这里用几行代码重读它需要的
// `version` 字段；`:validateAdapterMatrix` 负责校验它与磁盘目录一致。
include(
    *buildList {
        var inMcTable = false
        rootDir.resolve("gradle/adapters.toml").readLines().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("[[") -> inMcTable = when (val table = line.removeSurrounding("[[", "]]").trim()) {
                    "mc" -> true
                    else -> throw GradleException("gradle/adapters.toml: unknown table [[$table]]")
                }

                line.startsWith("version") && inMcTable ->
                    add("adapters:mc:" + line.substringAfter('=').trim().removeSurrounding("\""))
            }
        }
        check(isNotEmpty()) { "gradle/adapters.toml declares no adapters" }
    }.toTypedArray()
)
