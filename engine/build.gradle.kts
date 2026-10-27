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

// 文字の並びの言語モデル。コーパスは固定版を取得できないため、作ったモデルをリポジトリに置く。
// 作り直すときは lm/README.md の手順でコーパスを用意し、-Piroha.corpus=<ファイル,...> を付けて実行する
val languageModelFile = layout.projectDirectory.file("lm/iroha.lm")

tasks.register<JavaExec>("buildLanguageModel") {
    description = "Builds lm/iroha.lm from the corpus files given by -Piroha.corpus."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.skrtk98.iroha.engine.tools.BuildLanguageModelKt")
    // ウェブ文書を含むコーパス (約 1 億字) の 3-gram を数えるため
    maxHeapSize = "8g"
    // コーパスを渡さなければ、ツールが使い方を出して失敗する
    val output = languageModelFile.asFile.absolutePath
    val corpus = providers.gradleProperty("iroha.corpus").map { it.split(',') }.orElse(emptyList())
    argumentProviders.add(CommandLineArgumentProvider { listOf(output, "2") + corpus.get() })
}

// ウェブの文から書き言葉に近い文を選ぶ。lm/README.md の手順で、-Piroha.select=<出力>,<文字数>,<書き言葉>,<ウェブ> を付けて実行する
tasks.register<JavaExec>("selectWebSentences") {
    description = "Selects web sentences close to the written corpus, given by -Piroha.select."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.skrtk98.iroha.engine.tools.SelectWebSentencesKt")
    maxHeapSize = "8g"
    val select = providers.gradleProperty("iroha.select").map { it.split(',') }.orElse(emptyList())
    argumentProviders.add(CommandLineArgumentProvider { select.get() })
}

configurations.consumable("dictionary")
artifacts.add("dictionary", dictionaryFile) { builtBy(buildDictionary) }
artifacts.add("dictionary", languageModelFile)

tasks.test {
    // 実辞書を使う結合テストのため
    dependsOn(buildDictionary)
    systemProperty("iroha.dictionary", dictionaryFile.get().asFile.absolutePath)
    systemProperty("iroha.mozcDir", mozcCacheDir)
    systemProperty("iroha.languageModel", languageModelFile.asFile.absolutePath)
    inputs.file(languageModelFile)
    // 言語モデルの重みを試すとき用 (-Diroha.lmWeight=…)
    providers.systemProperty("iroha.lmWeight").orNull?.let { systemProperty("iroha.lmWeight", it) }
    // 手元の Microsoft Research IME Corpus で測るとき用 (-Diroha.msImeDir=…)。再配布できないのでリポジトリには置かない
    providers.systemProperty("iroha.msImeDir").orNull?.let { dir ->
        systemProperty("iroha.msImeDir", dir)
        // 差し替えたコーパスで測り直せるよう、中身も入力にする
        inputs.files(fileTree(dir) { include("*.read.3000.txt", "*.ref.3000.txt") }).withPropertyName("msImeCorpus")
    }
    providers.systemProperty("iroha.msImeMisses").orNull?.let { systemProperty("iroha.msImeMisses", it) }
    maxHeapSize = "1g"
}
