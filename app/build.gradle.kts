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
        versionCode = 1
        versionName = "0.1.0"
    }

    // 署名鍵はリポジトリに置かない。~/.gradle/gradle.properties か -Piroha.signing.* で渡す。
    // 未設定なら release は未署名で出力する（F-Droid のビルドサーバーや、鍵を持たない人のビルド）。
    val signingStoreFile = providers.gradleProperty("iroha.signing.storeFile")
    val releaseSigning = if (signingStoreFile.isPresent) {
        signingConfigs.create("release") {
            storeFile = file(signingStoreFile.get())
            storePassword = providers.gradleProperty("iroha.signing.storePassword").get()
            keyAlias = providers.gradleProperty("iroha.signing.keyAlias").get()
            keyPassword = providers.gradleProperty("iroha.signing.keyPassword").get()
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
