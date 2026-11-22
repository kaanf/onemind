package com.onemind.core.presentation.util

import com.onemind.core.domain.util.DataError
import com.onemind.core.presentation.generated.resources.Res
import com.onemind.core.presentation.generated.resources.error_needs_app_update
import com.onemind.core.presentation.generated.resources.error_no_internet
import com.onemind.core.presentation.generated.resources.error_rate_limited
import com.onemind.core.presentation.generated.resources.error_timeout
import com.onemind.core.presentation.generated.resources.error_unknown
import kotlin.test.Test
import kotlin.test.assertEquals

class DataErrorTextTest {
    @Test
    fun networkErrorsHaveLocalisedText() {
        assertEquals(UiText.Resource(Res.string.error_no_internet), DataError.Network.NO_INTERNET.toUiText())
        assertEquals(UiText.Resource(Res.string.error_timeout), DataError.Network.TIMEOUT.toUiText())
        assertEquals(UiText.Resource(Res.string.error_unknown), DataError.Network.SERIALIZATION.toUiText())
        assertEquals(UiText.Resource(Res.string.error_needs_app_update), DataError.Network.NEEDS_APP_UPDATE.toUiText())
        assertEquals(UiText.Resource(Res.string.error_unknown), DataError.Network.UNKNOWN.toUiText())
    }

    @Test
    fun sharedCodesAreLocalised() {
        assertEquals(UiText.Resource(Res.string.error_rate_limited), DataError.Api(429, "RATE_LIMITED", "Slow down").toUiText())
    }

    @Test
    fun unknownCodesShowTheServerDetail() {
        assertEquals(UiText.Dynamic("Venue is archived"), DataError.Api(422, "VENUE_ARCHIVED", "Venue is archived").toUiText())
    }

    @Test
    fun unknownCodeWithoutDetailIsGeneric() {
        assertEquals(UiText.Resource(Res.string.error_unknown), DataError.Api(502, "UNKNOWN", null).toUiText())
        assertEquals(UiText.Resource(Res.string.error_unknown), DataError.Api(500, "X", "  ").toUiText())
    }
}
