package ir.tafakoritech.tpcall.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun build(shared: okhttp3.OkHttpClient): okhttp3.OkHttpClient = shared
}

enum class ApiErrorKind {
    NETWORK,
    UNAUTHORIZED,
    CONFLICT,
    NOT_FOUND,
    VALIDATION,
    SERVER,
}

class ApiException(
    val kind: ApiErrorKind,
    val code: String?,
    val userMessage: String,
) : Exception(userMessage)

/**
 * Extracts a snake_case error code from a backend error body, trying several
 * common field names. Never throws.
 */
object ApiErrorParser {

    fun codeFromBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val obj = ApiClient.json.parseToJsonElement(body).jsonObject
            listOf("code", "error", "error_code", "message")
                .asSequence()
                .mapNotNull { obj[it] }
                .firstOrNull()
                ?.let { elem ->
                    if (elem is kotlinx.serialization.json.JsonPrimitive) elem.content else elem.toString()
                }
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
