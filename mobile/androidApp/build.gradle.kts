import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    alias(libs.plugins.convention.android.application.compose)
}

android {
    buildTypes {
        // `devDemo`: a release-like build (R8, not debuggable) against the dev backend, for demos and
        // frame-time measurements (Compose debug builds are much slower). Signed with the local debug
        // keystore; never a store build, and disabled for prod below.
        create("demo") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    beforeVariants(selector().withFlavor("environment" to "prod").withBuildType("demo")) { it.enable = false }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.koin.android)
}
