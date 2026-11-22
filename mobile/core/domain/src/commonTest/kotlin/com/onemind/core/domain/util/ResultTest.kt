package com.onemind.core.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ResultTest {
    private val success: Result<Int, DataError> = Result.Success(2)
    private val failure: Result<Int, DataError> = Result.Failure(DataError.Network.TIMEOUT)

    @Test
    fun mapTransformsOnlySuccess() {
        assertEquals(Result.Success(4), success.map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun asEmptyKeepsTheError() {
        assertEquals(Result.Success(Unit), success.asEmpty())
        assertEquals(Result.Failure(DataError.Network.TIMEOUT), failure.asEmpty())
    }

    @Test
    fun callbacksRunOnTheirSideOnly() {
        val seen = mutableListOf<String>()
        success.onSuccess { seen += "success $it" }.onFailure { seen += "failure $it" }
        failure.onSuccess { seen += "success $it" }.onFailure { seen += "failure $it" }
        assertEquals(listOf("success 2", "failure TIMEOUT"), seen)
    }

    @Test
    fun apiErrorsCompareByValue() {
        assertEquals(
            DataError.Api(422, "EMAIL_CODE_INVALID", params = mapOf("attemptsLeft" to "2")),
            DataError.Api(
                422,
                "EMAIL_CODE_INVALID",
                params =
                    mapOf("attemptsLeft" to "2"),
            ),
        )
    }
}
