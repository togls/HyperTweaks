plugins {
    alias(libs.plugins.android.library)
}

val libxposedApiVersion = libs.versions.libxposedApi.get().substringBefore('.')

android {
    namespace = "io.github.togls.hypertweaks.xposed.entry"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("int", "LIBXPOSED_API_VERSION", libxposedApiVersion)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:hook-runtime"))
    implementation(project(":core:logging-hook"))
    implementation(project(":feature:googlephotos-hook"))
    implementation(project(":feature:ime-hook"))
    implementation(project(":feature:keepalive-hook"))
    compileOnly(libs.libxposed.api)

    testImplementation(libs.junit)
    testImplementation(libs.libxposed.api)
}
