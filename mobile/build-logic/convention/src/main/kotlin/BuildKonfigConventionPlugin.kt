import com.codingfeline.buildkonfig.gradle.BuildKonfigExtension
import com.onemind.convention.oneMindEnvironment
import com.onemind.convention.pathToPackageName
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra

/**
 * BuildKonfig with the module's package. The flavor is `onemind.environment` (`dev`/`prod`); modules
 * declare their fields with `defaultConfigs` (dev) and `defaultConfigs("prod")`.
 */
class BuildKonfigConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val environment = oneMindEnvironment()
            val explicitFlavor = providers.gradleProperty("buildkonfig.flavor").orNull
            if (explicitFlavor != null && explicitFlavor != environment) {
                throw GradleException(
                    "Set -Ponemind.environment instead of buildkonfig.flavor ('$explicitFlavor' vs '$environment')",
                )
            }
            // BuildKonfig falls back to this project property when no flavor is passed on the command line.
            extra["buildkonfig.flavor"] = environment

            pluginManager.apply("com.codingfeline.buildkonfig")

            extensions.configure<BuildKonfigExtension> {
                packageName.set(pathToPackageName())
            }
        }
    }
}
