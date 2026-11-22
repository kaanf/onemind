import com.codingfeline.buildkonfig.gradle.BuildKonfigExtension
import com.onemind.convention.ENVIRONMENTS
import com.onemind.convention.oneMindEnvironment
import com.onemind.convention.pathToPackageName
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByType

/**
 * BuildKonfig with the module's package. The flavor is `onemind.environment` (`dev`/`prod`).
 *
 * Modules write the dev values in the unflavored `defaultConfigs` and override **every** field in
 * `defaultConfigs("prod")`; per-target values go in flavored `targetConfigs("dev")`/`("prod")` only.
 * BuildKonfig silently falls back to the unflavored (dev) value for a field a flavor doesn't set, and
 * unflavored target configs override flavored defaults, so both are build errors here.
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

            afterEvaluate { checkEveryEnvironmentSetsEveryField(extensions.getByType()) }
        }
    }

    private fun Project.checkEveryEnvironmentSetsEveryField(buildKonfig: BuildKonfigExtension) {
        val declared = buildKonfig.defaultConfigs[UNFLAVORED]?.fieldSpecs?.keys.orEmpty()
        ENVIRONMENTS.filter { it != "dev" }.forEach { environment ->
            val missing = declared - buildKonfig.defaultConfigs[environment]?.fieldSpecs?.keys.orEmpty()
            if (missing.isNotEmpty()) {
                throw GradleException("$path: defaultConfigs(\"$environment\") must set $missing (else the dev value ships)")
            }
        }
        val unflavoredTargets = buildKonfig.targetConfigs[UNFLAVORED]?.names.orEmpty()
        if (unflavoredTargets.isNotEmpty()) {
            throw GradleException("$path: use targetConfigs(\"dev\")/(\"prod\"), not unflavored targetConfigs $unflavoredTargets")
        }
    }

    private companion object {
        /** BuildKonfig's key for the unflavored config (`DEFAULT_FLAVOR`). */
        const val UNFLAVORED = ""
    }
}
