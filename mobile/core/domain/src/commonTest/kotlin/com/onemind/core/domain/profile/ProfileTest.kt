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
    fun aCharacterOutsideTheBmpIsNotCutInHalf() {
        assertEquals("\uD83C\uDF7AK", Profile("\uD83C\uDF7A Kaan").initials)
        assertEquals("\uD835\uDC00", Profile("\uD835\uDC00lpha").initials)
    }

    @Test
    fun noNameMeansNoInitials() {
        assertNull(Profile(null).initials)
        assertNull(Profile("   ").initials)
    }
}
