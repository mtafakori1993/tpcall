package ir.tafakoritech.tpcall.data.session

import android.content.Context
import ir.tafakoritech.tpcall.data.model.UserDto

data class Session(
    val token: String,
    val user: UserDto,
)

/** Storage abstraction so call-state logic can be unit-tested without Android. */
interface SessionStorage {
    fun save(token: String, user: UserDto)
    fun load(): Session?
    fun clear()
}

/**
 * Persists the authenticated session locally.
 *
 * Uses SharedPreferences (DataStore is preferred upstream, but the androidx
 * DataStore artifact is not obtainable in the offline build environment used
 * for this MVP). Only the token and basic user info are stored, never the
 * password.
 */
class SessionStore(context: Context) : SessionStorage {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(token: String, user: UserDto) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.userId)
            .putString(KEY_NAME, user.name)
            .putString(KEY_EXTENSION, user.extension)
            .apply()
    }

    override fun load(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val user = UserDto(
            name = prefs.getString(KEY_NAME, null) ?: "",
            extension = prefs.getString(KEY_EXTENSION, null) ?: "",
        )
        return Session(token = token, user = user)
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "tpcall_session"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_NAME = "name"
        const val KEY_EXTENSION = "extension"
    }
}
