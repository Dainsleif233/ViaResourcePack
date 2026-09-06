plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.aliyun.com/repository/gradle-plugin/")
    maven("https://maven.aliyun.com/repository/public/")
}

dependencies {
    // buildSrc 的运行期类路径同时也是主构建的构建脚本类路径，所以在这里声明 gson，
    // 就不需要根项目再写 `buildscript { classpath(...) }`：universal 打包任务可以
    // 直接 import com.google.gson.*，版本也只有一处。
    implementation(libs.gson)
}
