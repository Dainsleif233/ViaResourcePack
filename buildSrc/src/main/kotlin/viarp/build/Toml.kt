package viarp.build

import java.io.File

/**
 * 极简 TOML 读取器，只覆盖 `gradle/adapters.toml` 实际用到的子集：
 * 数组表头（`[[mc]]`）、带引号的字符串、裸布尔、裸整数。
 *
 * 为什么手写：`settings.gradle.kts` 需要在 buildSrc 存在之前读到同一份数据，
 * 所以格式必须简单到能在 settings 脚本里用几行重读一遍（见 settings 里的注释）。
 * 引入真正的 TOML 库只会给 build-logic 类路径加依赖，却帮不了 settings 脚本。
 *
 * 任何超出支持子集的写法都会显式报错而不是被忽略——静默丢掉一个矩阵条目
 * 就等于静默丢掉一个适配器。
 */
object Toml {

    /** 把 [file] 解析成 `表名 -> 行列表`，保持声明顺序。 */
    fun parseArrayTables(file: File): Map<String, List<Map<String, Any>>> {
        require(file.isFile) { "TOML file missing: ${file.absolutePath}" }

        val tables = LinkedHashMap<String, MutableList<Map<String, Any>>>()
        var current: MutableMap<String, Any>? = null

        file.readLines().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed

            fun fail(reason: String): Nothing =
                throw IllegalArgumentException("${file.name}:${index + 1}: $reason -> $raw")

            when {
                line.startsWith("[[") -> {
                    if (!line.endsWith("]]")) fail("unterminated array-table header")
                    val name = line.removeSurrounding("[[", "]]").trim()
                    if (name.isEmpty()) fail("empty array-table name")
                    current = LinkedHashMap<String, Any>().also {
                        tables.getOrPut(name) { mutableListOf() }.add(it)
                    }
                }

                line.startsWith("[") -> fail("plain tables are not supported, use [[name]]")

                else -> {
                    val table = current ?: fail("key-value pair before any [[table]] header")
                    val separator = line.indexOf('=')
                    if (separator < 0) fail("not a key-value pair")
                    val key = line.substring(0, separator).trim()
                    val value = line.substring(separator + 1).trim()
                    if (key.isEmpty()) fail("empty key")
                    table[key] = when {
                        value.length >= 2 && value.startsWith('"') && value.endsWith('"') ->
                            value.removeSurrounding("\"")

                        value == "true" -> true
                        value == "false" -> false
                        value.toIntOrNull() != null -> value.toInt()
                        else -> fail("only quoted strings, booleans and integers are supported")
                    }
                }
            }
        }

        return tables
    }
}
