plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            // The business day and the venue's time zone come from the API (rule 6).
            api(libs.kotlinx.datetime)
        }
    }
}
