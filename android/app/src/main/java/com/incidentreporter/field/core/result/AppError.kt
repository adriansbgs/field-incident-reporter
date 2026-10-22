package com.incidentreporter.field.core.result

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.format.DateTimeParseException

sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable? = null) : AppError("Can't reach the server", cause)
    class Rejected(val code: Int) : AppError("Server rejected the change (HTTP $code)")
    class Server(val code: Int) : AppError("Server error (HTTP $code)")
    class Serialization(cause: Throwable? = null) : AppError("Unexpected server response", cause)
}

/** Sorts a network-layer failure into an [AppError]. Anything unrecognised is a bug, so it is rethrown, not hidden. */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is IOException -> AppError.Network(this)
    is HttpException -> if (code() >= 500) AppError.Server(code()) else AppError.Rejected(code())
    is SerializationException, is DateTimeParseException, is IllegalArgumentException -> AppError.Serialization(this)
    else -> throw this
}

/** Runs one API call. Cancellation passes through untouched; every other failure becomes an [AppError]. */
suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toAppError()
    }
