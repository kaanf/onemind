plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.today.domain)
            implementation(projects.core.data)
            implementation(projects.core.api)
        }
    }
}
