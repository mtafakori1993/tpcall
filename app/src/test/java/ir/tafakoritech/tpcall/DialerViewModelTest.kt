package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.data.api.ApiErrorKind
import ir.tafakoritech.tpcall.data.api.ApiException
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.ui.home.DialerViewModel
import ir.tafakoritech.tpcall.ui.home.LookupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DialerViewModelTest {

    private class Harness(
        val vm: DialerViewModel,
        val controller: CallController,
        val signaling: FakeSignalingGateway,
        val userCalls: AtomicInteger,
    )

    private var lastVm: DialerViewModel? = null

    /** Runs [block] with Dispatchers.Main routed to the shared test scheduler. */
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

    private fun TestScope.build(
        user: Result<UserDto> = Result.success(
            UserDto(name = "حسابداری", extension = "203", online = true),
        ),
    ): Harness {
        val auth = FakeAuthGateway()
        auth.meResult = Result.success(UserDto(name = "محمد", extension = "101"))
        val userCalls = AtomicInteger()
        auth.userResultOverride = { _ ->
            userCalls.incrementAndGet()
            user
        }

        val sessionStore = FakeSessionStorage()
        sessionStore.save("token", UserDto(name = "محمد", extension = "101"))
        val fakeSignaling = FakeSignalingGateway()
        val controller = CallController(
            scope = backgroundScope,
            sessionStore = sessionStore,
            authApi = auth,
            signalingFactory = { fakeSignaling },
            rtc = FakeRtcGateway(),
            audio = FakeCallAudio(),
            notifier = FakeCallNotifier(),
            telecom = FakeTelecomGateway(),
        )
        controller.initialize()
        runCurrent()
        controller.startSignaling()
        runCurrent()
        val vm = DialerViewModel(controller, auth, "token")
        lastVm = vm
        runCurrent()
        return Harness(vm, controller, fakeSignaling, userCalls)
    }

    private fun TestScope.connect(h: Harness) {
        h.signaling.setConnected()
        runCurrent()
    }

    @Test
    fun `digits are limited to three and backspace removes one`() = withMainDispatcher {
        val h = build()
        h.vm.onDigitPressed('2')
        h.vm.onDigitPressed('0')
        h.vm.onDigitPressed('3')
        h.vm.onDigitPressed('9') // ignored, already three digits
        assertEquals("203", h.vm.uiState.value.extensionInput)

        h.vm.onBackspacePressed()
        assertEquals("20", h.vm.uiState.value.extensionInput)
        h.vm.onBackspacePressed()
        assertEquals("2", h.vm.uiState.value.extensionInput)
        h.vm.onBackspacePressed()
        assertEquals("", h.vm.uiState.value.extensionInput)
    }

    @Test
    fun `clear removes everything`() = withMainDispatcher {
        val h = build()
        h.vm.onDigitPressed('1')
        h.vm.onDigitPressed('2')
        h.vm.onDigitPressed('3')
        h.vm.onClearPressed()
        assertEquals("", h.vm.uiState.value.extensionInput)
        assertEquals(LookupState.IDLE, h.vm.uiState.value.lookup)
    }

    @Test
    fun `self call detection disables call`() = withMainDispatcher {
        val h = build()
        connect(h)

        h.vm.onDigitPressed('1')
        h.vm.onDigitPressed('0')
        h.vm.onDigitPressed('1') // user's own extension
        assertTrue(h.vm.uiState.value.isSelfCall)
        assertFalse(h.vm.isCallEnabled())

        h.vm.onBackspacePressed()
        assertFalse(h.vm.uiState.value.isSelfCall)
    }

    @Test
    fun `call requires signaling connected and valid extension`() = withMainDispatcher {
        val h = build()
        h.vm.onDigitPressed('2')
        h.vm.onDigitPressed('0')
        h.vm.onDigitPressed('3')
        assertFalse(h.vm.isCallEnabled())

        connect(h)
        assertTrue(h.vm.isCallEnabled())
    }

    @Test
    fun `lookup runs once for a complete extension`() = withMainDispatcher {
        val h = build()
        h.vm.onDigitPressed('2')
        h.vm.onDigitPressed('0')
        h.vm.onDigitPressed('3')
        advanceTimeBy(400)
        runCurrent()

        assertEquals(1, h.userCalls.get())
        assertEquals(LookupState.FOUND, h.vm.uiState.value.lookup)
        assertEquals("حسابداری", h.vm.uiState.value.resolvedName)
        assertEquals(true, h.vm.uiState.value.resolvedOnline)
    }

    @Test
    fun `lookup is invalidated when digits change`() = withMainDispatcher {
        val h = build()
        h.vm.onDigitPressed('2')
        h.vm.onDigitPressed('0')
        h.vm.onDigitPressed('3')
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, h.userCalls.get())

        h.vm.onBackspacePressed()
        assertEquals(LookupState.IDLE, h.vm.uiState.value.lookup)
        assertNull(h.vm.uiState.value.resolvedName)
    }

    @Test
    fun `lookup not found maps to NOT_FOUND`() = withMainDispatcher {
        val h = build(
            user = Result.failure(ApiException(ApiErrorKind.NOT_FOUND, "user_not_found", "nf")),
        )
        h.vm.onDigitPressed('9')
        h.vm.onDigitPressed('9')
        h.vm.onDigitPressed('9')
        advanceTimeBy(400)
        runCurrent()

        assertEquals(LookupState.NOT_FOUND, h.vm.uiState.value.lookup)
    }

    @Test
    fun `onCallPressed starts outgoing call when valid`() = withMainDispatcher {
        val h = build()
        connect(h)
        h.vm.onDigitPressed('2')
        h.vm.onDigitPressed('0')
        h.vm.onDigitPressed('3')

        h.vm.onCallPressed()
        runCurrent()

        val sent = h.signaling.sentOfType("call")
        assertEquals(1, sent.size)
        assertEquals("203", sent[0].payload["to"]!!.jsonPrimitive.content)
    }
}
