plugins {
    alias(libs.plugins.convention.cmp.application)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            implementation(projects.core.domain)
            implementation(projects.core.presentation)
            implementation(projects.core.designsystem)

            implementation(projects.feature.auth.data)
            implementation(projects.feature.auth.presentation)
            implementation(projects.feature.today.data)
            implementation(projects.feature.today.presentation)
            implementation(projects.feature.sales.data)
            implementation(projects.feature.sales.presentation)
            implementation(projects.feature.staff.data)
            implementation(projects.feature.staff.presentation)
        }
    }
}
