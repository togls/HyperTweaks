plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val hookSourceFiles = fileTree(rootDir) {
    include("**/*.kt", "**/*.java")
    exclude("**/build/**", "**/.gradle/**")
}
val hookModuleBuildFiles = fileTree(rootDir) {
    include("**/build.gradle.kts")
    exclude("build.gradle.kts", "**/build/**", "**/.gradle/**")
}

val verifyHookArchitecture = tasks.register("verifyHookArchitecture") {
    group = "verification"
    description = "Checks Hook module dependency boundaries and legacy API removal."
    inputs.files(hookSourceFiles, hookModuleBuildFiles)
    doLast {
        val architectureFiles = inputs.files.files
        val libxposedApiViolations = architectureFiles.filter { architectureFile ->
            val normalizedPath = architectureFile.invariantSeparatorsPath
            val allowedModule = normalizedPath.contains("/core/hook-runtime/") ||
                normalizedPath.contains("/xposed/entry/")
            val sourceReference = architectureFile.extension != "kts" &&
                architectureFile.readText().contains("io.github.libxposed.api")
            val dependencyReference = architectureFile.extension == "kts" &&
                architectureFile.readText().contains("libs.libxposed.api")
            !allowedModule && (sourceReference || dependencyReference)
        }
        check(libxposedApiViolations.isEmpty()) {
            "Only Hook runtime and entry may reference libxposed API: " +
                libxposedApiViolations.joinToString()
        }
        val legacyApiViolations = architectureFiles.filter { sourceFile ->
            sourceFile.readText().contains("de.robv.android.xposed")
        }
        check(legacyApiViolations.isEmpty()) {
            "Legacy Xposed API references remain: ${legacyApiViolations.joinToString()}"
        }
    }
}

tasks.register("verifyXposedMigration") {
    group = "verification"
    description = "Runs Hook architecture, catalog, and packaged metadata verification."
    dependsOn(
        verifyHookArchitecture,
        ":xposed:entry:testDebugUnitTest",
        ":app:verifyXposedMetadata",
    )
}
