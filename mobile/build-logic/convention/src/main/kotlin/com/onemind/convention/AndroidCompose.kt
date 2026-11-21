package com.onemind.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose for the Android host. Versions come from Compose Multiplatform (no androidx Compose
 * BOM), so the host and the shared UI never resolve different Compose versions.
 */
internal fun Project.configureAndroidCompose(applicationExtension: ApplicationExtension) {
    applicationExtension.buildFeatures {
        compose = true
    }

    dependencies {
        "implementation"(libs.library("androidx-activity-compose"))
        "debugImplementation"(libs.library("jetbrains-compose-ui-tooling"))
    }
}
