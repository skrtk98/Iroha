import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.skrtk98.iroha"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.skrtk98.iroha"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    // 署名の設定はリポジトリの外の signing.properties から読む（書式は signing.properties.example）。
    // 場所は -Piroha.signing.file で変えられ、使う鍵は -Piroha.signing.key=app|upload で選ぶ（既定は app）。
    // ファイルが無ければ release は未署名で出力する（F-Droid のビルドサーバーや、鍵を持たない人のビルド）。
    val signingFile = rootProject.file(providers.gradleProperty("iroha.signing.file").getOrElse("signing.properties"))
    // fileContents 経由で読むと、ファイルの有無と中身の変化を configuration cache が追跡する
    val signingText = providers.fileContents(rootProject.layout.projectDirectory.file(signingFile.absolutePath)).asText
    val releaseSigning = if (signingText.isPresent) {
        val keyName = providers.gradleProperty("iroha.signing.key").getOrElse("app")
        require(keyName == "app" || keyName == "upload") { "iroha.signing.key must be app or upload: $keyName" }
        val props = Properties().apply { signingText.get().reader().use { load(it) } }
        fun prop(name: String): String = props.getProperty("$keyName.$name")?.takeIf { it.isNotBlank() }
            ?: error("$signingFile has no $keyName.$name")
        signingConfigs.create("release") {
            storeFile = file(prop("storeFile"))
            storePassword = prop("password")
            keyAlias = prop("keyAlias")
            keyPassword = prop("password")
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }
    androidResources {
        localeFilters += listOf("ja", "en")
        // 辞書は APK から直接 mmap するため圧縮しない
        noCompress += "dic"
    }
    dependenciesInfo {
        // APK に Google 署名の依存情報ブロックを入れない。F-Droid が中身を検証できず、再現ビルドの妨げになる。
        // Play に出す AAB には残す。
        includeInApk = false
        includeInBundle = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/kotlin/**"
            excludes += "DebugProbesKt.bin"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// engine モジュールが生成した iroha.dic を assets に入れる
val dictionary = configurations.dependencyScope("dictionary")
val resolvableDictionary = configurations.resolvable("dictionaryFiles") { extendsFrom(dictionary.get()) }

abstract class CopyDictionaryTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val dictionaryFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        fileSystem.sync {
            from(dictionaryFiles)
            into(outputDir)
        }
    }
}

val copyDictionary = tasks.register<CopyDictionaryTask>("copyDictionary") {
    dictionaryFiles.from(resolvableDictionary)
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyDictionary, CopyDictionaryTask::outputDir)
    }
}

dependencies {
    implementation(project(":engine"))
    "dictionary"(project(path = ":engine", configuration = "dictionary"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
