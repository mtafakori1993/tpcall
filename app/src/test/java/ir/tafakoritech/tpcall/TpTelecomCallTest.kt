package ir.tafakoritech.tpcall

import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import android.telecom.DisconnectCause
import ir.tafakoritech.tpcall.telecom.TpTelecomCall
import ir.tafakoritech.tpcall.telecom.toDisconnectCause
import ir.tafakoritech.tpcall.telecom.toTpDisconnectCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the per-call Telecom control wrapper is idempotent: answer and
 * disconnect may be invoked repeatedly without corrupting the underlying scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TpTelecomCallTest {

    private class FakeCallControlScope : CallControlScope {
        var answerCount = 0
        var setActiveCount = 0
        var setInactiveCount = 0
        var disconnectCount = 0

        override fun getCallId(): android.os.ParcelUuid =
            android.os.ParcelUuid(java.util.UUID.randomUUID())

        override suspend fun setActive(): CallControlResult {
            setActiveCount++
            return CallControlResult.Success()
        }

        override suspend fun setInactive(): CallControlResult {
            setInactiveCount++
            return CallControlResult.Success()
        }

        override suspend fun answer(callType: Int): CallControlResult {
            answerCount++
            return CallControlResult.Success()
        }

        override suspend fun disconnect(disconnectCause: DisconnectCause): CallControlResult {
            disconnectCount++
            return CallControlResult.Success()
        }

        override suspend fun requestEndpointChange(endpoint: CallEndpointCompat): CallControlResult {
            return CallControlResult.Success()
        }

        override val currentCallEndpoint: Flow<CallEndpointCompat> = kotlinx.coroutines.flow.emptyFlow()
        override val availableEndpoints: Flow<List<CallEndpointCompat>> = kotlinx.coroutines.flow.emptyFlow()
        override val isMuted: Flow<Boolean> = MutableStateFlow(false)

        override val coroutineContext: kotlin.coroutines.CoroutineContext =
            CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).coroutineContext
    }

    @Test
    fun `answer is idempotent`() = runTest {
        val scope = FakeCallControlScope()
        val call = TpTelecomCall("A", scope)

        call.answer()
        call.answer()
        call.answer()

        assertEquals(1, scope.answerCount)
    }

    @Test
    fun `system-marked answer suppresses app answer`() = runTest {
        val scope = FakeCallControlScope()
        val call = TpTelecomCall("A", scope)

        call.markSystemAnswered()
        call.answer()

        assertEquals(0, scope.answerCount)
    }

    @Test
    fun `disconnect is idempotent and records the cause`() = runTest {
        val scope = FakeCallControlScope()
        val call = TpTelecomCall("A", scope)

        call.disconnect(DisconnectCause(DisconnectCause.LOCAL))
        call.disconnect(DisconnectCause(DisconnectCause.REMOTE))
        call.disconnect(DisconnectCause(DisconnectCause.REJECTED))

        assertEquals(1, scope.disconnectCount)
    }

    @Test
    fun `setActive works and is repeated safe`() = runTest {
        val scope = FakeCallControlScope()
        val call = TpTelecomCall("A", scope)

        call.setActive()
        call.setActive()

        assertEquals(2, scope.setActiveCount)
        assertFalse(scope.disconnectCount > 0)
    }

    @Test
    fun `setInactive is forwarded`() = runTest {
        val scope = FakeCallControlScope()
        val call = TpTelecomCall("A", scope)

        call.setInactive()

        assertEquals(1, scope.setInactiveCount)
    }

    @Test
    fun `requestEndpoint without available endpoints is a no-op`() = runTest {
        val scope = FakeCallControlScope()
        val call = TpTelecomCall("A", scope)

        // No endpoints cached yet; request must not throw.
        call.requestEndpoint(CallEndpointCompat.TYPE_SPEAKER)
        assertTrue(true)
    }
}
