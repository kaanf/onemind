package com.onemind.convention

import org.gradle.api.Project

/**
 * Maps a Gradle path to the package convention of ADR 0011:
 * `:core:data` → `com.onemind.core.data`, `:feature:sales:presentation` → `com.onemind.sales.presentation`,
 * `:composeApp` → `com.onemind.composeapp`.
 */
fun Project.pathToPackageName(): String {
    val relativePackageName =
        path
            .removePrefix(":feature")
            .replace(':', '.')
            .replace("-", "")
            .lowercase()

    return "com.onemind$relativePackageName"
}
