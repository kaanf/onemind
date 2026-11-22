import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.onemind.convention.ENVIRONMENTS
import com.onemind.convention.configureKotlinAndroid
import com.onemind.convention.intVersion
import com.onemind.convention.libs
import com.onemind.convention.oneMindEnvironment
import com.onemind.convention.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File

/**
 * Android host. Release signing comes from `ONEMIND_*` Gradle properties or environment variables;
 * without them the release build is unsigned. Kotlin is built into AGP 9 (no `kotlin.android`).
 *
 * Flavors `dev` (`com.onemind.app.dev`) and `prod` (ADR 0012 §2). Shared modules are compiled once per
 * build with the BuildKonfig values of `onemind.environment`, so only the matching flavor is enabled.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("com.onemind.convention.ktlint")
            }

            fun signingValue(name: String) = providers.gradleProperty(name).orElse(providers.environmentVariable(name))

            val releaseStoreFile = signingValue("ONEMIND_STORE_FILE")
            val releaseStorePassword = signingValue("ONEMIND_STORE_PASSWORD")
            val releaseKeyAlias = signingValue("ONEMIND_KEY_ALIAS")
            val releaseKeyPassword = signingValue("ONEMIND_KEY_PASSWORD")
            val hasReleaseSigning =
                releaseStoreFile.isPresent &&
                    releaseStorePassword.isPresent &&
                    releaseKeyAlias.isPresent &&
                    releaseKeyPassword.isPresent

            val environment = oneMindEnvironment()

            extensions.configure<ApplicationExtension> {
                namespace = libs.version("projectApplicationId")

                defaultConfig {
                    applicationId = libs.version("projectApplicationId")
                    targetSdk = libs.intVersion("projectTargetSdkVersion")
                    versionCode = libs.intVersion("projectVersionCode")
                    versionName = libs.version("projectVersionName")
                }

                flavorDimensions += "environment"
                productFlavors {
                    create("dev") {
                        dimension = "environment"
                        applicationIdSuffix = ".dev"
                    }
                    create("prod") {
                        dimension = "environment"
                    }
                }

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                if (hasReleaseSigning) {
                    signingConfigs {
                        create("release") {
                            storeFile = File(releaseStoreFile.get())
                            storePassword = releaseStorePassword.get()
                            keyAlias = releaseKeyAlias.get()
                            keyPassword = releaseKeyPassword.get()
                        }
                    }
                }

                buildTypes {
                    getByName("debug") {
                        isDebuggable = true
                    }

                    getByName("release") {
                        isDebuggable = false
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        if (hasReleaseSigning) {
                            signingConfig = signingConfigs.getByName("release")
                        }
                    }
                }

                configureKotlinAndroid(this)
            }

            extensions.configure<ApplicationAndroidComponentsExtension> {
                ENVIRONMENTS.filter { it != environment }.forEach { other ->
                    beforeVariants(selector().withFlavor("environment" to other)) { it.enable = false }
                }
            }
        }
    }
}
