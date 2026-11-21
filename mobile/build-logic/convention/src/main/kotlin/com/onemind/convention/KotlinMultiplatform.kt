package com.onemind.convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Android + iOS targets for every shared module. Only `composeApp` exports an iOS framework
 * (see [configureIosFramework]); library modules are linked into it.
 */
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
            compileSdk = libs.intVersion("projectCompileSdkVersion")
            minSdk = libs.intVersion("projectMinSdkVersion")
            namespace = pathToPackageName()
            experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
            // Runs commonTest on the JVM (`testAndroidHostTest`), so tests need no simulator or emulator.
            withHostTest {}
        }

        iosArm64()
        iosSimulatorArm64()

        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
}

internal fun Project.configureIosFramework() {
    extensions.configure<KotlinMultiplatformExtension> {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
                binaryOption("bundleId", "com.onemind.composeapp")
            }
        }
    }
}
