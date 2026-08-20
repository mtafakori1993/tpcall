package ir.tafakoritech.tpcall.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.call.isActive
import ir.tafakoritech.tpcall.data.api.ApiException
import ir.tafakoritech.tpcall.data.api.AuthGateway
import ir.tafakoritech.tpcall.data.model.CallHistoryItem
import ir.tafakoritech.tpcall.util.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallHistoryUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val calls: List<CallHistoryItem> = emptyList(),
    val error: String? = null,
    val hasMore: Boolean = false,
)

/**
 * Single source of truth for the History screen.
 *
 * Pagination appends results and never replaces the first page while loading
 * the next; concurrent duplicate requests are prevented by a single in-flight
 * guard. History becomes stale whenever a call ends; the next time the screen
 * is visible it refreshes automatically (or immediately if already visible).
 */
class CallHistoryViewModel(
    private val controller: CallController,
    private val authApi: AuthGateway,
    private val token: String,
    private val pageSize: Int = 30,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallHistoryUiState())
    val uiState: StateFlow<CallHistoryUiState> = _uiState.asStateFlow()

    private var loadedOnce = false
    private var stale = false
    private var visible = false
    private var requestInFlight = false
    private var prevCallActive = false

    init {
        viewModelScope.launch {
            controller.callState.collect { state ->
                val active = state.isActive
                if (prevCallActive && !active) {
                    stale = true
                    if (visible) silentRefresh()
                }
                prevCallActive = active
            }
        }
    }

    /** Called when the History screen becomes visible (composition + resume). */
    fun onScreenVisible() {
        visible = true
        when {
            !loadedOnce -> loadFirstPage()
            stale -> {
                stale = false
                silentRefresh()
            }
        }
    }

    /** Called when the History screen is hidden (dispose + pause). */
    fun onScreenHidden() {
        visible = false
    }

    /** Pull-to-refresh / retry: reload the first page. */
    fun refresh() {
        loadFirstPage(refreshing = true)
    }

    /** Loads the next page when approaching the bottom. Idempotent. */
    fun loadMore() {
        val s = _uiState.value
        if (requestInFlight || !s.hasMore || s.loading || s.refreshing) return
        requestInFlight = true
        viewModelScope.launch {
            runCatching { authApi.callHistory(token, pageSize, s.calls.size) }
                .onSuccess { resp ->
                    val newItems = resp.calls.mapNotNull { it.toDomain() }
                    _uiState.update {
                        it.copy(
                            calls = it.calls + newItems,
                            hasMore = resp.hasMore,
                            error = null,
                        )
                    }
                }
                .onFailure { /* keep what we have; a footer retry will fire again */ }
            requestInFlight = false
        }
    }

    private fun loadFirstPage(refreshing: Boolean = false) {
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
            runCatching { authApi.callHistory(token, pageSize, 0) }
                .onSuccess { resp ->
                    val items = resp.calls.mapNotNull { it.toDomain() }
                    _uiState.value = CallHistoryUiState(
                        loading = false,
                        refreshing = false,
                        calls = items,
                        error = null,
                        hasMore = resp.hasMore,
                    )
                    loadedOnce = true
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = errorMessageFor(e),
                        )
                    }
                }
            requestInFlight = false
        }
    }

    /** Refresh that must never clobber visible content if it fails. */
    private fun silentRefresh() {
        if (requestInFlight) return
        requestInFlight = true
        viewModelScope.launch {
            runCatching { authApi.callHistory(token, pageSize, 0) }
                .onSuccess { resp ->
                    val items = resp.calls.mapNotNull { it.toDomain() }
                    _uiState.update {
                        it.copy(
                            calls = items,
                            hasMore = resp.hasMore,
                            error = null,
                        )
                    }
                }
                .onFailure { /* keep visible history; next visit retries */ }
            requestInFlight = false
        }
    }

    private fun errorMessageFor(e: Throwable): String =
        (e as? ApiException)?.let { ex ->
            ErrorMapper.mapError(ex.code) ?: ex.userMessage
        } ?: "دریافت تاریخچه تماس‌ها ناموفق بود"

    companion object {
        fun factory(controller: CallController, authApi: AuthGateway, token: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { CallHistoryViewModel(controller, authApi, token) }
            }
    }
}
