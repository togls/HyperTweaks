import java.util.Properties
import java.util.zip.ZipFile

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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(project(":core:hook-api"))
    implementation(project(":core:logging-api"))
    implementation(project(":core:logging-app"))
    implementation(project(":feature:googlephotos-hook"))
    implementation(project(":feature:ime-hook"))
    implementation(project(":feature:keepalive-hook"))
    implementation(project(":feature:logviewer"))
    implementation(project(":xposed:entry"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.libxposed.service)

    testImplementation(libs.junit)
}

fun registerXposedMetadataVerificationTask(variantName: String): TaskProvider<Task> {
    val variantDisplayName = variantName.replaceFirstChar(Char::uppercase)
    val expectedApiVersion = libs.versions.libxposedApi.get().substringBefore('.')
    val expectedJavaEntry = "io.github.togls.hypertweaks.xposed.HyperTweaksModule"
    val apkDirectory = layout.buildDirectory.dir("outputs/apk/$variantName")
    return tasks.register("verify${variantDisplayName}XposedMetadata") {
        group = "verification"
        description = "Verifies modern Xposed metadata in the $variantName APK."
        dependsOn("assemble$variantDisplayName")
        inputs.dir(apkDirectory)
        doLast {
            val apkFiles = inputs.files.asFileTree
                .matching { include("*.apk") }
                .files
            check(apkFiles.isNotEmpty()) { "No $variantName APK found" }
            apkFiles.forEach { apkFile ->
                ZipFile(apkFile).use { apk ->
                    val moduleEntry = checkNotNull(apk.getEntry("META-INF/xposed/module.prop"))
                    val moduleProperties = Properties().apply {
                        load(apk.getInputStream(moduleEntry))
                    }
                    check(moduleProperties.getProperty("minApiVersion") == expectedApiVersion)
                    check(moduleProperties.getProperty("targetApiVersion") == expectedApiVersion)
                    val javaEntry = checkNotNull(apk.getEntry("META-INF/xposed/java_init.list"))
                    val javaEntries = apk.getInputStream(javaEntry)
                        .bufferedReader()
                        .useLines { lines -> lines.filter(String::isNotBlank).toList() }
                    check(javaEntries == listOf(expectedJavaEntry))
                    checkNotNull(apk.getEntry("META-INF/xposed/scope.list"))
                }
            }
        }
    }
}

val verifyDebugXposedMetadata = registerXposedMetadataVerificationTask("debug")
val verifyReleaseXposedMetadata = registerXposedMetadataVerificationTask("release")

tasks.register("verifyXposedMetadata") {
    group = "verification"
    description = "Verifies modern Xposed metadata in debug and release APKs."
    dependsOn(verifyDebugXposedMetadata, verifyReleaseXposedMetadata)
}
