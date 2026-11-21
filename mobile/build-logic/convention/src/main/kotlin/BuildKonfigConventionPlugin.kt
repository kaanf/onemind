import com.codingfeline.buildkonfig.gradle.BuildKonfigExtension
import com.onemind.convention.pathToPackageName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Fields and the `dev`/`prod` flavors are added with the variants roadmap item. */
class BuildKonfigConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.codingfeline.buildkonfig")

            extensions.configure<BuildKonfigExtension> {
                packageName.set(pathToPackageName())
                defaultConfigs { }
            }
        }
    }
}
