import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    alias(libs.plugins.shadow)
}

version = rootProject.version

repositories {
    maven("https://maven.aliyun.com/repository/public/")
    maven("https://jitpack.io")
    mavenCentral()
}

/**
 * 只被 shade 进产物、不进任何 POM 的依赖。
 *
 * ResourcePackConverter（MIT，钉在提交 `63ba2cd402` 以拿到 1.21.10/1.21.11 的转换器）
 * + 它的两个传递依赖。gson 必须打进来并 relocate：RPC 用了 gson 2.11 才有的
 * `com.google.gson.Strictness` / `JsonReader.setStrictness`，而 MC 1.14.4 只带 gson 2.8.0，
 * 直接用游戏自带的会 NoSuchMethodError。zip4j 游戏完全不带，同样必须打包。
 */
val shaded: Configuration = configurations.create("shaded") {
    isCanBeResolved = true
    isCanBeConsumed = false
    // RPC 的根 POM 把 jopt-simple 声明成所有模块的依赖，但只有它的 Console 模块
    // （命令行解析）真的用；我们只调 API，不需要它。
    exclude(group = "net.sf.jopt-simple")
}

dependencies {
    // ConverterEngine 接口住在 core 里；编译期需要，运行期由 Fabric 的类加载器提供。
    compileOnly(project(":"))
    compileOnly(libs.slf4j.api)

    shaded(libs.rpc.library)
    shaded(libs.rpc.forwards)
    shaded(libs.rpc.backwards)
    shaded(libs.gson)
    shaded(libs.zip4j)

    // 同一批坐标再声明一次 compileOnly：shaded 只负责打包，编译期可见性要单独给。
    compileOnly(libs.rpc.library)
    compileOnly(libs.rpc.forwards)
    compileOnly(libs.rpc.backwards)
    compileOnly(libs.gson)
    compileOnly(libs.zip4j)

    // 测试跑真实转换，所以 RPC 与 core 都要进运行期类路径。
    testImplementation(project(":"))
    testImplementation(libs.rpc.library)
    testImplementation(libs.rpc.forwards)
    testImplementation(libs.rpc.backwards)
    testImplementation(libs.gson)
    testImplementation(libs.zip4j)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // 测试代码用 Java 17 语法编译（见下面的 compileTestJava 配置），运行也用当前 JDK。
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

tasks.processResources {
    val modVersion = project.version.toString()
    // RPC 的版本坐标要进产物：describe() 会把它写进缓存元数据，换版本后旧缓存才会失效。
    val rpcVersion = libs.versions.rpc.get()
    inputs.property("version", modVersion)
    inputs.property("rpcVersion", rpcVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
    filesMatching("top/syshub/viarp/library/rpc.properties") {
        expand("rpcVersion" to rpcVersion)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations.set(listOf(shaded))
    archiveClassifier.set("")

    // gson / zip4j 必须改包名，否则会和游戏自带的 gson 撞车（旧版本 API 更少）。
    relocate("com.google.gson", "top.syshub.viarp.shaded.gson")
    relocate("net.lingala.zip4j", "top.syshub.viarp.shaded.zip4j")

    // RPC 自己的包名**不**改：它用 `PackConverter.class.getResourceAsStream("/protocol.json")`
    // 这类绝对资源路径加载数据文件，relocate 类名不会同步改资源路径，保持原样最安全。

    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("module-info.class")
    // errorprone / jsr305 之类的编译期注解不需要进运行期产物。
    exclude("com/google/errorprone/**")
    exclude("javax/annotation/**")

    mergeServiceFiles()
}

// 主产物就是 shadowJar：根项目的 universal 任务会把它作为 jar-in-jar 嵌进主 jar。
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // 与 core 一致编译成 Java 8 字节码：这个 jar 要能在 1.14.4（Java 8）下加载。
    // 用 -source/-target 而不是 --release，理由见 docs/DESIGN.md §5。
    options.compilerArgs.addAll(listOf("-source", "8", "-target", "8", "-nowarn"))
    options.isFork = true
    sourceCompatibility = "8"
    targetCompatibility = "8"
}

// 测试只在开发机上跑，不受 Java 8 约束——JUnit 5 本身就需要 Java 8+，但用新语法
// 写测试更省事。
tasks.compileTestJava {
    options.compilerArgs.clear()
    options.compilerArgs.addAll(listOf("-nowarn"))
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
