import com.onemind.convention.libs
import com.onemind.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Feature presentation modules: Compose + ViewModel + Navigation + Koin. */
class CmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.onemind.convention.cmp.library")
            }

            dependencies {
                "commonMainImplementation"(project(":core:presentation"))
                "commonMainImplementation"(project(":core:designsystem"))

                "commonMainImplementation"(libs.library("koin-compose"))
                "commonMainImplementation"(libs.library("koin-compose-viewmodel"))

                "commonMainImplementation"(libs.library("jetbrains-compose-backhandler"))
                "commonMainImplementation"(libs.library("jetbrains-lifecycle-viewmodel"))
                "commonMainImplementation"(libs.library("jetbrains-lifecycle-viewmodel-compose"))
                "commonMainImplementation"(libs.library("jetbrains-lifecycle-viewmodel-savedstate"))
                "commonMainImplementation"(libs.library("jetbrains-lifecycle-runtime-compose"))
                "commonMainImplementation"(libs.library("jetbrains-savedstate"))
                "commonMainImplementation"(libs.library("jetbrains-bundle"))
                "commonMainImplementation"(libs.library("jetbrains-compose-navigation"))

                "commonTestImplementation"(libs.library("kotlinx-coroutines-test"))
                "commonTestImplementation"(libs.library("turbine"))
            }
        }
    }
}
