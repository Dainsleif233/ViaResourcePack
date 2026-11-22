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

// 源 jar 以版本族目录命名；嵌套路径明确标记它是 MC 适配器。
fun nestedJarPath(adapter: McAdapter): String =
    "META-INF/jars/viarp-adapter-mc-${adapter.version}-${project.version}.jar"

fun nestedJarFile(adapter: McAdapter): File {
    val target = project(":${adapter.projectPath}")
    return target.layout.buildDirectory.file("libs/${target.name}-${target.version}.jar").get().asFile
}

fun packageUniversalJar() {
    // universal fat jar 是最终交付物，占用朴素的 `<name>-<version>.jar` 名字，取代
    // `:jar` 产出的 core jar。打包时先复制到临时文件、写完再原子移动覆盖输出，
    // 避免直接打开 Gradle 追踪的输出文件（会撞上独占文件锁）。
    val outputFile = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar").get().asFile
    val coreJar = layout.buildDirectory.file("libs/${project.name}-core-${project.version}.jar").get().asFile
    require(coreJar.isFile) { "Core jar missing: ${coreJar.absolutePath}. Run `:jar` first." }

    matrix.mc.forEach { adapter ->
        val jar = nestedJarFile(adapter)
        require(jar.isFile) {
            "Nested jar missing for ${adapter.projectPath}: ${jar.absolutePath}. " +
                "Build `:${adapter.projectPath}:${adapter.jarTask}` first."
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

            matrix.mc.forEach { adapter ->
                val source = nestedJarFile(adapter)
                val name = nestedJarPath(adapter)
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

/** 构建全部适配器，与 core 一起打成 universal jar。 */
val universal = tasks.register("universal") {
    group = "build"
    description = "Build and embed every Minecraft adapter in the universal jar"

    matrix.mc.forEach { dependsOn(":${it.projectPath}:${it.jarTask}") }
    dependsOn(tasks.named("jar"))

    outputs.upToDateWhen {
        matrix.mc.all { project(":${it.projectPath}").tasks.getByName(it.jarTask).state.upToDate } &&
            tasks.named("jar").get().state.upToDate
    }

    inputs.property("adapterPaths", matrix.mc.map { nestedJarPath(it) })
    outputs.file(layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))

    doLast {
        packageUniversalJar()
    }
}

// 同时检查清单和实际 ZIP 条目，防止旧依赖以直接打包的类或资源残留。
fun verifyNoLegacyDependencies(zip: ZipFile, mod: JsonObject, label: String) {
    val legacyId = "viarp-library"
    val relations = listOf("depends", "recommends", "suggests", "conflicts", "breaks")
    require(
        mod.get("id")?.asString != legacyId &&
            mod.getAsJsonArray("provides")?.none { it.asString == legacyId } != false &&
            relations.none { mod.getAsJsonObject(it)?.has(legacyId) == true }
    ) {
        "$label fabric.mod.json references the legacy mod $legacyId"
    }

    val forbidden = listOf(
        "com/agentdid127/resourcepack/",
        "com/google/gson/",
        "net/lingala/zip4j/",
        "top/syshub/viarp/library/",
        "top/syshub/viarp/shaded/"
    )
    val versionedPrefix = Regex("^META-INF/versions/[0-9]+/")
    val legacyEntry = zip.entries().asSequence().firstOrNull { entry ->
        val path = entry.name.replaceFirst(versionedPrefix, "")
        forbidden.any { path.startsWith(it) }
    }
    require(legacyEntry == null) { "$label bundles an old dependency entry: ${legacyEntry?.name}" }
}

fun nestedJarEntries(zip: ZipFile): List<String> = zip.entries().asSequence()
    .filter {
        !it.isDirectory && (it.name.startsWith("META-INF/jars/", ignoreCase = true) ||
            it.name.endsWith(".jar", ignoreCase = true))
    }
    .map { it.name }
    .toList()

/**
 * 校验 universal jar 的嵌套结构。
 *
 * 刻意不声明 `dependsOn`，只声明顺序：它校验的是「谁产出了这个 jar」，
 * 声明依赖会让 `universal` 之外的路径重复打包一次。
 */
val verifyUniversalJar = tasks.register("verifyUniversalJar") {
    group = "verification"
    description = "Verify the universal jar contains only the expected adapters and their two mixins each"
    mustRunAfter(universal)

    doLast {
        val universalJar = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar").get().asFile
        require(universalJar.isFile) { "Universal jar not found: ${universalJar.absolutePath}" }

        val expected = matrix.mc.associateBy { nestedJarPath(it) }
        fun requireExactAdapterPaths(paths: List<String>, source: String) {
            val actual = paths.toSet()
            require(paths.size == expected.size && actual == expected.keys) {
                val duplicates = paths.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                "$source must contain each expected adapter exactly once: missing=${expected.keys - actual}, " +
                    "unexpected=${actual - expected.keys}, duplicates=$duplicates"
            }
        }
        logger.lifecycle("Verifying ${universalJar.name} (expecting ${expected.size} nested adapters)")

        ZipFile(universalJar).use { zip ->
            val rootEntry = zip.getEntry("fabric.mod.json") ?: error("Universal jar has no fabric.mod.json")
            val rootMod = zip.getInputStream(rootEntry).bufferedReader().use {
                Gson().fromJson(it, JsonObject::class.java)
            }
            verifyNoLegacyDependencies(zip, rootMod, "Universal jar")
            require(rootMod.getAsJsonObject("depends")?.has("viarp-adapter-mc") == true) {
                "Root fabric.mod.json must depend on viarp-adapter-mc"
            }

            val jars = rootMod.getAsJsonArray("jars") ?: error("Root fabric.mod.json has no 'jars' array")
            val declaredPaths = jars.map {
                it.asJsonObject.get("file")?.asString ?: error("A 'jars' entry has no 'file' field")
            }
            requireExactAdapterPaths(declaredPaths, "Root fabric.mod.json jars")
            requireExactAdapterPaths(nestedJarEntries(zip), "Universal archive")

            for ((path, adapter) in expected) {
                val entry = zip.getEntry(path) ?: error("Nested adapter '$path' is missing from the archive")
                val temp = Files.createTempFile("viarp-verify-", ".jar")
                try {
                    zip.getInputStream(entry).use { Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING) }
                    ZipFile(temp.toFile()).use { inner ->
                        val modEntry = inner.getEntry("fabric.mod.json")
                            ?: error("Nested adapter '$path' has no fabric.mod.json")
                        val mod = inner.getInputStream(modEntry).bufferedReader().use {
                            Gson().fromJson(it, JsonObject::class.java)
                        }
                        verifyNoLegacyDependencies(inner, mod, "Adapter '$path'")
                        require(
                            mod.get("id")?.asString == "viarp-adapter-mc" &&
                                mod.get("version")?.asString == "${project.version}-${adapter.version}"
                        ) {
                            "Nested jar '$path' is not the expected adapter for Minecraft ${adapter.version}"
                        }
                        require((mod.getAsJsonArray("jars")?.size() ?: 0) == 0 && nestedJarEntries(inner).isEmpty()) {
                            "Adapter '$path' must not contain or declare nested jars"
                        }

                        val configs = mod.getAsJsonArray("mixins") ?: error("Adapter '$path' declares no mixin config")
                        require(configs.size() == 1) { "Adapter '$path' must declare exactly one mixin config" }
                        val configName = configs[0].asString
                        val config = inner.getEntry(configName)
                            ?: error("Adapter '$path' references missing mixin config $configName")
                        val mixins = inner.getInputStream(config).bufferedReader().use {
                            Gson().fromJson(it, JsonObject::class.java)
                        }
                        val pkg = mixins.get("package").asString.replace('.', '/')
                        val declared = mixins.getAsJsonArray("client")?.map { it.asString }
                            ?: error("Mixin config $configName in '$path' has no 'client' list")
                        require(
                            declared.size == adapter.mixins.size && declared.toSet() == adapter.mixins.toSet() &&
                                listOf("mixins", "server").all { (mixins.getAsJsonArray(it)?.size() ?: 0) == 0 }
                        ) {
                            "Adapter '$path' should declare only the expected client mixins: ${adapter.mixins}"
                        }
                        declared.forEach { mixin ->
                            val classPath = "$pkg/${mixin.replace('.', '/')}.class"
                            require(inner.getEntry(classPath)?.isDirectory == false) {
                                "Adapter '$path' declares mixin $mixin but $classPath is absent"
                            }
                        }
                    }
                } finally {
                    Files.deleteIfExists(temp)
                }
                logger.lifecycle("  ok $path")
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

            val reload = rootDir.resolve("${adapter.reloadHookDir}/src/main/java")
            if (!reload.isDirectory) fail("MC ${adapter.version}: missing reload hook sources ${reload.path}")

            val reloadUi = rootDir.resolve("${adapter.reloadUiDir}/src/main/java")
            if (!reloadUi.isDirectory) fail("MC ${adapter.version}: missing progress UI sources ${reloadUi.path}")
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
    dependsOn(verifyUniversalJar)
    dependsOn(checkArchitecture)
    dependsOn(validateAdapterMatrix)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // 各版本的适配器共用同一份 core，必须保持 Java 8 字节码以支持旧客户端。
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
