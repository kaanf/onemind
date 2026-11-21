import com.onemind.convention.libs
import com.onemind.convention.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Same ktlint setup as the backend (`backend/build.gradle.kts`). */
class KtlintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<KtlintExtension> {
                version.set(libs.version("ktlint-cli"))
                filter {
                    exclude { it.file.path.contains("/build/") }
                }
            }

            // ktlint cannot load the project's Kotlin compiler; its own configurations use an older one.
            val ktlintKotlin = libs.version("ktlint-kotlin")
            configurations.matching { it.name.startsWith("ktlint") }.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") {
                        useVersion(ktlintKotlin)
                    }
                }
            }
        }
    }
}
