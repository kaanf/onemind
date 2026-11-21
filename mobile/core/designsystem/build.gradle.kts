plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.presentation)
        }
    }
}

compose.resources {
    packageOfResClass = "com.onemind.core.designsystem.generated.resources"
}
