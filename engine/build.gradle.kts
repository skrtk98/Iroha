// Android に依存しない変換エンジン。JVM 上で単体テストできるよう純 Kotlin で保つ。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

// mozc の辞書から iroha.dic を作る。mozc のファイルはリポジトリ直下の .mozc にキャッシュする。
// 自立語の最大コストを下げると辞書が小さくなるが、珍しい語が出なくなる。
val dictionaryMaxCost = providers.gradleProperty("iroha.dictionary.maxCost").orElse("8500")
val dictionaryFile = layout.buildDirectory.file("dictionary/iroha.dic")
val mozcCacheDir = rootProject.layout.projectDirectory.dir(".mozc").asFile.absolutePath

val buildDictionary = tasks.register<JavaExec>("buildDictionary") {
    description = "Builds iroha.dic from the mozc OSS dictionary."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.skrtk98.iroha.engine.tools.BuildDictionaryKt")
    maxHeapSize = "3g"
    inputs.property("maxCost", dictionaryMaxCost)
    outputs.file(dictionaryFile)
    args(mozcCacheDir, dictionaryFile.get().asFile.absolutePath, dictionaryMaxCost.get())
}

configurations.consumable("dictionary")
artifacts.add("dictionary", dictionaryFile) { builtBy(buildDictionary) }

tasks.test {
    // 実辞書を使う結合テストのため
    dependsOn(buildDictionary)
    systemProperty("iroha.dictionary", dictionaryFile.get().asFile.absolutePath)
    systemProperty("iroha.mozcDir", mozcCacheDir)
    maxHeapSize = "1g"
}
