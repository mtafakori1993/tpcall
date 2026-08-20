package ir.tafakoritech.tpcall.data.contacts

/**
 * One internal corporate directory entry. This is the TP Call company address
 * book, not the user's personal Android address book — no Android Contacts
 * permissions are involved.
 *
 * Only safe presentation fields are stored; backend secrets (password,
 * last_login, token, device internals) are never mapped.
 */
data class CorporateContact(
    val id: String,
    val name: String,
    val extension: String,
    val online: Boolean,
)
