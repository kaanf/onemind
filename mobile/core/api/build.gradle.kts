import java.io.File

plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.openapi.generator)
}

// DTOs from the API contract (ADR 0001, ADR 0011): models only, generated into build/, never committed.
// The split spec is bundled with Redocly first: openapi-generator's parser does not resolve refs from
// one split schema file to another (Problem.errors would become List<String>).
val apiSpecDir = rootProject.layout.projectDirectory.dir("../docs/api")
val bundledSpec = layout.buildDirectory.file("openapi/openapi.bundled.yaml")
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

// GUI-launched IDEs on macOS can omit Homebrew from PATH, so npx is looked up in PATH, then
// /opt/homebrew/bin and /usr/local/bin, or in -Ponemind.nodeBinDir. The lookup runs when the task runs,
// not at configuration: builds that don't bundle the spec never need Node, and nothing here is read
// while configuring, so the configuration cache stays valid when PATH or Node's location changes.
val nodeBinDir = providers.gradleProperty("onemind.nodeBinDir")
val processPath = providers.environmentVariable("PATH").orElse("")
val npxName = if (System.getProperty("os.name").startsWith("Windows")) "npx.cmd" else "npx"

val bundleOpenApiSpec =
    tasks.register<Exec>("bundleOpenApiSpec") {
        description = "Bundles the split OpenAPI spec into one file (needs Node.js for npx)."
        inputs.dir(apiSpecDir).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(bundledSpec)
        val override = nodeBinDir
        val path = processPath
        val name = npxName
        doFirst {
            val candidates =
                override.orNull?.let { listOf(it) }
                    ?: (path.get().split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin"))
            val npx =
                candidates.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }
                    ?: throw GradleException(
                        "Cannot find npx in ${override.orNull ?: "PATH, /opt/homebrew/bin or /usr/local/bin"}. Install Node.js " +
                            "or set onemind.nodeBinDir (the directory with node and npx) in ~/.gradle/gradle.properties.",
                    )
            executable = npx.absolutePath
            // npx starts node through /usr/bin/env, so node's directory must be on the child's PATH too.
            environment("PATH", npx.parent + File.pathSeparator + path.get())
        }
        executable = name
        args(
            "--yes",
            "@redocly/cli@${libs.versions.redocly.cli.get()}",
            "bundle",
            apiSpecDir.file("openapi.yaml").asFile.path,
            "--output",
            bundledSpec.get().asFile.path,
        )
    }

openApiGenerate {
    generatorName = "kotlin"
    library = "multiplatform"
    inputSpec = bundledSpec
    outputDir = openApiOutputDir
    // Two templates of the kotlin generator (7.27.0) with one change in their multiplatform branch: string
    // enums get an UNKNOWN entry and a serializer that reads any value the app doesn't know as UNKNOWN,
    // so a value added to the API later doesn't fail the whole response. Re-copy them on a generator bump.
    templateDir = layout.projectDirectory.dir("templates")
    packageName = "com.onemind.core.api"
    modelPackage = "com.onemind.core.api.model"
    globalProperties =
        mapOf(
            "models" to "",
            "apis" to "false",
            "supportingFiles" to "false",
            "modelDocs" to "false",
            "modelTests" to "false",
        )
    // Free-form values (e.g. Problem.params) become JsonElement instead of Any, which kotlinx cannot serialize.
    typeMappings = mapOf("AnyType" to "kotlinx.serialization.json.JsonElement")
    importMappings = mapOf("kotlinx.serialization.json.JsonElement" to "kotlinx.serialization.json.JsonElement")
    configOptions =
        mapOf(
            "sourceFolder" to "src/commonMain/kotlin",
            "enumPropertyNaming" to "original",
            "dateLibrary" to "kotlinx-datetime",
        )
}

tasks.openApiGenerate {
    dependsOn(bundleOpenApiSpec)
}

kotlin {
    sourceSets {
        commonMain {
            // Wiring the source dir to the task output makes every consumer (compile, ktlint) run after it.
            kotlin.srcDir(tasks.openApiGenerate.map { openApiOutputDir.get().dir("src/commonMain/kotlin") })
            dependencies {
                api(libs.kotlinx.serialization.json)
                // `format: date` becomes kotlinx.datetime.LocalDate (dateLibrary below); it ships its own
                // kotlinx serializer, and the models expose it, so consumers need it too.
                api(libs.kotlinx.datetime)
            }
        }
    }
}

// AGP's release-only art profile task looks for `baselineProfiles` next to each Kotlin source dir,
// so it reads the generator output too; declare the order (`assembleRelease` fails without it).
tasks.matching { it.name == "prepareAndroidMainArtProfile" }.configureEach {
    dependsOn(tasks.openApiGenerate)
}
