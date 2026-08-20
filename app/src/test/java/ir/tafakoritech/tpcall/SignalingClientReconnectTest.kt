package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.data.signaling.SignalingClient
import ir.tafakoritech.tpcall.data.signaling.SignalingConnectionState
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Verifies the real SignalingClient reconnect worker: single-socket ownership,
 * single reconnect job, exponential backoff, network gating, auth cancellation
 * and backoff reset. Uses the internal test seams (fake socket factory).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalingClientReconnectTest {

    private class FakeWebSocket : WebSocket {
        var closed = false
        override fun request(): Request =
            Request.Builder().url("ws://test/ws").build()

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean = true

        override fun send(bytes: ByteString): Boolean = true

        override fun close(code: Int, reason: String?): Boolean {
            closed = true
            return true
        }

        override fun cancel() {}
    }

    private val dispatcher = StandardTestDispatcher()

    private val sockets = mutableListOf<FakeWebSocket>()
    private val openCount = AtomicInteger(0)

    private fun openResponse(): Response =
        Response.Builder()
            .code(101)
            .message("Upgrade")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(Request.Builder().url("ws://test/ws").build())
            .build()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.buildClient(): SignalingClient {
        sockets.clear()
        openCount.set(0)
        val factory: (Request, WebSocketListener) -> WebSocket = { _, _ ->
            val ws = FakeWebSocket()
            sockets.add(ws)
            openCount.incrementAndGet()
            ws
        }
        return SignalingClient(
            connectivityManager = null,
            client = OkHttpClient(),
            scope = backgroundScope,
            jitterRandom = Random(0),
            socketFactory = factory,
        )
    }

    /** Simulates the async onOpen the OkHttp runtime would deliver. */
    private fun TestScope.openCurrentSocket(client: SignalingClient) {
        val ws = client.testActiveSocket() ?: return
        client.testListener.onOpen(ws, openResponse())
        runCurrent()
    }

    private fun TestScope.startConnected(client: SignalingClient) {
        client.start("token")
        runCurrent()
        openCurrentSocket(client)
        assertEquals(SignalingConnectionState.Connected, client.connectionState.value)
        assertEquals(1, openCount.get())
    }

    @Test
    fun `unexpected socket failure schedules reconnect`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        val ws = client.testActiveSocket()!!
        client.testListener.onFailure(ws, RuntimeException("tcp reset"), null)
        runCurrent()
        assertTrue(client.connectionState.value is SignalingConnectionState.Reconnecting)
    }

    @Test
    fun `first retry happens after about one second`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        val ws = client.testActiveSocket()!!
        client.testListener.onFailure(ws, RuntimeException("drop"), null)
        runCurrent()

        // Before 1s elapses, no new socket.
        advanceTimeBy(900)
        runCurrent()
        assertEquals(1, openCount.get())

        // After ~1s, a new socket is opened.
        advanceTimeBy(200)
        runCurrent()
        assertEquals(2, openCount.get())
    }

    @Test
    fun `second retry uses longer backoff`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        // First failure.
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("x"), null)
        advanceTimeBy(1_500)
        runCurrent()
        // Second failure of the new socket.
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("y"), null)
        runCurrent()
        assertTrue(client.testAttempt() >= 2)

        // ~2s (jitter 1.6–2.4s) should have opened the next socket.
        advanceTimeBy(2_500)
        runCurrent()
        assertTrue(openCount.get() >= 3)
    }

    @Test
    fun `backoff caps at 15 seconds`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        // Force many consecutive failures.
        repeat(6) {
            client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("z"), null)
            advanceTimeBy(16_000)
            runCurrent()
        }
        assertTrue(client.testAttempt() >= 6)
        assertTrue(openCount.get() >= 7)
    }

    @Test
    fun `successful reconnect resets attempt`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("drop"), null)
        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(client.testAttempt() >= 1)

        // New socket opened by the reconnect worker -> onOpen resets attempt.
        openCurrentSocket(client)
        assertEquals(0, client.testAttempt())
        assertEquals(SignalingConnectionState.Connected, client.connectionState.value)
    }

    @Test
    fun `logout cancels reconnect`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("drop"), null)
        runCurrent()

        client.stop()
        runCurrent()
        val openedAfterStop = openCount.get()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(openedAfterStop, openCount.get())
        assertEquals(SignalingConnectionState.Disconnected, client.connectionState.value)
    }

    @Test
    fun `auth close cancels reconnect`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        client.testListener.onClosed(client.testActiveSocket()!!, 4401, "session_replaced")
        runCurrent()
        assertEquals(SignalingStatus.AUTH_FAILED, client.status.value)
        val openedAfterAuth = openCount.get()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(openedAfterAuth, openCount.get())
    }

    @Test
    fun `http auth failure cancels reconnect`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        val response = Response.Builder()
            .code(401)
            .message("Unauthorized")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(Request.Builder().url("ws://test/ws").build())
            .build()
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("401"), response)
        runCurrent()
        assertEquals(SignalingStatus.AUTH_FAILED, client.status.value)
        val openedAfterAuth = openCount.get()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(openedAfterAuth, openCount.get())
    }

    @Test
    fun `network unavailable prevents retry loop`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        client.testSetNetworkUnavailable()
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("no net"), null)
        runCurrent()

        // No reconnect scheduled while offline.
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, openCount.get())
        assertEquals(SignalingConnectionState.Disconnected, client.connectionState.value)
    }

    @Test
    fun `network available triggers immediate reconnect`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        client.testSetNetworkUnavailable()
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("no net"), null)
        runCurrent()
        assertEquals(1, openCount.get())

        // Network returns -> immediate reconnect without backoff.
        client.onNetworkAvailable()
        runCurrent()
        assertEquals(2, openCount.get())
        openCurrentSocket(client)
        assertEquals(SignalingConnectionState.Connected, client.connectionState.value)
    }

    @Test
    fun `duplicate failure callbacks create only one reconnect job`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        val ws = client.testActiveSocket()!!
        client.testListener.onFailure(ws, RuntimeException("a"), null)
        client.testListener.onFailure(ws, RuntimeException("b"), null)
        client.testListener.onClosed(ws, 1006, "closed")
        runCurrent()

        // Only one reconnect scheduled -> only one new socket after backoff.
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(2, openCount.get())
    }

    @Test
    fun `only one active socket at a time`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)
        val first = client.testActiveSocket()!!
        client.testListener.onFailure(first, RuntimeException("drop"), null)
        advanceTimeBy(1_500)
        runCurrent()
        val second = client.testActiveSocket()
        assertNotSame(first, second)
        assertSame(second, client.testActiveSocket())
        assertEquals(2, openCount.get())
    }

    @Test
    fun `reconnect after backend restart`() = runTest(dispatcher) {
        val client = buildClient()
        startConnected(client)

        // Simulate backend restart: abrupt close with no clean handshake.
        client.testListener.onFailure(client.testActiveSocket()!!, RuntimeException("connection reset"), null)
        advanceTimeBy(1_500)
        runCurrent()
        openCurrentSocket(client)
        // Socket restored after backoff.
        assertEquals(SignalingConnectionState.Connected, client.connectionState.value)
        assertTrue(openCount.get() >= 2)
    }
}
