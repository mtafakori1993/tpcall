package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.data.model.CallDirection
import ir.tafakoritech.tpcall.data.model.CallHistoryItem
import ir.tafakoritech.tpcall.data.model.CallHistoryItemDto
import ir.tafakoritech.tpcall.data.model.CallHistoryPeerDto
import ir.tafakoritech.tpcall.data.model.CallHistoryStatus
import ir.tafakoritech.tpcall.ui.history.CallHistoryPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CallHistoryPresentationTest {

    private fun item(
        direction: CallDirection = CallDirection.OUTGOING,
        status: CallHistoryStatus = CallHistoryStatus.COMPLETED,
        peerExtension: String = "203",
    ) = CallHistoryItem(
        id = "1",
        callId = "call-1",
        direction = direction,
        status = status,
        peerName = "حسابداری",
        peerExtension = peerExtension,
        startedAt = Instant.parse("2026-08-19T14:32:00Z"),
        answeredAt = null,
        endedAt = null,
        durationSeconds = null,
    )

    @Test
    fun `direction mapping is correct`() {
        assertEquals(
            "تماس ورودی",
            CallHistoryPresentation.directionLabel(item(direction = CallDirection.INCOMING)),
        )
        assertEquals(
            "تماس خروجی",
            CallHistoryPresentation.directionLabel(item(direction = CallDirection.OUTGOING)),
        )
    }

    @Test
    fun `status mapping is correct`() {
        assertEquals(
            "تماس ورودی",
            CallHistoryPresentation.statusLabel(item(CallDirection.INCOMING, CallHistoryStatus.COMPLETED)),
        )
        assertEquals(
            "تماس خروجی",
            CallHistoryPresentation.statusLabel(item(CallDirection.OUTGOING, CallHistoryStatus.COMPLETED)),
        )
        assertEquals(
            "تماس بی‌پاسخ",
            CallHistoryPresentation.statusLabel(item(status = CallHistoryStatus.MISSED)),
        )
        assertEquals(
            "تماس رد شده",
            CallHistoryPresentation.statusLabel(item(status = CallHistoryStatus.REJECTED)),
        )
    }

    @Test
    fun `direction icon mapping is correct`() {
        assertEquals(
            R.drawable.ic_call_received,
            CallHistoryPresentation.directionIconRes(item(CallDirection.INCOMING)),
        )
        assertEquals(
            R.drawable.ic_call_made,
            CallHistoryPresentation.directionIconRes(item(CallDirection.OUTGOING)),
        )
        assertEquals(
            R.drawable.ic_phone_missed,
            CallHistoryPresentation.directionIconRes(item(status = CallHistoryStatus.MISSED)),
        )
        assertEquals(
            R.drawable.ic_call_end,
            CallHistoryPresentation.directionIconRes(item(status = CallHistoryStatus.REJECTED)),
        )
    }

    @Test
    fun `call again uses the peer extension`() {
        val call = item(peerExtension = "305")
        assertEquals("305", CallHistoryPresentation.callAgainExtension(call))
    }

    @Test
    fun `dto to domain maps incoming direction`() {
        val dto = CallHistoryItemDto(
            id = "x",
            callId = "c",
            direction = "incoming",
            status = "missed",
            peer = CallHistoryPeerDto(name = "رضا احمدی", extension = "305"),
            startedAt = "2026-08-18T17:45:00Z",
        )
        val domain = dto.toDomain()!!
        assertEquals(CallDirection.INCOMING, domain.direction)
        assertEquals(CallHistoryStatus.MISSED, domain.status)
        assertEquals("305", domain.peerExtension)
        assertEquals("رضا احمدی", domain.peerName)
        assertEquals(Instant.parse("2026-08-18T17:45:00Z"), domain.startedAt)
        assertNull(domain.durationSeconds)
    }

    @Test
    fun `dto to domain maps outgoing completed with duration`() {
        val dto = CallHistoryItemDto(
            id = "y",
            callId = "c",
            direction = "outgoing",
            status = "completed",
            peer = CallHistoryPeerDto(name = "حسابداری", extension = "203"),
            startedAt = "2026-08-19T14:30:00Z",
            answeredAt = "2026-08-19T14:30:08Z",
            endedAt = "2026-08-19T14:32:26Z",
            durationSeconds = 138,
        )
        val domain = dto.toDomain()!!
        assertEquals(CallDirection.OUTGOING, domain.direction)
        assertEquals(CallHistoryStatus.COMPLETED, domain.status)
        assertEquals(Instant.parse("2026-08-19T14:30:08Z"), domain.answeredAt)
        assertEquals(138L, domain.durationSeconds)
    }

    @Test
    fun `unknown direction and status map to null`() {
        assertEquals(
            null,
            CallHistoryItemDto(
                id = "x",
                direction = "sideways",
                status = "completed",
                startedAt = "2026-08-19T14:30:00Z",
            ).toDomain(),
        )
        assertEquals(
            null,
            CallHistoryItemDto(
                id = "x",
                direction = "outgoing",
                status = "mystery",
                startedAt = "2026-08-19T14:30:00Z",
            ).toDomain(),
        )
        assertEquals(
            null,
            CallHistoryItemDto(
                id = "x",
                direction = "outgoing",
                status = "completed",
                startedAt = "not-a-time",
            ).toDomain(),
        )
    }
}
