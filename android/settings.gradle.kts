pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "wellfriend-scan-android"
include(":app")
include(":scanner-core")
include(":scanner-perception")
include(":scanner-ui")
include(":scanner-export")
include(":scanner-testing")
