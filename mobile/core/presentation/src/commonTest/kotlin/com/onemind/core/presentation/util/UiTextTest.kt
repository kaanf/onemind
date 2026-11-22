package com.onemind.core.presentation.util

import com.onemind.core.presentation.generated.resources.Res
import com.onemind.core.presentation.generated.resources.error_unknown
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UiTextTest {
    @Test
    fun dynamicTextIsReturnedAsIs() =
        runTest {
            assertEquals("Server says hi", UiText.Dynamic("Server says hi").asStringAsync())
        }

    @Test
    fun resourcesCompareByIdAndArgs() {
        assertEquals(UiText.Resource(Res.string.error_unknown, listOf(3)), UiText.Resource(Res.string.error_unknown, listOf(3)))
        assertNotEquals(UiText.Resource(Res.string.error_unknown, listOf(3)), UiText.Resource(Res.string.error_unknown, listOf(4)))
    }
}
