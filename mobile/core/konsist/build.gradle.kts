// Guard-only module: Konsist tests over the mobile sources, no production code. Run with
// `./gradlew :core:konsist:test` (CI runs it in the mobile job).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.convention.ktlint)
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    // The generated API models are scanned too (money rule), so they must exist first.
    dependsOn(":core:api:openApiGenerate")
    systemProperty("onemind.mobileDir", rootDir.absolutePath)
    // Money words are shared with the backend; a change to the list reruns the guards.
    val moneyWords = rootDir.resolve("../docs/api/lint/money-words.json").canonicalFile
    systemProperty("onemind.moneyWords", moneyWords.absolutePath)
    inputs.file(moneyWords).withPathSensitivity(PathSensitivity.RELATIVE)
    // Sources are the test input: rerun when any of them changes.
    inputs.files(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "core/konsist/**")
        },
    )
    inputs.dir(rootProject.layout.projectDirectory.dir("core/api/build/generated/openapi"))
    inputs.dir("src/test/fixtures")
}
