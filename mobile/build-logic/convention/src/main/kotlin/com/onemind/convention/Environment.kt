package com.onemind.convention

import org.gradle.api.GradleException
import org.gradle.api.Project

/** The build variants of ADR 0012 §2. */
internal val ENVIRONMENTS = listOf("dev", "prod")

/**
 * The single environment switch: `-Ponemind.environment=dev|prod` (default `dev` in `gradle.properties`).
 * It selects the BuildKonfig flavor of shared modules and the one enabled Android flavor, so a build
 * can't mix a dev app with prod values. The iOS build phase passes it from the Xcode configuration.
 */
internal fun Project.oneMindEnvironment(): String {
    val environment = providers.gradleProperty("onemind.environment").getOrElse("dev")
    if (environment !in ENVIRONMENTS) {
        throw GradleException("onemind.environment must be one of $ENVIRONMENTS, was '$environment'")
    }
    return environment
}
