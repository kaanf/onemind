package com.onemind.backend.pos

import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.client.PosProviderConfig
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The mock POS is dev/demo only (product rule 2): never in prod, never as packaged data. */
class PosProviderProfileTests {
    private val runner =
        ApplicationContextRunner()
            .withBean(Clock::class.java, { Clock.fixed(Instant.parse("2026-11-20T21:00:00Z"), ZoneOffset.UTC) })
            .withUserConfiguration(PosProviderConfig::class.java, MockPosProvider::class.java)

    private fun ApplicationContextRunner.inProd() = withInitializer { it.environment.setActiveProfiles("prod") }

    @Test
    fun `outside prod the mock is the provider when configured`() {
        runner.withPropertyValues("onemind.pos.provider=mock").run { context ->
            assertTrue(context.getBeansOfType(PosProvider::class.java).values.single() is MockPosProvider)
        }
    }

    @Test
    fun `a prod start with the mock fails`() {
        runner.inProd().withPropertyValues("onemind.pos.provider=mock").run { context ->
            val failure = assertNotNull(context.startupFailure)
            assertTrue(
                generateSequence(failure) { it.cause }.any { "not allowed in prod" in it.message.orEmpty() },
                failure.toString(),
            )
        }
    }

    @Test
    fun `a prod context never has the mock bean`() {
        runner.inProd().withPropertyValues("onemind.pos.provider=storyous").run { context ->
            assertEquals(null, context.startupFailure)
            assertTrue(context.getBeansOfType(MockPosProvider::class.java).isEmpty())
        }
    }

    @Test
    fun `no POS fixture files are packaged`() {
        val resources = File("src/main/resources")
        assertTrue(resources.isDirectory, "run from backend/")
        val suspicious =
            resources
                .walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(resources).path.lowercase() }
                .filter { path -> listOf("mock", "fixture", "demo", "pos/", "storyous").any { it in path } }
                .toList()
        assertEquals(emptyList(), suspicious)
    }
}
