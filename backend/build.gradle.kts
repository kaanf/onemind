plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.ktlint)
}

group = "com.onemind"
version = "0.0.1-SNAPSHOT"
description = "OneMind backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.starter.jooq.test)
    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.starter.validation.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xemit-jvm-type-annotations")
    }
}

// Controller interfaces and DTOs from the API contract (ADR 0001). Generated into build/, never committed.
// Packages sit outside com.onemind.backend so Spring Modulith does not treat them as a module.
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName = "kotlin-spring"
    inputSpec = layout.projectDirectory.file("../docs/api/openapi.yaml")
    outputDir = openApiOutputDir
    apiPackage = "com.onemind.api.v1.web"
    modelPackage = "com.onemind.api.v1.model"
    generateApiTests = false
    generateModelTests = false
    generateApiDocumentation = false
    generateModelDocumentation = false
    configOptions =
        mapOf(
            "interfaceOnly" to "true",
            "useTags" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "useBeanValidation" to "true",
            "skipDefaultInterface" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "enumPropertyNaming" to "original",
            "gradleBuildFile" to "false",
            "exceptionHandler" to "false",
            // Null handling stays with the global ObjectMapper.
            "generateJsonIncludeAnnotations" to "false",
            "generateJsonSetterNullsAnnotations" to "false",
        )
}

// Wiring the source dir to the task output makes every consumer (compile, ktlint) run after it.
sourceSets {
    main {
        kotlin.srcDir(tasks.openApiGenerate.map { openApiOutputDir.get().dir("src/main/kotlin") })
    }
}

ktlint {
    version = libs.versions.ktlint.cli
    filter {
        exclude { it.file.path.contains("/build/generated/") }
    }
}

configurations.matching { it.name.startsWith("ktlint") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(
                libs.versions.ktlint.kotlin
                    .get(),
            )
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
