import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")

val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

fun signingValue(
    propertyKey: String,
    environmentKey: String,
): String? {
    return keystoreProperties.getProperty(propertyKey)
        ?: providers.environmentVariable(environmentKey).orNull
}

val releaseStoreFile = signingValue(
    propertyKey = "storeFile",
    environmentKey = "ANDROID_KEYSTORE_PATH",
)

val releaseStorePassword = signingValue(
    propertyKey = "storePassword",
    environmentKey = "ANDROID_KEYSTORE_PASSWORD",
)

val releaseKeyAlias = signingValue(
    propertyKey = "keyAlias",
    environmentKey = "ANDROID_KEY_ALIAS",
)

val releaseKeyPassword = signingValue(
    propertyKey = "keyPassword",
    environmentKey = "ANDROID_KEY_PASSWORD",
)

val hasReleaseSigning =
    !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "io.github.togls.hypertweaks"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.togls.hypertweaks"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }

            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}