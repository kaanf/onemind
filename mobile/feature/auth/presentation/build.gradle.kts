plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
        }
    }
}

compose.resources {
    packageOfResClass = "com.onemind.auth.presentation.generated.resources"
}
