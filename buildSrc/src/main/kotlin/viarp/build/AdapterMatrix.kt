package viarp.build

import java.io.File

/**
 * `gradle/adapters.toml` 里声明的一个 Minecraft 适配器。
 *
 * @param version    Minecraft 版本；等于 `adapters/mc/<version>/` 目录名，也是本族
 *                   支持的**最低**版本（`depends.minecraft = ">=version"`）
 * @param covers     本族实际覆盖的版本区间，仅文档用途
 * @param obfuscated MC 26.1+ 为 false（无 ProGuard）：用 `fabric-loom` + `jar`，
 *                   没有 `officialMojangMappings()`，也没有 `mod*` 配置
 * @param java       本族编译目标与 mixin compatibilityLevel 的 Java 版本
 * @param localHook  本地资源包拦截方案（见 docs/DESIGN.md §1.2）
 * @param serverHook 服务器资源包拦截方案（见 docs/DESIGN.md §1.3）
 * @param reloadHook 原版菜单提交的生命周期入口
 * @param reloadUi  与客户端屏幕 ABI 对应的 UI 桥
 */
data class McAdapter(
    val version: String,
    val covers: String,
    val obfuscated: Boolean,
    val java: Int,
    val localHook: String,
    val serverHook: String,
    val reloadHook: String,
    val reloadUi: String
) {
    val projectPath: String get() = "adapters:mc:$version"

    /** Loom 只为混淆版本注册 `remapJar`。 */
    val jarTask: String get() = if (obfuscated) "remapJar" else "jar"

    /** 叶子构建脚本必须应用的 Loom 插件 id。 */
    val loomPluginId: String
        get() = if (obfuscated) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom"

    /** 本地包 hook 对应的共享源码目录（相对 rootDir）。 */
    val localHookDir: String get() = "adapters/shared/local/$localHook"

    /** 服务器包 hook 对应的共享源码目录（相对 rootDir）。 */
    val serverHookDir: String get() = "adapters/shared/server/$serverHook"

    val reloadHookDir: String get() = "adapters/shared/reloadHook/$reloadHook"

    val reloadUiDir: String get() = "adapters/shared/reloadUi/$reloadUi"

    /** 该族启用的 mixin 类名（相对 mixin 配置的 package）。 */
    val mixins: List<String>
        get() = listOf(
            LOCAL_MIXINS[localHook] ?: error("$projectPath: unknown localHook '$localHook'"),
            SERVER_MIXINS[serverHook] ?: error("$projectPath: unknown serverHook '$serverHook'"),
            RELOAD_MIXINS[reloadHook] ?: error("$projectPath: unknown reloadHook '$reloadHook'")
        )

    /** Mixin 配置里的 `compatibilityLevel`。 */
    val mixinCompatibilityLevel: String get() = "JAVA_$java"

    private companion object {
        /** localHook -> mixin 类名。键必须与 `adapters/shared/local/<key>/` 目录同名。 */
        val LOCAL_MIXINS = mapOf(
            "createSupplier" to "local.FolderRepositorySourceMixin",
            "folderDetector" to "local.FolderRepositorySourceDetectorMixin",
            "packDetector" to "local.PackDetectorMixin"
        )

        /** serverHook -> mixin 类名。键必须与 `adapters/shared/server/<key>/` 目录同名。 */
        val SERVER_MIXINS = mapOf(
            "setServerPack" to "server.ClientPackSourceMixin",
            "setServerPackDownloaded" to "server.DownloadedPackSourceLegacyMixin",
            "loadRequestedPacks" to "server.DownloadedPackSourceMixin"
        )

        val RELOAD_MIXINS = mapOf(
            "selectScreen" to "reload.SelectScreenReloadMixin",
            "optionsScreen" to "reload.OptionsScreenReloadMixin",
            "options" to "reload.OptionsReloadMixin"
        )
    }
}

/**
 * 解析后的适配器矩阵——「有哪些适配器、各自钉住什么版本」的单一数据源。
 *
 * 由 `settings.gradle.kts`（间接地，用它自己的小解析器）、buildSrc 的适配器插件、
 * 根项目的 `universal` 打包任务和 `:validateAdapterMatrix` 共同读取。任何地方都
 * 不允许再从目录列表或构建脚本文本里反推这份数据。
 */
class AdapterMatrix(val mc: List<McAdapter>) {

    fun mc(version: String): McAdapter = mc.firstOrNull { it.version == version }
        ?: error("Minecraft adapter '$version' is not declared in $RELATIVE_PATH")

    companion object {
        const val RELATIVE_PATH: String = "gradle/adapters.toml"

        fun load(rootDir: File): AdapterMatrix {
            val tables = Toml.parseArrayTables(rootDir.resolve(RELATIVE_PATH))

            tables.keys.forEach { table ->
                require(table == "mc") { "$RELATIVE_PATH: unknown table [[$table]]" }
            }

            val mc = tables["mc"].orEmpty().map { row ->
                McAdapter(
                    version = row.string("version"),
                    covers = row.string("covers"),
                    obfuscated = row.boolean("obfuscated"),
                    java = row.int("java"),
                    localHook = row.string("localHook"),
                    serverHook = row.string("serverHook"),
                    reloadHook = row.string("reloadHook"),
                    reloadUi = row.string("reloadUi")
                )
            }
            require(mc.isNotEmpty()) { "$RELATIVE_PATH declares no [[mc]] adapters" }

            val matrix = AdapterMatrix(mc)
            // 配置期就把 mixins 求值一次，让未知的 hook 名立刻炸掉，而不是等到
            // processResources 写出一个引用不存在类的 mixin 配置。
            mc.forEach { it.mixins }
            return matrix
        }

        private fun Map<String, Any>.string(key: String): String =
            this[key] as? String ?: error("[[mc]] entry $this is missing string key '$key'")

        private fun Map<String, Any>.boolean(key: String): Boolean =
            this[key] as? Boolean ?: error("[[mc]] entry $this is missing boolean key '$key'")

        private fun Map<String, Any>.int(key: String): Int =
            this[key] as? Int ?: error("[[mc]] entry $this is missing integer key '$key'")
    }
}
