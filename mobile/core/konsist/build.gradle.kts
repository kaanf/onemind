// Guard-only module: Konsist tests over the mobile sources, no production code. Run with
// `./gradlew :core:konsist:test` (CI runs it in the mobile job).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.convention.ktlint)
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    // The generated API models are scanned too (money rule), so they must exist first.
    dependsOn(":core:api:openApiGenerate")
    systemProperty("onemind.mobileDir", rootDir.absolutePath)
    // Sources are the test input: rerun when any of them changes.
    inputs.files(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "core/konsist/**")
        },
    )
    inputs.dir(rootProject.layout.projectDirectory.dir("core/api/build/generated/openapi"))
}
