plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.togls.hypertweaks.hook.runtime"
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
    implementation(project(":core:logging-hook"))
    compileOnly(libs.libxposed.api)

    testImplementation(libs.junit)
}
