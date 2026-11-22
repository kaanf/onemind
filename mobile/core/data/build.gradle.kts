import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import java.util.Properties

plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.convention.buildkonfig)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.api)

            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

// Optional dev override in the git-ignored local.properties, e.g. a physical phone reaching the
// backend on the LAN: onemind.apiBaseUrl=http://192.168.1.20:8080
val localApiBaseUrl: String? =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text -> Properties().apply { load(text.reader()) }.getProperty("onemind.apiBaseUrl")?.trim() }
        .orNull
        ?.also { url ->
            require(Regex("^https?://[^/\\s]+$").matches(url)) {
                "onemind.apiBaseUrl must be scheme://host[:port] without a path, was '$url'"
            }
        }

val appVersion: String = libs.versions.projectVersionName.get()

// API host per environment (ADR 0012 §2); the client appends the spec's `/api/v1`. No secrets here.
// The unflavored block is dev; the buildkonfig convention plugin fails if prod misses a field.
buildkonfig {
    defaultConfigs {
        buildConfigField(STRING, "ENVIRONMENT", "dev")
        // Sent as X-App-Version; the API answers 426 below the platform's minimum.
        buildConfigField(STRING, "APP_VERSION", appVersion)
        // The iOS simulator reaches the local docker backend on localhost.
        buildConfigField(STRING, "API_BASE_URL", localApiBaseUrl ?: "http://localhost:8080")
    }
    defaultConfigs("prod") {
        buildConfigField(STRING, "ENVIRONMENT", "prod")
        buildConfigField(STRING, "APP_VERSION", appVersion)
        // Placeholder until hosting is set up (phase 7); matches the JWT issuer of ADR 0004.
        buildConfigField(STRING, "API_BASE_URL", "https://api.onemind.app")
    }
    targetConfigs("dev") {
        // The Android emulator reaches the host machine on 10.0.2.2.
        create("android") {
            buildConfigField(STRING, "API_BASE_URL", localApiBaseUrl ?: "http://10.0.2.2:8080")
        }
    }
}
