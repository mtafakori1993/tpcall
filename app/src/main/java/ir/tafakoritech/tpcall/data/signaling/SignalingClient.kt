package ir.tafakoritech.tpcall.data.signaling

import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import ir.tafakoritech.tpcall.call.SignalingGateway
import ir.tafakoritech.tpcall.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

enum class SignalingStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, AUTH_FAILED }

/**
 * Manages exactly one authenticated WebSocket to the signaling server per app
 * session, including a single, race-safe reconnect worker with exponential
 * backoff + jitter and Android network awareness.
 *
 * Reconnect ownership: this client owns reconnection. Activities, services and
 * the controller only start/stop it; they never open sockets themselves.
 *
 * Guarantees:
 *  - never more than one active socket
 *  - never more than one reconnect job (mutex + job ownership)
 *  - no reconnect after logout / auth failure / session_replaced
 *  - no tight retry loop while the network is unavailable
 *  - immediate reconnect when the network comes back
 *  - every reconnect re-authenticates with the current JWT
 *
 * The token is only ever placed in the Authorization header of the WebSocket
 * handshake; it is never logged or surfaced in the UI.
 */
class SignalingClient(
    private val connectivityManager: ConnectivityManager?,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val jitterRandom: Random = Random.Default,
    private val socketFactory: (Request, WebSocketListener) -> WebSocket = { request, wsListener ->
        client.newWebSocket(request, wsListener)
    },
) : SignalingGateway {

    private val _messages = MutableSharedFlow<WsEnvelope>(extraBufferCapacity = 128)
    override val messages: SharedFlow<WsEnvelope> = _messages.asSharedFlow()

    private val _status = MutableStateFlow(SignalingStatus.DISCONNECTED)
    override val status: StateFlow<SignalingStatus> = _status.asStateFlow()

    private val _connectionState = MutableStateFlow<SignalingConnectionState>(
        SignalingConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    private var socket: WebSocket? = null

    // Single reconnect worker ownership.
    private var reconnectJob: Job? = null
    private var token: String? = null

    // Atomic flags for race-safety across onFailure / onClosed / network callback.
    private val intentionalDisconnect = AtomicBoolean(true)
    private val authFailed = AtomicBoolean(false)
    private val reconnectAttempt = AtomicInteger(0)

    // Network awareness (volatile; written by the binder-thread callback).
    @Volatile
    private var networkAvailable = true

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val networkCallbackRegistered = AtomicBoolean(false)

    private fun setState(state: SignalingConnectionState) {
        _connectionState.value = state
        _status.value = when (state) {
            SignalingConnectionState.Connected -> SignalingStatus.CONNECTED
            SignalingConnectionState.Connecting -> SignalingStatus.CONNECTING
            is SignalingConnectionState.Reconnecting -> SignalingStatus.RECONNECTING
            SignalingConnectionState.Disconnected -> SignalingStatus.DISCONNECTED
        }
    }

    @Synchronized
    override fun start(token: String) {
        this.token = token
        intentionalDisconnect.set(false)
        authFailed.set(false)
        reconnectAttempt.set(0)
        registerNetworkCallback()
        if (socket != null || reconnectJob?.isActive == true) return
        connect()
    }

    @Synchronized
    override fun stop() {
        Log.i(TAG, "signaling_stopped")
        intentionalDisconnect.set(true)
        token = null
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, null)
        socket = null
        unregisterNetworkCallback()
        setState(SignalingConnectionState.Disconnected)
    }

    override fun sendMessage(type: String, requestId: String?, payload: JsonObject) {
        val envelope = WsEnvelope(type = type, requestId = requestId, payload = payload)
        val raw = runCatching {
            ir.tafakoritech.tpcall.data.api.ApiClient.json.encodeToString(envelope)
        }.getOrNull() ?: return
        val sent = socket?.send(raw) ?: false
        if (!sent) {
            Log.w(TAG, "signaling_send_failed type=$type")
        }
    }

    // ---------------------------------------------------------------------
    // Reconnect control
    // ---------------------------------------------------------------------

    private fun connect() {
        val t = token ?: return
        if (intentionalDisconnect.get()) return
        if (authFailed.get()) return
        Log.i(TAG, "signaling_connect_started")
        setState(SignalingConnectionState.Connecting)
        val request = Request.Builder()
            .url(AppConfig.WS_URL)
            .header("Authorization", "Bearer $t")
            .build()
        val ws = socketFactory(request, listener)
        socket = ws
    }

    /**
     * Schedules the single reconnect worker with exponential backoff + jitter.
     * Guarded so that simultaneous failure callbacks only ever produce one job.
     */
    private fun scheduleReconnect(reason: String) {
        if (intentionalDisconnect.get() || authFailed.get()) return
        if (reconnectJob?.isActive == true) {
            Log.i(TAG, "signaling_reconnect_already_scheduled reason=$reason")
            return
        }
        if (!networkAvailable) {
            Log.i(TAG, "signaling_reconnect_waiting_network reason=$reason")
            return
        }
        val attempt = reconnectAttempt.incrementAndGet()
        val delayMs = SignalingBackoff.jitteredDelayMs(attempt, jitterRandom)
        Log.i(
            TAG,
            "signaling_reconnect_scheduled attempt=$attempt delay_ms=$delayMs reason=$reason",
        )
        setState(SignalingConnectionState.Reconnecting(attempt))
        reconnectJob = scope.launch {
            delay(delayMs)
            reconnectJob = null
            if (intentionalDisconnect.get() || authFailed.get()) return@launch
            if (socket != null) return@launch
            Log.i(TAG, "signaling_reconnect_attempt attempt=$attempt")
            connect()
        }
    }

    /** Called when Android reports the network is usable again. */
    fun onNetworkAvailable() {
        if (intentionalDisconnect.get() || authFailed.get()) return
        networkAvailable = true
        Log.i(TAG, "network_available")
        if (socket == null) {
            // Immediate retry, still under single-worker control so we never
            // open two sockets and never fight an in-flight backoff.
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt.set(0)
            connect()
        }
    }

    private fun markAuthFailed() {
        authFailed.set(true)
        intentionalDisconnect.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, null)
        socket = null
        unregisterNetworkCallback()
        setState(SignalingConnectionState.Disconnected)
        _status.value = SignalingStatus.AUTH_FAILED
        Log.w(TAG, "signaling_reconnect_cancelled reason=auth_failed")
    }

    // ---------------------------------------------------------------------
    // Network awareness
    // ---------------------------------------------------------------------

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        if (networkCallbackRegistered.get()) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "network_available")
                networkAvailable = true
                scope.launch { onNetworkAvailable() }
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "network_lost")
                networkAvailable = false
                // A scheduled backoff must not fire while offline; it will be
                // re-triggered immediately when the network returns.
                scope.launch {
                    if (!intentionalDisconnect.get() && !authFailed.get()) {
                        reconnectJob?.cancel()
                        reconnectJob = null
                    }
                }
            }

            override fun onUnavailable() {
                Log.i(TAG, "network_unavailable")
                networkAvailable = false
                scope.launch {
                    if (!intentionalDisconnect.get() && !authFailed.get()) {
                        reconnectJob?.cancel()
                        reconnectJob = null
                    }
                }
            }
        }
        networkCallback = callback
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            networkCallbackRegistered.set(true)
            Log.i(TAG, "network_callback_registered")
        }.onFailure { e ->
            Log.w(TAG, "network_callback_register_failed ${e.message}")
            networkCallbackRegistered.set(false)
        }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager ?: return
        if (!networkCallbackRegistered.getAndSet(false)) return
        networkCallback?.let { cb ->
            runCatching { cm.unregisterNetworkCallback(cb) }
                .onFailure { e -> Log.w(TAG, "network_callback_unregister_failed ${e.message}") }
        }
        networkCallback = null
    }

    // ---------------------------------------------------------------------
    // Socket events
    // ---------------------------------------------------------------------

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (socket !== webSocket) {
                // Stale socket from a superseded attempt; ignore.
                Log.i(TAG, "signaling_stale_open_ignored")
                return
            }
            reconnectAttempt.set(0)
            reconnectJob?.cancel()
            reconnectJob = null
            setState(SignalingConnectionState.Connected)
            Log.i(TAG, "signaling_connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (socket !== webSocket) return
            val envelope = WsEnvelope.parse(text)
            if (envelope == null) {
                Log.w(TAG, "websocket_malformed_message_ignored")
                return
            }
            _messages.tryEmit(envelope)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket !== webSocket) return
            socket = null
            Log.i(TAG, "signaling_disconnected code=$code reason=$reason")
            if (intentionalDisconnect.get()) return
            if (isAuthCloseCode(code)) {
                Log.w(TAG, "signaling_reconnect_cancelled reason=session_replaced_or_auth code=$code")
                markAuthFailed()
                return
            }
            setState(SignalingConnectionState.Disconnected)
            scheduleReconnect("socket_closed")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket !== webSocket) return
            socket = null
            Log.i(
                TAG,
                "signaling_disconnected reason=${t.message ?: "unknown"}",
            )
            if (intentionalDisconnect.get()) return
            if (response != null && (response.code == 401 || response.code == 403)) {
                Log.w(TAG, "signaling_reconnect_cancelled reason=http_auth_failed code=${response.code}")
                markAuthFailed()
                return
            }
            setState(SignalingConnectionState.Disconnected)
            scheduleReconnect("socket_failure")
        }
    }

    private fun isAuthCloseCode(code: Int): Boolean =
        code == 1008 || code == 4001 || code == 4401 || code == 4403

    // ---------------------------------------------------------------------
    // Test seams (internal only; production uses the OkHttp defaults)
    // ---------------------------------------------------------------------

    /** The WebSocket listener instance, exposed for driving unit tests. */
    internal val testListener: WebSocketListener
        get() = listener

    /** The currently active socket, exposed for unit-test assertions. */
    internal fun testActiveSocket(): WebSocket? = socket

    internal fun testSetNetworkUnavailable() {
        networkAvailable = false
    }

    internal fun testIsNetworkAvailable(): Boolean = networkAvailable

    internal fun testAttempt(): Int = reconnectAttempt.get()

    private companion object {
        const val TAG = "SignalingClient"
    }
}
