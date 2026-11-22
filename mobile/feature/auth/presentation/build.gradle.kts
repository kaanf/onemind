plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
        }
        // TestNavHostController has a no-arg constructor only off Android (on Android it needs a Context),
        // so the navigation test lives in iosTest.
        iosTest.dependencies {
            implementation(libs.androidx.navigation.testing)
        }
    }
}

compose.resources {
    packageOfResClass = "com.onemind.auth.presentation.generated.resources"
}
