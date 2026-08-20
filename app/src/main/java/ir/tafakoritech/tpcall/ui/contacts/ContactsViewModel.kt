package ir.tafakoritech.tpcall.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.data.api.AuthGateway
import ir.tafakoritech.tpcall.data.contacts.CorporateContact
import ir.tafakoritech.tpcall.util.PersianSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val query: String = "",
    val contacts: List<CorporateContact> = emptyList(),
    val error: String? = null,
)

/**
 * Single source of truth for the corporate directory (مخاطبین شرکت).
 *
 * The directory is fetched from the backend once and filtered locally by name
 * or extension — no backend request per keystroke. Call buttons always route
 * through [CallController.startCall].
 */
class ContactsViewModel(
    private val controller: CallController,
    private val authApi: AuthGateway,
    private val token: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private var loadedOnce = false
    private var requestInFlight = false

    init {
        load(refreshing = false)
    }

    /** Fetches the directory (first load or explicit refresh). */
    fun refresh() {
        load(refreshing = true)
    }

    private fun load(refreshing: Boolean) {
        if (requestInFlight) return
        requestInFlight = true
        _uiState.update {
            it.copy(
                loading = !loadedOnce && !refreshing,
                refreshing = refreshing,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { authApi.directory(token) }
                .onSuccess { users ->
                    val contacts = users.mapNotNull { u ->
                        if (u.extension.isBlank()) return@mapNotNull null
                        CorporateContact(
                            id = u.userId.ifBlank { u.extension },
                            name = u.name.ifBlank { u.extension },
                            extension = u.extension,
                            online = u.online ?: false,
                        )
                    }
                    _uiState.value = ContactsUiState(
                        loading = false,
                        refreshing = false,
                        query = _uiState.value.query,
                        contacts = PersianSearch.sortByExtension(contacts),
                        error = null,
                    )
                    loadedOnce = true
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            refreshing = false,
                            error = "دریافت مخاطبین ناموفق بود",
                        )
                    }
                }
            requestInFlight = false
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Contacts matching the current query, sorted by extension. */
    fun filteredContacts(): List<CorporateContact> {
        val state = _uiState.value
        val query = state.query
        return if (query.isBlank()) {
            state.contacts
        } else {
            state.contacts.filter { PersianSearch.matches(it.name, it.extension, query) }
        }
    }

    /** True when the contact is the logged-in employee (self-call disallowed). */
    fun isSelf(contact: CorporateContact): Boolean =
        contact.extension == controller.myUser?.extension

    /** Routes through the single application-level call starter. */
    fun callContact(extension: String) {
        controller.startCall(extension)
    }

    companion object {
        fun factory(controller: CallController, authApi: AuthGateway, token: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ContactsViewModel(controller, authApi, token) }
            }
    }
}
