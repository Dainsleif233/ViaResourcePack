package viarp.build

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * 供预编译脚本插件访问版本目录。
 *
 * Gradle 不会在 buildSrc 的预编译脚本插件里生成类型安全的 `libs.*` 访问器，
 * 所以这里通过 API 读取 `gradle/libs.versions.toml`。集中在一处，避免依赖版本
 * 以字符串字面量的形式重新渗回插件里。
 */
object Catalog {

    /** 库别名 [alias] 的 `group:name:version` 记法。 */
    fun notation(project: Project, alias: String): String = library(project, alias).let { library ->
        "${library.module.group}:${library.module.name}:${library.versionConstraint.requiredVersion}"
    }

    /** [alias] 的 `group:name:version`，但版本替换为 [version]。 */
    fun notation(project: Project, alias: String, version: String): String =
        library(project, alias).module.let { "${it.group}:${it.name}:$version" }

    fun version(project: Project, alias: String): String =
        catalog(project).findVersion(alias)
            .orElseThrow { IllegalStateException("Version '$alias' missing from gradle/libs.versions.toml") }
            .requiredVersion

    private fun library(project: Project, alias: String): MinimalExternalModuleDependency =
        catalog(project).findLibrary(alias)
            .orElseThrow { IllegalStateException("Library '$alias' missing from gradle/libs.versions.toml") }
            .get()

    private fun catalog(project: Project) =
        project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
}
