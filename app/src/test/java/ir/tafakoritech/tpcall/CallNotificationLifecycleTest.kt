package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.notification.CallNotificationDecision
import ir.tafakoritech.tpcall.notification.CallNotificationLifecycle
import ir.tafakoritech.tpcall.notification.CallNotificationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the call-notification foreground-service lifecycle.
 *
 * Verifies the IDLE → RINGING → ACTIVE → ENDED transitions and the invariant
 * that an ACTIVE notification update is never requested before the foreground
 * service notification lifecycle has been established.
 */
class CallNotificationLifecycleTest {

    @Test
    fun `idle to idle produces no action`() {
        val machine = CallNotificationLifecycle()
        assertEquals(CallNotificationDecision.None, machine.transition(CallNotificationPhase.IDLE))
        assertFalse(machine.isForegroundEstablished())
    }

    @Test
    fun `idle to ringing enters foreground`() {
        val machine = CallNotificationLifecycle()
        assertEquals(
            CallNotificationDecision.EnterForeground,
            machine.transition(CallNotificationPhase.RINGING),
        )
        assertTrue(machine.isForegroundEstablished())
    }

    @Test
    fun `ringing to active updates the established foreground notification`() {
        val machine = CallNotificationLifecycle()
        machine.transition(CallNotificationPhase.RINGING)

        assertEquals(
            CallNotificationDecision.UpdateForeground,
            machine.transition(CallNotificationPhase.ACTIVE),
        )
        assertTrue(machine.isForegroundEstablished())
    }

    @Test
    fun `active to idle stops foreground`() {
        val machine = CallNotificationLifecycle()
        machine.transition(CallNotificationPhase.RINGING)
        machine.transition(CallNotificationPhase.ACTIVE)

        assertEquals(
            CallNotificationDecision.StopForeground,
            machine.transition(CallNotificationPhase.IDLE),
        )
        assertFalse(machine.isForegroundEstablished())
    }

    @Test
    fun `idle to active enters foreground instead of bare update`() {
        // The critical invariant: if the first observed phase is ACTIVE (e.g.
        // service started mid-call), the machine must ESTABLISH the foreground
        // notification via startForeground, never attempt a bare notify().
        val machine = CallNotificationLifecycle()
        assertEquals(
            CallNotificationDecision.EnterForeground,
            machine.transition(CallNotificationPhase.ACTIVE),
        )
        assertTrue(machine.isForegroundEstablished())
    }

    @Test
    fun `full ring to active to ended sequence`() {
        val machine = CallNotificationLifecycle()

        assertEquals(
            CallNotificationDecision.EnterForeground,
            machine.transition(CallNotificationPhase.RINGING),
        )
        assertEquals(
            CallNotificationDecision.UpdateForeground,
            machine.transition(CallNotificationPhase.ACTIVE),
        )
        assertEquals(
            CallNotificationDecision.StopForeground,
            machine.transition(CallNotificationPhase.IDLE),
        )
        // Fully torn down: another IDLE is a no-op.
        assertEquals(
            CallNotificationDecision.None,
            machine.transition(CallNotificationPhase.IDLE),
        )
    }

    @Test
    fun `repeated ringing does not lose established state`() {
        val machine = CallNotificationLifecycle()
        machine.transition(CallNotificationPhase.RINGING)
        machine.transition(CallNotificationPhase.RINGING)

        assertTrue(machine.isForegroundEstablished())
        assertEquals(
            CallNotificationDecision.UpdateForeground,
            machine.transition(CallNotificationPhase.ACTIVE),
        )
    }

    @Test
    fun `end then start again re-establishes foreground`() {
        val machine = CallNotificationLifecycle()
        machine.transition(CallNotificationPhase.RINGING)
        machine.transition(CallNotificationPhase.IDLE)

        assertEquals(
            CallNotificationDecision.EnterForeground,
            machine.transition(CallNotificationPhase.RINGING),
        )
    }
}
