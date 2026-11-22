package com.onemind.auth.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailAddressTest {
    @Test
    fun acceptsUsualAddresses() {
        listOf("owner@yourbar.cz", " owner@yourbar.cz ", "a.b+bar@mail.example.co.uk").forEach {
            assertTrue(isPlausibleEmail(it), it)
        }
    }

    @Test
    fun rejectsTypos() {
        listOf("", "owner", "owner@", "@yourbar.cz", "owner@yourbar", "owner@yourbar.", "own er@bar.cz", "a@b@c.cz").forEach {
            assertFalse(isPlausibleEmail(it), it)
        }
    }
}
