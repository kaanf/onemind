import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.jooq.docker)
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

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.modulith.bom
                .get()
                .toString(),
        )
    }
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.micrometer.tracing.opentelemetry)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jooq.postgres.extensions)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.modulith.api)
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.db.scheduler.spring.boot.starter)
    // Compile scope only for PSQLException's structured fields (constraint name) in idempotency/infra.
    implementation(libs.postgresql)
    // Versions come from the Spring Boot BOM, so codegen matches the runtime jOOQ and Flyway.
    jooqCodegen(libs.postgresql)
    jooqCodegen(libs.jooq.codegen)
    jooqCodegen(libs.flyway.core)
    jooqCodegen(libs.flyway.database.postgresql)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.starter.jooq.test)
    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.starter.validation.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.micrometer.tracing.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xemit-jvm-type-annotations")
        // Parameter names in bytecode: the rule 5 gate (architecture/ProductRules) reads them.
        javaParameters = true
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

// Test-only HTTP client from the same contract, for the end-to-end smoke tests (account/AuthE2eSmokeTests):
// Spring HTTP interfaces (@HttpExchange) + models, compiled into the test source set only.
val openApiTestClientDir = layout.buildDirectory.dir("generated/openapi-test-client")

val openApiGenerateTestClient by tasks.registering(GenerateTask::class) {
    generatorName = "kotlin-spring"
    library = "spring-declarative-http-interface"
    inputSpec = layout.projectDirectory.file("../docs/api/openapi.yaml")
    outputDir = openApiTestClientDir
    apiPackage = "com.onemind.api.v1.client"
    modelPackage = "com.onemind.api.v1.client.model"
    generateApiTests = false
    generateModelTests = false
    generateApiDocumentation = false
    generateModelDocumentation = false
    configOptions =
        mapOf(
            "useTags" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "skipDefaultInterface" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "enumPropertyNaming" to "original",
            "gradleBuildFile" to "false",
        )
}

// Prints a new ES256 signing key for access tokens (ADR 0004): `./gradlew generateJwtKey`.
tasks.register<JavaExec>("generateJwtKey") {
    group = "application"
    description = "Prints a new P-256 JWK for JWT_SIGNING_KEY."
    // A dev tool, so it lives in the test sources and stays out of the boot jar.
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.onemind.backend.tools.GenerateJwtKeyKt"
}

// jOOQ classes from the migrated schema (ADR 0002): PostgreSQL container → Flyway → codegen.
// Packages com.onemind.db.<schema> sit outside com.onemind.backend; which module may use which
// package is checked by the module boundary test.
jooq {
    withContainer {
        image {
            name = "postgres:18"
        }
    }
}

tasks.generateJooqClasses {
    schemas = listOf("account", "pos", "reporting", "platform")
    basePackageName = "com.onemind.db"
    outputDirectory = layout.buildDirectory.dir("generated-src/jooq")
    migrationLocations.setFromFilesystem("src/main/resources/db/migration")
    // Same Flyway settings as application.yml: module schemas are created by the migrations.
    flywayProperties.putAll(mapOf("flyway.defaultSchema" to "platform", "flyway.schemas" to "platform"))
    usingJavaConfig {
        withName("org.jooq.codegen.KotlinGenerator")
        // platform tables of libraries (Flyway, Spring Modulith, db-scheduler) are accessed by them only.
        database.withExcludes("flyway_schema_history|event_publication|scheduled_tasks")
        database.withForcedTypes(
            // citext keeps its own binding so comparisons stay case-insensitive (not text = text).
            org.jooq.meta.jaxb
                .ForcedType()
                .withUserType("java.lang.String")
                .withBinding("org.jooq.postgres.extensions.bindings.CitextBinding")
                .withIncludeTypes(".*citext"),
            org.jooq.meta.jaxb
                .ForcedType()
                .withUserType("org.jooq.postgres.extensions.types.Inet")
                .withBinding("org.jooq.postgres.extensions.bindings.InetBinding")
                .withIncludeTypes(".*inet"),
            org.jooq.meta.jaxb
                .ForcedType()
                .withName("INSTANT")
                .withIncludeTypes("timestamptz|timestamp with time zone"),
            org.jooq.meta.jaxb
                .ForcedType()
                .withUserType("java.util.Currency")
                .withConverter("com.onemind.backend.common.money.infra.persistence.CurrencyConverter")
                .withIncludeExpression(".*\\.currency")
                .withIncludeTypes("bpchar|char|character"),
        )
    }
}

// Wiring the source dir to the task output makes every consumer (compile, ktlint) run after it.
sourceSets {
    main {
        kotlin.srcDir(tasks.openApiGenerate.map { openApiOutputDir.get().dir("src/main/kotlin") })
    }
    test {
        kotlin.srcDir(openApiGenerateTestClient.map { openApiTestClientDir.get().dir("src/main/kotlin") })
    }
}

ktlint {
    version = libs.versions.ktlint.cli
    filter {
        exclude { it.file.path.contains("/build/generated") }
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

// Dev/demo-only code and config (the mock POS, application-local.yml): on the bootRun and test
// classpaths, never in the boot jar (product rule 2). `developmentOnly` is Spring Boot's
// configuration that bootRun uses and bootJar leaves out.
val mock by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations["mockImplementation"].extendsFrom(configurations.implementation.get())
configurations["mockRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    developmentOnly(mock.output)
    testImplementation(mock.output)
}

// `./gradlew bootRun` without a profile runs as `local`.
tasks.bootRun {
    systemProperty("spring.profiles.default", "local")
}

val verifyBootJarExcludesMock by tasks.registering {
    description = "Fails if the boot jar contains dev/demo mock code or config."
    val jar = tasks.bootJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val found =
            ZipFile(jar.get().asFile).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .filterNot { it.endsWith("/") }
                    .filter { "/mock/" in it || it.endsWith("application-local.yml") }
                    .toList()
            }
        check(found.isEmpty()) { "The boot jar must not contain dev/demo code or config: $found" }
    }
}

tasks.check {
    dependsOn(verifyBootJarExcludesMock)
}
