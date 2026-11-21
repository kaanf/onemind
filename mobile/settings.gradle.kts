rootProject.name = "onemind"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":core:api")
include(":core:data")
include(":core:domain")
include(":core:presentation")
include(":core:designsystem")
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")
include(":feature:today:domain")
include(":feature:today:data")
include(":feature:today:presentation")
include(":feature:sales:domain")
include(":feature:sales:data")
include(":feature:sales:presentation")
include(":feature:staff:domain")
include(":feature:staff:data")
include(":feature:staff:presentation")
include(":composeApp")
include(":androidApp")
