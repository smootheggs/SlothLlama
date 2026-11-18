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

// The Sloth Lobster: SlothLlama's sister app, the only one of the two that goes online.
// A separate build from android-project/, so SlothLlama's APK can never pick up its network code.
rootProject.name = "SlothLobster"
include(":app")
