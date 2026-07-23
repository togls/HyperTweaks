plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.togls.hypertweaks.ime.hook"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:hook-api"))

    testImplementation(libs.junit)
}
