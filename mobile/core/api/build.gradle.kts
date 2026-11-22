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

val bundleOpenApiSpec =
    tasks.register<Exec>("bundleOpenApiSpec") {
        description = "Bundles the split OpenAPI spec into one file (needs Node.js for npx)."
        inputs.dir(apiSpecDir).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(bundledSpec)
        commandLine(
            "npx",
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
            }
        }
    }
}

// AGP's release-only art profile task looks for `baselineProfiles` next to each Kotlin source dir,
// so it reads the generator output too; declare the order (`assembleRelease` fails without it).
tasks.matching { it.name == "prepareAndroidMainArtProfile" }.configureEach {
    dependsOn(tasks.openApiGenerate)
}
