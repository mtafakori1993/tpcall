package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.data.api.ApiErrorKind
import ir.tafakoritech.tpcall.data.api.ApiException
import ir.tafakoritech.tpcall.data.model.CallHistoryItemDto
import ir.tafakoritech.tpcall.data.model.CallHistoryPeerDto
import ir.tafakoritech.tpcall.data.model.CallHistoryResponseDto
import ir.tafakoritech.tpcall.data.model.CallDirection
import ir.tafakoritech.tpcall.data.model.CallHistoryStatus
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.ui.history.CallHistoryViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CallHistoryViewModelTest {

    private var lastVm: CallHistoryViewModel? = null

    private fun withMainDispatcher(block: TestScope.() -> Unit) {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                block()
            } finally {
                lastVm?.viewModelScope?.cancel()
                lastVm = null
                Dispatchers.resetMain()
            }
        }
    }

    private data class Harness(
        val vm: CallHistoryViewModel,
        val controller: CallController,
        val auth: FakeAuthGateway,
        val signaling: FakeSignalingGateway,
    )

    private fun TestScope.build(): Harness {
        val auth = FakeAuthGateway()
        auth.meResult = Result.success(UserDto(name = "محمد", extension = "101"))
        val sessionStore = FakeSessionStorage()
        sessionStore.save("token", UserDto(name = "محمد", extension = "101"))
        val signaling = FakeSignalingGateway()
        val controller = CallController(
            scope = backgroundScope,
            sessionStore = sessionStore,
            authApi = auth,
            signalingFactory = { signaling },
            rtc = FakeRtcGateway(),
            audio = FakeCallAudio(),
            notifier = FakeCallNotifier(),
            telecom = FakeTelecomGateway(),
        )
        controller.initialize()
        runCurrent()
        val vm = CallHistoryViewModel(controller, auth, "token", pageSize = 2)
        lastVm = vm
        runCurrent()
        return Harness(vm, controller, auth, signaling)
    }

    private fun item(id: String, startedAt: Instant = Instant.parse("2026-08-19T14:32:00Z")) =
        CallHistoryItemDto(
            id = id,
            callId = "call-$id",
            direction = "outgoing",
            status = "completed",
            peer = CallHistoryPeerDto(name = "حسابداری", extension = "203"),
            startedAt = startedAt.toString(),
            answeredAt = startedAt.plusSeconds(8).toString(),
            endedAt = startedAt.plusSeconds(146).toString(),
            durationSeconds = 138,
        )

    private fun page(calls: List<CallHistoryItemDto>, hasMore: Boolean = false) =
        CallHistoryResponseDto(calls = calls, limit = 2, offset = 0, hasMore = hasMore)

    @Test
    fun `becoming visible loads the first page`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.success(page(listOf(item("a"), item("b"))))

        vm.onScreenVisible()
        runCurrent()

        val state = vm.uiState.value
        assertEquals(listOf(2 to 0), auth.historyRequests)
        assertEquals(2, state.calls.size)
        assertEquals("a", state.calls[0].id)
        assertEquals(CallDirection.OUTGOING, state.calls[0].direction)
        assertEquals(CallHistoryStatus.COMPLETED, state.calls[0].status)
        assertEquals("حسابداری", state.calls[0].peerName)
        assertEquals(138L, state.calls[0].durationSeconds)
        assertNull(state.error)
        assertTrue(!state.loading)
    }

    @Test
    fun `failed first load maps to friendly error`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.failure(
            ApiException(ApiErrorKind.NETWORK, null, "ارتباط با سرور برقرار نیست"),
        )

        vm.onScreenVisible()
        runCurrent()

        val state = vm.uiState.value
        assertEquals("ارتباط با سرور برقرار نیست", state.error)
        assertEquals(0, state.calls.size)
        assertTrue(!state.loading)
    }

    @Test
    fun `failed load without api exception uses generic error`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.failure(RuntimeException("boom"))

        vm.onScreenVisible()
        runCurrent()

        assertEquals("دریافت تاریخچه تماس‌ها ناموفق بود", vm.uiState.value.error)
    }

    @Test
    fun `loadMore appends next page with correct offset`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.success(page(listOf(item("a"), item("b")), hasMore = true))

        vm.onScreenVisible()
        runCurrent()
        assertEquals(listOf(2 to 0), auth.historyRequests)

        auth.historyResult = Result.success(page(listOf(item("c"), item("d")), hasMore = false))
        vm.loadMore()
        runCurrent()

        assertEquals(listOf(2 to 0, 2 to 2), auth.historyRequests)
        val state = vm.uiState.value
        assertEquals(listOf("a", "b", "c", "d"), state.calls.map { it.id })
        assertTrue(!state.hasMore)
    }

    @Test
    fun `duplicate concurrent pagination requests are prevented`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.success(page(listOf(item("a"), item("b")), hasMore = true))
        vm.onScreenVisible()
        runCurrent()

        auth.historyResult = Result.success(page(listOf(item("c"), item("d")), hasMore = true))
        vm.loadMore()
        vm.loadMore() // in flight: must be ignored
        vm.loadMore() // still in flight: must be ignored
        runCurrent()

        assertEquals(2, auth.historyRequests.size)
        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.calls.map { it.id })
    }

    @Test
    fun `loadMore does nothing without hasMore`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.success(page(listOf(item("a"), item("b")), hasMore = false))
        vm.onScreenVisible()
        runCurrent()

        vm.loadMore()
        runCurrent()

        assertEquals(1, auth.historyRequests.size)
    }

    @Test
    fun `failed refresh keeps visible history`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.success(page(listOf(item("a"), item("b"))))
        vm.onScreenVisible()
        runCurrent()
        assertEquals(2, vm.uiState.value.calls.size)

        auth.historyResult = Result.failure(
            ApiException(ApiErrorKind.NETWORK, null, "ارتباط با سرور برقرار نیست"),
        )
        vm.refresh()
        runCurrent()

        val state = vm.uiState.value
        assertEquals(2, state.calls.size) // history not cleared
        assertEquals("ارتباط با سرور برقرار نیست", state.error)
    }

    @Test
    fun `successful refresh replaces with newest data`() = withMainDispatcher {
        val (vm, _, auth) = build()
        auth.historyResult = Result.success(page(listOf(item("a"), item("b"))))
        vm.onScreenVisible()
        runCurrent()

        auth.historyResult = Result.success(page(listOf(item("z"))))
        vm.refresh()
        runCurrent()

        assertEquals(listOf("z"), vm.uiState.value.calls.map { it.id })
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `call ending marks history stale and refreshes while visible`() = withMainDispatcher {
        val (vm, controller, auth, signaling) = build()
        controller.startSignaling()
        signaling.setConnected()
        runCurrent()

        auth.historyResult = Result.success(page(listOf(item("a"))))
        vm.onScreenVisible()
        runCurrent()
        assertEquals(1, auth.historyRequests.size)

        // Start an outgoing call, then the call ends -> stale + auto refresh.
        controller.startCall("203")
        runCurrent()
        signaling.emit("call_ended", payload = buildJsonObject {
            put("call_id", "A")
            put("reason", "remote_hangup")
        })
        runCurrent()

        assertEquals(2, auth.historyRequests.size)
    }

    @Test
    fun `call ending while hidden refreshes on next visit`() = withMainDispatcher {
        val (vm, controller, auth, signaling) = build()
        controller.startSignaling()
        signaling.setConnected()
        runCurrent()

        auth.historyResult = Result.success(page(listOf(item("a"))))
        vm.onScreenVisible()
        runCurrent()
        vm.onScreenHidden()

        controller.startCall("203")
        runCurrent()
        signaling.emit("call_ended", payload = buildJsonObject {
            put("call_id", "A")
            put("reason", "remote_hangup")
        })
        runCurrent()

        // Hidden: no immediate refresh.
        assertEquals(1, auth.historyRequests.size)

        // Reopening the screen refreshes.
        auth.historyResult = Result.success(page(listOf(item("a"), item("b"))))
        vm.onScreenVisible()
        runCurrent()

        assertEquals(2, auth.historyRequests.size)
        assertEquals(2, vm.uiState.value.calls.size)
    }

    @Test
    fun `bad direction or status rows are skipped`() = withMainDispatcher {
        val (vm, _, auth) = build()
        val badStatus = item("bad")
            .copy(status = "mystery")
        val badDirection = item("dir")
            .copy(direction = "sideways")
        val badTime = item("time")
            .copy(startedAt = "not-a-timestamp")
        auth.historyResult = Result.success(page(listOf(item("good"), badStatus, badDirection, badTime)))

        vm.onScreenVisible()
        runCurrent()

        assertEquals(listOf("good"), vm.uiState.value.calls.map { it.id })
    }
}
