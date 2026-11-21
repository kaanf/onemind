plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.sales.domain)
        }
    }
}

compose.resources {
    packageOfResClass = "com.onemind.sales.presentation.generated.resources"
}
