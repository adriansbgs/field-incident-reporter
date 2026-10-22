package com.incidentreporter.field.core.result

sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable? = null) : AppError("Can't reach the server", cause)
    class Rejected(val code: Int) : AppError("Server rejected the change (HTTP $code)")
    class Server(val code: Int) : AppError("Server error (HTTP $code)")
    class Serialization(cause: Throwable? = null) : AppError("Unexpected server response", cause)
}
