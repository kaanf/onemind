plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.datetime)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.onemind.core.presentation.generated.resources"
}
