package viarp.build

import org.gradle.api.Project

/**
 * 反射访问 Fabric Loom 内部实现。
 *
 * 反射在这里无法避免：Gradle 隔离了每个插件的类路径，即使 Loom 被应用到同一个
 * project，它的类在预编译脚本插件里也不可见，而这些操作没有公开 API。
 */
object Loom {

    /**
     * 当前 project 的 Minecraft 版本对应的 `officialMojangMappings()` 依赖。
     *
     * 只对混淆版本有效。26.1+ 的游戏 jar 已带 Mojang 名，Loom 会抛
     * `UnsupportedOperationException`，因此调用方必须依据 `adapters.toml` 的
     * `obfuscated` 标志来决定，而不是靠 try-catch 探测。
     */
    fun officialMojangMappings(project: Project): Any {
        val loom = project.extensions.getByName("loom")
        return loom.javaClass.methods.first {
            it.name == "officialMojangMappings" && it.parameterCount == 0
        }.invoke(loom)
    }
}
