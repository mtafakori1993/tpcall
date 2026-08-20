package ir.tafakoritech.tpcall.util

/**
 * Pure validation rules for the three-digit extension field.
 */
object ExtensionValidator {

    fun isValidExtension(value: String): Boolean {
        return value.length == 3 && isDigitOnly(value) && value.toInt() in 100..999
    }

    fun isDigitOnly(value: String): Boolean {
        return value.isNotEmpty() && value.all { it in '0'..'9' }
    }

    /** Sanitizes user input to at most 3 ASCII digits. */
    fun sanitize(value: String): String = value.filter { it in '0'..'9' }.take(3)

    fun isPasswordValid(value: String): Boolean = value.length >= 6

    fun isNameValid(value: String): Boolean = value.isNotBlank()
}
