package ir.tafakoritech.tpcall.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(
    val extension: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val name: String,
    val extension: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("token_type") val tokenType: String = "Bearer",
    val user: UserDto = UserDto(),
)

/**
 * Flexible user representation. `id` is kept as JsonElement because the backend
 * may use either integer or string identifiers; callers read it via [userId].
 */
@Serializable
data class UserDto(
    val id: JsonElement? = null,
    val name: String = "",
    val extension: String = "",
    val online: Boolean? = null,
) {
    val userId: String
        get() = id?.let { elem ->
            if (elem is kotlinx.serialization.json.JsonPrimitive) elem.content else elem.toString()
        } ?: ""
}

@Serializable
data class IceServerDto(
    val urls: List<String> = emptyList(),
)

@Serializable
data class IceConfigDto(
    @SerialName("iceServers") val iceServers: List<IceServerDto> = emptyList(),
)
