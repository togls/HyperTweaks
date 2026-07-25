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
val featureHookFiles = fileTree(rootDir) {
    include("feature/*-hook/src/**/*.kt", "feature/*-hook/build.gradle.kts")
    exclude("**/build/**", "**/.gradle/**")
}
val featureHookMainSourceFiles = fileTree(rootDir) {
    include("feature/*-hook/src/main/**/*.kt")
    exclude("**/build/**", "**/.gradle/**")
}

val verifyHookArchitecture = tasks.register("verifyHookArchitecture") {
    group = "verification"
    description = "Checks Hook module dependency boundaries and legacy API removal."
    inputs.files(
        hookSourceFiles,
        hookModuleBuildFiles,
        featureHookFiles,
        featureHookMainSourceFiles,
    )
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
        val forbiddenFeatureReferences = listOf(
            "androidx.activity.",
            "androidx.compose.",
            "androidx.room",
            "top.yukonga.miuix",
            "project(\":app\")",
            "project(\":core:logging-app\")",
            "project(\":feature:logviewer\")",
        )
        val featureHookArchitectureFiles = architectureFiles.filter { architectureFile ->
            val path = architectureFile.invariantSeparatorsPath
            path.contains("/feature/") && path.contains("-hook/")
        }
        val featureBoundaryViolations = featureHookArchitectureFiles.filter { featureFile ->
            val content = featureFile.readText()
            forbiddenFeatureReferences.any(content::contains)
        }
        check(featureBoundaryViolations.isEmpty()) {
            "Feature Hook modules depend on UI or app-only infrastructure: " +
                featureBoundaryViolations.joinToString()
        }
        val requiredDiagnosticEvents = setOf(
            "target.resolve.started",
            "target.resolve.succeeded",
            "target.resolve.failed",
            "hook.callback.entered",
            "hook.callback.transformed",
            "hook.callback.bypassed",
            "hook.callback.failed",
        )
        val featureModuleNames = listOf(
            "googlephotos-hook",
            "ime-hook",
            "keepalive-hook",
        )
        val missingDiagnosticEvents = featureModuleNames.flatMap { moduleName ->
            val moduleContent = featureHookArchitectureFiles
                .filter { sourceFile ->
                    val path = sourceFile.invariantSeparatorsPath
                    path.contains("/feature/$moduleName/src/main/") &&
                        sourceFile.extension == "kt"
                }
                .joinToString(separator = "\n") { sourceFile -> sourceFile.readText() }
            requiredDiagnosticEvents
                .filterNot(moduleContent::contains)
                .map { eventName -> "$moduleName:$eventName" }
        }
        check(missingDiagnosticEvents.isEmpty()) {
            "Feature Hook modules are missing required diagnostic events: " +
                missingDiagnosticEvents.joinToString()
        }
    }
}

tasks.register("verifyXposedMigration") {
    group = "verification"
    description = "Runs Hook architecture, catalog, and packaged metadata verification."
    dependsOn(
        verifyHookArchitecture,
        ":feature:googlephotos-hook:testReleaseUnitTest",
        ":xposed:entry:testDebugUnitTest",
        ":app:verifyXposedMetadata",
    )
}
