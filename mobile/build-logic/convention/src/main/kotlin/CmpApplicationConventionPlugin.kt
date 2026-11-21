import com.onemind.convention.configureIosFramework
import org.gradle.api.Plugin
import org.gradle.api.Project

/** `composeApp`: a CMP library that also exports the single iOS framework. */
class CmpApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.onemind.convention.cmp.library")
            }

            configureIosFramework()
        }
    }
}
