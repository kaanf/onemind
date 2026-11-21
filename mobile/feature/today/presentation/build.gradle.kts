plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.today.domain)
            implementation(projects.feature.sales.presentation)
        }
    }
}

compose.resources {
    packageOfResClass = "com.onemind.today.presentation.generated.resources"
}
