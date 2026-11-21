import com.onemind.convention.libs
import com.onemind.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class CmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.onemind.convention.kmp.library")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
            }

            dependencies {
                "commonMainImplementation"(libs.library("jetbrains-compose-runtime"))
                "commonMainImplementation"(libs.library("jetbrains-compose-ui"))
                "commonMainImplementation"(libs.library("jetbrains-compose-foundation"))
                "commonMainImplementation"(libs.library("jetbrains-compose-material3"))
                "commonMainImplementation"(libs.library("jetbrains-compose-material-icons-core"))
                "commonMainImplementation"(libs.library("jetbrains-compose-resources"))
                "commonMainImplementation"(libs.library("jetbrains-compose-ui-tooling-preview"))
                "androidMainImplementation"(libs.library("jetbrains-compose-ui-tooling"))
            }
        }
    }
}
