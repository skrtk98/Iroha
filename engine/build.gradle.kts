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
