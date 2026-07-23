@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HyperTweaks"
include(":app")
include(":core:hook-api")
include(":core:hook-runtime")
include(":core:logging-api")
include(":core:logging-app")
include(":core:logging-hook")
include(":feature:googlephotos-hook")
include(":feature:ime-hook")
include(":feature:keepalive-hook")
include(":feature:logviewer")
include(":xposed:entry")
