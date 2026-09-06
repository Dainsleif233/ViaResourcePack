// buildSrc 是独立构建，不会继承主构建自动发现的版本目录。这里显式声明，
// 让 build-logic 代码与项目其余部分共享同一份版本，而不是写死字面量。
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
