package ir.tafakoritech.tpcall.util

/**
 * Maps backend error codes / signaling codes to friendly Persian messages.
 * Returns null when no known mapping exists (caller decides fallback text).
 */
object ErrorMapper {

    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val EXTENSION_ALREADY_EXISTS = "extension_already_exists"
    const val USER_NOT_FOUND = "user_not_found"
    const val CANNOT_CALL_SELF = "cannot_call_self"
    const val INVALID_CALL_STATE = "invalid_call_state"
    const val SESSION_REPLACED = "session_replaced"

    fun mapError(code: String?): String? = when (code) {
        INVALID_CREDENTIALS -> "داخلی یا رمز عبور اشتباه است"
        EXTENSION_ALREADY_EXISTS -> "این داخلی قبلاً ثبت شده است"
        USER_NOT_FOUND -> "داخلی موردنظر وجود ندارد"
        CANNOT_CALL_SELF -> "نمی‌توانید با داخلی خودتان تماس بگیرید"
        INVALID_CALL_STATE -> "وضعیت تماس تغییر کرده است"
        SESSION_REPLACED -> "این داخلی در دستگاه دیگری وارد شده است"
        else -> null
    }

    fun callEndedMessage(reason: String?): String? = when (reason) {
        "no_answer" -> "پاسخ داده نشد"
        "peer_disconnected" -> "ارتباط قطع شد"
        else -> null
    }
}
