package com.incidentreporter.field.core.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

class AppErrorTest {

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun toAppError_sortsFailuresByWhatTheAppDoes() {
        assertTrue(IOException("offline").toAppError() is AppError.Network)
        assertEquals(404, (httpError(404).toAppError() as AppError.Rejected).code)
        assertEquals(503, (httpError(503).toAppError() as AppError.Server).code)
        assertTrue(SerializationException("bad json").toAppError() is AppError.Serialization)
        val badTimestamp = runCatching { Instant.parse("yesterday") }.exceptionOrNull()!!
        assertTrue(badTimestamp.toAppError() is AppError.Serialization)

        val already = AppError.Rejected(422)
        assertSame(already, already.toAppError())
    }

    @Test
    fun apiCall_mapsFailures() = runTest {
        val error = runCatching { apiCall<Unit> { throw IOException("offline") } }.exceptionOrNull()
        assertTrue(error is AppError.Network)
    }

    @Test
    fun apiCall_rethrowsCancellationUntouched() = runTest {
        val error = runCatching { apiCall<Unit> { throw CancellationException("screen closed") } }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
