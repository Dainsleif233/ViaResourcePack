import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import viarp.build.AdapterMatrix
import viarp.build.McAdapter
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.copyTo

plugins {
    java
}

// Gson（下面重写嵌套 fabric.mod.json 用）经由 buildSrc 的运行期类路径到达本脚本，
// 所以它的版本和其他依赖一样住在 gradle/libs.versions.toml 里 —— 这里刻意不写
// `buildscript { classpath(...) }`。

rootProject.version = providers.gradleProperty("version").get()

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.aliyun.com/repository/public/")
}

dependencies {
    compileOnly(libs.fabric.loader)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.slf4j.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

val matrix: AdapterMatrix = AdapterMatrix.load(rootDir)

/**
 * universal jar 里的一个嵌套 jar。
 *
 * @param projectPath 不带前导冒号的项目路径
 * @param jarTask     产出 jar 的任务名（混淆版本是 `remapJar`）
 * @param nestedName  嵌套后的文件名；刻意与源 jar 名解耦，好让 `META-INF/jars/` 下的
 *                    名字自解释（源 jar 名是项目目录名，比如 `library-1.0.0.jar`、
 *                    `1.20.5-1.0.0.jar`，直接嵌进去可读性很差）
 */
data class Nested(val projectPath: String, val jarTask: String, val nestedName: String)

val nested: List<Nested> = listOf(
    Nested("library", "shadowJar", "viarp-library-${project.version}.jar")
) + matrix.mc.map {
    Nested(it.projectPath, it.jarTask, "viarp-adapter-mc-${it.version}-${project.version}.jar")
}

fun nestedJarFile(entry: Nested): File {
    val target = project(":${entry.projectPath}")
    return target.layout.buildDirectory.file("libs/${target.name}-${target.version}.jar").get().asFile
}

fun packageUniversalJar() {
    // universal fat jar 是最终交付物，占用朴素的 `<name>-<version>.jar` 名字，取代
    // `:jar` 产出的 core jar。打包时先复制到临时文件、写完再原子移动覆盖输出，
    // 避免直接打开 Gradle 追踪的输出文件（会撞上独占文件锁）。
    val outputFile = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar").get().asFile
    val coreJar = layout.buildDirectory.file("libs/${project.name}-core-${project.version}.jar").get().asFile
    require(coreJar.isFile) { "Core jar missing: ${coreJar.absolutePath}. Run `:jar` first." }

    nested.forEach { entry ->
        val jar = nestedJarFile(entry)
        require(jar.isFile) {
            "Nested jar missing for ${entry.projectPath}: ${jar.absolutePath}. " +
                "Build `:${entry.projectPath}:${entry.jarTask}` first."
        }
    }

    val tmp = File(outputFile.parentFile, "universal-${System.nanoTime()}.jar")
    coreJar.copyTo(tmp)

    try {
        FileSystems.newFileSystem(URI.create("jar:" + tmp.toURI()), emptyMap<String, Any>()).use { fs ->
            Files.createDirectories(fs.getPath("/META-INF/jars"))

            val mod = fs.getPath("/fabric.mod.json").bufferedReader().use {
                Gson().fromJson(it, JsonElement::class.java)
            } as JsonObject

            val jars = JsonArray().also { mod.add("jars", it) }

            nested.forEach { entry ->
                val source = nestedJarFile(entry)
                val name = "META-INF/jars/${entry.nestedName}"
                source.toPath().copyTo(fs.getPath("/$name"))
                jars.add(JsonObject().also { it.addProperty("file", name) })
            }

            fs.getPath("/fabric.mod.json").bufferedWriter().use {
                GsonBuilder().setPrettyPrinting().create().toJson(mod, it)
            }
        }
    } catch (t: Throwable) {
        tmp.delete()
        throw t
    }

    outputFile.parentFile.mkdirs()
    Files.move(tmp.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

/** 构建全部适配器与嵌套库，再打成 universal fat jar。 */
val universal = tasks.register("universal") {
    group = "build"
    description = "Build every adapter plus the nested library and package them into a universal fat jar"

    nested.forEach { dependsOn(":${it.projectPath}:${it.jarTask}") }
    dependsOn(tasks.named("jar"))

    outputs.upToDateWhen {
        nested.all { project(":${it.projectPath}").tasks.getByName(it.jarTask).state.upToDate } &&
            tasks.named("jar").get().state.upToDate
    }

    outputs.file(layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))

    doLast {
        packageUniversalJar()
    }
}

/**
 * 校验 universal jar 的嵌套结构。
 *
 * 刻意不声明 `dependsOn`，只声明顺序：它校验的是「谁产出了这个 jar」，
 * 声明依赖会让 `universal` 之外的路径重复打包一次。
 */
val verifyUniversalJar = tasks.register("verifyUniversalJar") {
    group = "verification"
    description = "Verify the universal jar: nested jar count, fabric.mod.json consistency, mixin config presence"
    mustRunAfter(universal)

    doLast {
        val universalJar = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar").get().asFile
        require(universalJar.isFile) { "Universal jar not found: ${universalJar.absolutePath}" }

        val expected = nested.size
        logger.lifecycle("Verifying ${universalJar.name} (expecting $expected nested jars)")

        ZipFile(universalJar).use { zip ->
            val rootEntry = zip.getEntry("fabric.mod.json") ?: error("Universal jar has no fabric.mod.json")
            val rootMod = zip.getInputStream(rootEntry).bufferedReader().use {
                Gson().fromJson(it, JsonObject::class.java)
            }

            val depends = rootMod.getAsJsonObject("depends")
            require(depends != null && depends.has("viarp-adapter-mc") && depends.has("viarp-library")) {
                "Root fabric.mod.json must depend on viarp-adapter-mc and viarp-library"
            }

            val jars = rootMod.getAsJsonArray("jars")
                ?: error("Root fabric.mod.json has no 'jars' array")
            require(jars.size() == expected) {
                "Nested jar count mismatch: fabric.mod.json declares ${jars.size()}, expected $expected"
            }

            var adapters = 0
            for (element in jars) {
                val path = element.asJsonObject.get("file")?.asString
                    ?: error("A 'jars' entry has no 'file' field")
                val entry = zip.getEntry(path)
                require(entry != null) { "Nested jar '$path' is declared but missing from the archive" }

                val temp = Files.createTempFile("viarp-verify-", ".jar")
                try {
                    zip.getInputStream(entry).use { Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING) }
                    ZipFile(temp.toFile()).use { inner ->
                        val modEntry = inner.getEntry("fabric.mod.json")
                            ?: error("Nested jar '$path' has no fabric.mod.json")
                        val mod = inner.getInputStream(modEntry).bufferedReader().use {
                            Gson().fromJson(it, JsonObject::class.java)
                        }
                        val id = mod.get("id")?.asString ?: error("Nested jar '$path' declares no mod id")
                        if (id == "viarp-adapter-mc") {
                            adapters++
                            val configName = mod.getAsJsonArray("mixins")?.firstOrNull()?.asString
                                ?: error("Adapter '$path' declares no mixin config")
                            val config = inner.getEntry(configName)
                                ?: error("Adapter '$path' references missing mixin config $configName")
                            val mixins = inner.getInputStream(config).bufferedReader().use {
                                Gson().fromJson(it, JsonObject::class.java)
                            }
                            val pkg = mixins.get("package").asString.replace('.', '/')
                            val declared = mixins.getAsJsonArray("client")
                                ?: error("Mixin config $configName in '$path' has no 'client' list")
                            require(declared.size() == 2) {
                                "Adapter '$path' should declare exactly 2 mixins (one local + one server), " +
                                    "found ${declared.size()}"
                            }
                            declared.forEach { mixin ->
                                val classPath = "$pkg/${mixin.asString.replace('.', '/')}.class"
                                require(inner.getEntry(classPath) != null) {
                                    "Adapter '$path' declares mixin ${mixin.asString} but $classPath is absent"
                                }
                            }
                        }
                    }
                } finally {
                    Files.deleteIfExists(temp)
                }
                logger.lifecycle("  ok $path")
            }

            require(adapters == matrix.mc.size) {
                "Expected ${matrix.mc.size} adapter jars, found $adapters"
            }
        }

        logger.lifecycle("Universal jar verification passed")
    }
}

/**
 * 扫描 core 编译产物的常量池，禁止出现 Minecraft 引用。
 *
 * core 是七个适配器族共用的那一份 Java 8 字节码，一旦引用了 `net/minecraft/`，
 * 它就只能在某一个版本上加载，整个架构随之失效。
 */
val checkArchitecture = tasks.register("checkArchitecture") {
    group = "verification"
    description = "Scan core classes for forbidden net.minecraft references"
    dependsOn(tasks.named("compileJava"))

    doLast {
        val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
        require(classesDir.isDirectory) {
            "Compiled classes not found: ${classesDir.absolutePath}. Run `compileJava` first."
        }

        val forbidden = listOf("net/minecraft/", "com/mojang/")
        val allowed = listOf("net/fabricmc/api/", "net/fabricmc/loader/api/")

        // 直接按常量池的 UTF-8 项扫描，不必引入 ASM。
        fun scan(bytes: ByteArray): List<String> {
            if (bytes.size < 10) return emptyList()
            val magic = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            if (magic != 0xCAFEBABE.toInt()) return emptyList()

            val poolSize = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
            var offset = 10
            val hits = mutableListOf<String>()
            var index = 1
            while (index < poolSize && offset < bytes.size) {
                val tag = bytes[offset].toInt() and 0xFF
                offset++
                when (tag) {
                    1 -> {
                        if (offset + 2 > bytes.size) break
                        val length = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
                        offset += 2
                        if (offset + length > bytes.size) break
                        val text = String(bytes, offset, length, Charsets.UTF_8)
                        if (allowed.none { text.startsWith(it) } && forbidden.any { text.startsWith(it) }) {
                            hits += text
                        }
                        offset += length
                    }

                    7, 8, 16, 19, 20 -> offset += 2
                    3, 4, 9, 10, 11, 12, 17, 18 -> offset += 4
                    5, 6 -> { offset += 8; index++ }
                    15 -> offset += 3
                    else -> break
                }
                index++
            }
            return hits
        }

        val violations = mutableListOf<String>()
        var scanned = 0
        Files.walk(classesDir.toPath()).use { stream ->
            stream.filter { it.toString().endsWith(".class") }.forEach { file ->
                scanned++
                val relative = classesDir.toPath().relativize(file).toString()
                scan(Files.readAllBytes(file)).forEach { violations += "$relative references $it" }
            }
        }

        logger.lifecycle("checkArchitecture: scanned $scanned class files")
        require(violations.isEmpty()) {
            "checkArchitecture FAILED — core must stay platform independent:\n" +
                violations.joinToString("\n") { "  $it" }
        }
        logger.lifecycle("checkArchitecture: PASSED")
    }
}

/**
 * 校验 `gradle/adapters.toml` 与磁盘一致，并模拟 Fabric Loader 的适配器选择。
 *
 * 所有适配器共用 mod id `viarp-adapter-mc`，Loader 会在满足 `minecraft >= X` 的候选里
 * 挑版本最高的那一个。这里逐个版本模拟一遍，确认每个族的最低版本都会选中自己。
 */
val validateAdapterMatrix = tasks.register("validateAdapterMatrix") {
    group = "verification"
    description = "Validate adapters.toml against disk and simulate Fabric Loader adapter selection"

    doLast {
        var errors = 0
        fun fail(message: String) {
            logger.error("  FAIL: $message")
            errors++
        }

        matrix.mc.forEach { adapter ->
            val dir = rootDir.resolve("adapters/mc/${adapter.version}")
            if (!dir.isDirectory) fail("MC ${adapter.version}: missing directory ${dir.path}")

            val local = rootDir.resolve("${adapter.localHookDir}/src/main/java")
            if (!local.isDirectory) fail("MC ${adapter.version}: missing local hook sources ${local.path}")

            val server = rootDir.resolve("${adapter.serverHookDir}/src/main/java")
            if (!server.isDirectory) fail("MC ${adapter.version}: missing server hook sources ${server.path}")
        }

        val duplicates = matrix.mc.groupingBy { it.version }.eachCount().filter { it.value > 1 }
        duplicates.forEach { (version, count) -> fail("MC version $version declared $count times") }

        fun compareVersions(a: String, b: String): Int {
            val left = a.split(".").map { it.toIntOrNull() ?: 0 }
            val right = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(left.size, right.size)) {
                val l = left.getOrElse(i) { 0 }
                val r = right.getOrElse(i) { 0 }
                if (l != r) return l.compareTo(r)
            }
            return 0
        }

        fun winnerFor(version: String): McAdapter? = matrix.mc
            .filter { compareVersions(version, it.version) >= 0 }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }

        matrix.mc.forEach { adapter ->
            val winner = winnerFor(adapter.version)
            when {
                winner == null -> fail("MC ${adapter.version}: no adapter covers this version")
                winner.version != adapter.version ->
                    fail("MC ${adapter.version}: Loader would select ${winner.version} instead")
                else -> logger.lifecycle("  ok MC ${adapter.version} (${adapter.covers}) selects itself")
            }
        }

        require(errors == 0) { "validateAdapterMatrix failed with $errors error(s)" }
        logger.lifecycle("validateAdapterMatrix: PASSED")
    }
}

tasks.build {
    dependsOn(universal)
    dependsOn(checkArchitecture)
    dependsOn(validateAdapterMatrix)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // core 必须是 Java 8 字节码：它是 1.14.4（Java 8）到 26.2（Java 25）共用的那一份。
    // 用 -source/-target 而不是 --release，理由见 docs/DESIGN.md §5。
    options.compilerArgs.addAll(listOf("-source", "8", "-target", "8", "-nowarn"))
    options.isFork = true
    sourceCompatibility = "8"
    targetCompatibility = "8"
}

// 测试只在开发机上跑，不受 Java 8 约束。
tasks.compileTestJava {
    options.compilerArgs.clear()
    options.compilerArgs.add("-nowarn")
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

// core jar 以 `ViaResourcePack-core-<version>.jar` 名字产出；universal 任务把它连同
// 嵌套 jar 重新打包成朴素命名的最终交付物。
tasks.jar {
    archiveAppendix = "core"
}
