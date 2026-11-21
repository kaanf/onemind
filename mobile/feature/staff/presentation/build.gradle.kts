plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.staff.domain)
        }
    }
}

compose.resources {
    packageOfResClass = "com.onemind.staff.presentation.generated.resources"
}
