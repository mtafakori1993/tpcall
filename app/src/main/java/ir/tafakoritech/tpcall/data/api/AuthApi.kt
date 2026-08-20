package ir.tafakoritech.tpcall.data.api

import ir.tafakoritech.tpcall.config.AppConfig
import ir.tafakoritech.tpcall.data.model.AuthResponse
import ir.tafakoritech.tpcall.data.model.CallHistoryResponseDto
import ir.tafakoritech.tpcall.data.model.IceConfigDto
import ir.tafakoritech.tpcall.data.model.LoginRequest
import ir.tafakoritech.tpcall.data.model.RegisterRequest
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.util.ErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * REST gateway abstraction so call-state logic can be unit-tested without
 * performing real HTTP requests.
 */
interface AuthGateway {
    suspend fun login(extension: String, password: String): AuthResponse
    suspend fun register(name: String, extension: String, password: String): AuthResponse
    suspend fun me(token: String): UserDto
    suspend fun user(extension: String, token: String): UserDto
    suspend fun directory(token: String): List<UserDto>
    suspend fun webRtcConfig(token: String): IceConfigDto
    suspend fun callHistory(token: String, limit: Int = 30, offset: Int = 0): CallHistoryResponseDto
}

/**
 * REST client for authentication, user lookup and WebRTC config.
 * Protected endpoints receive the JWT via the Authorization header.
 */
class AuthApi(private val client: OkHttpClient) : AuthGateway {

    override suspend fun login(extension: String, password: String): AuthResponse =
        post(
            path = "/api/v1/auth/login",
            body = ApiClient.json.encodeToString(LoginRequest(extension, password)),
        )

    override suspend fun register(name: String, extension: String, password: String): AuthResponse =
        post(
            path = "/api/v1/auth/register",
            body = ApiClient.json.encodeToString(RegisterRequest(name, extension, password)),
        )

    override suspend fun me(token: String): UserDto {
        val response = execute(path = "/api/v1/me", token = token, method = "GET")
        return parse(response, "user") ?: UserDto()
    }

    override suspend fun user(extension: String, token: String): UserDto {
        val response = execute(path = "/api/v1/users/$extension", token = token, method = "GET")
        return parse(response, "user") ?: UserDto()
    }

    /**
     * Corporate directory: GET /api/v1/users returns a JSON array of directory
     * entries ({name, extension, online}). This is the internal TP Call address
     * book — no Android Contacts permission is required.
     */
    override suspend fun directory(token: String): List<UserDto> {
        val response = execute(path = "/api/v1/users", token = token, method = "GET")
        return runCatching {
            ApiClient.json.decodeFromString<List<UserDto>>(response)
        }.getOrDefault(emptyList())
    }

    override suspend fun webRtcConfig(token: String): IceConfigDto {
        val response = execute(path = "/api/v1/webrtc/config", token = token, method = "GET")
        return parse(response, "ice_config") ?: IceConfigDto()
    }

    override suspend fun callHistory(token: String, limit: Int, offset: Int): CallHistoryResponseDto {
        val response = execute(
            path = "/api/v1/calls",
            token = token,
            method = "GET",
            query = mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        )
        return parse(response, "call_history") ?: throw ApiException(
            kind = ApiErrorKind.SERVER,
            code = null,
            userMessage = "پاسخ نامعتبر از سرور",
        )
    }

    private suspend fun post(path: String, body: String): AuthResponse {
        val response = execute(path = path, method = "POST", body = body)
        return parse(response, "auth") ?: throw ApiException(
            kind = ApiErrorKind.SERVER,
            code = null,
            userMessage = "پاسخ نامعتبر از سرور",
        )
    }

    private suspend fun execute(
        path: String,
        method: String,
        token: String? = null,
        body: String? = null,
        query: Map<String, String>? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val urlBuilder = AppConfig.BASE_URL.toHttpUrl().newBuilder()
                .encodedPath(path)
            query?.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
            val url = urlBuilder.build()
            val requestBuilder = Request.Builder()
                .url(url)
                .method(method, body?.toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        responseBody
                    } else {
                        // Prefer the backend's snake_case error code; fall back to
                        // HTTP-status heuristics when the body carries no code.
                        val code = ApiErrorParser.codeFromBody(responseBody)
                        val kind = when (response.code) {
                            401 -> ApiErrorKind.UNAUTHORIZED
                            409 -> ApiErrorKind.CONFLICT
                            404 -> ApiErrorKind.NOT_FOUND
                            422 -> ApiErrorKind.VALIDATION
                            else -> ApiErrorKind.SERVER
                        }
                        val userMessage = ErrorMapper.mapError(code) ?: when (kind) {
                            ApiErrorKind.UNAUTHORIZED -> "داخلی یا رمز عبور اشتباه است"
                            ApiErrorKind.CONFLICT -> "این داخلی قبلاً ثبت شده است"
                            ApiErrorKind.NOT_FOUND -> "داخلی موردنظر وجود ندارد"
                            ApiErrorKind.VALIDATION -> "اطلاعات وارد شده نامعتبر است"
                            else -> "ارتباط با سرور برقرار نیست"
                        }
                        throw ApiException(kind, code, userMessage)
                    }
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException(ApiErrorKind.NETWORK, null, "ارتباط با سرور برقرار نیست")
            }
        }

    private fun <T> parse(raw: String, context: String): T? {
        val serializer = when (context) {
            "user" -> UserDto.serializer()
            "auth" -> AuthResponse.serializer()
            "ice_config" -> IceConfigDto.serializer()
            "call_history" -> CallHistoryResponseDto.serializer()
            else -> return null
        }
        @Suppress("UNCHECKED_CAST")
        return ApiClient.json.decodeFromString(serializer as kotlinx.serialization.KSerializer<T>, raw)
    }
}
