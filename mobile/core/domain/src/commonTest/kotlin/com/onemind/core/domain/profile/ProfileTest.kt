package com.onemind.core.domain.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileTest {
    @Test
    fun initialsAreTheFirstAndLastWordsFirstLetters() {
        assertEquals("KF", Profile("Kaan Fırat").initials)
        assertEquals("AD", Profile("anna maria  dvořák").initials)
        assertEquals("K", Profile("Kaan").initials)
    }

    @Test
    fun noNameMeansNoInitials() {
        assertNull(Profile(null).initials)
        assertNull(Profile("   ").initials)
    }
}
