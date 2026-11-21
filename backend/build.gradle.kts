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
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jooq.postgres.extensions)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.modulith.api)
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.db.scheduler.spring.boot.starter)
    runtimeOnly(libs.postgresql)
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
    schemas = listOf("account", "pos", "reporting")
    basePackageName = "com.onemind.db"
    outputDirectory = layout.buildDirectory.dir("generated-src/jooq")
    migrationLocations.setFromFilesystem("src/main/resources/db/migration")
    // Same Flyway settings as application.yml: module schemas are created by the migrations.
    flywayProperties.putAll(mapOf("flyway.defaultSchema" to "platform", "flyway.schemas" to "platform"))
    usingJavaConfig {
        withName("org.jooq.codegen.KotlinGenerator")
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
                .withConverter("com.onemind.backend.common.money.CurrencyConverter")
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
