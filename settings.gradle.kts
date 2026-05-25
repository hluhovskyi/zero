pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Zero"
include(":app")
include(":zero-api")
include(":zero-database")
include(":zero-core")
include(":zero-ui")
include(":zero-image-loading")
include(":zero-crash")
include(":lint-rules")
include(":zero-sync")
include(":zero-backup")
include(":zero-remote")
include(":zero-test-bridge")
