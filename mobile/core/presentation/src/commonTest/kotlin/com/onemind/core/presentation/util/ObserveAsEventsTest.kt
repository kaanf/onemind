package com.onemind.core.presentation.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAsEventsTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun deliversOnlyWhileStartedAndKeepsEventsSentWhileStopped() =
        runTest(dispatcher) {
            val owner = TestOwner()
            val events = Channel<String>(Channel.BUFFERED)
            val received = mutableListOf<String>()
            val job = launch { owner.lifecycle.collectWhileStarted(events.receiveAsFlow()) { received += it } }

            owner.registry.currentState = Lifecycle.State.CREATED
            events.send("while created")
            assertEquals(emptyList(), received)

            owner.registry.currentState = Lifecycle.State.STARTED
            assertEquals(listOf("while created"), received)

            events.send("while started")
            assertEquals(listOf("while created", "while started"), received)

            owner.registry.currentState = Lifecycle.State.CREATED
            events.send("after stop")
            assertEquals(listOf("while created", "while started"), received)

            owner.registry.currentState = Lifecycle.State.RESUMED
            assertEquals(listOf("while created", "while started", "after stop"), received)
            job.cancel()
        }
}
