package ir.tafakoritech.tpcall.ui.history

import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.data.model.CallDirection
import ir.tafakoritech.tpcall.data.model.CallHistoryItem
import ir.tafakoritech.tpcall.data.model.CallHistoryStatus

/**
 * Pure mapping from history model to presentation strings/icons so the
 * direction/status rules can be unit-tested without a Compose runtime.
 */
object CallHistoryPresentation {

    /** Primary per-row label: direction for completed, outcome otherwise. */
    fun statusLabel(call: CallHistoryItem): String = when (call.status) {
        CallHistoryStatus.COMPLETED -> directionLabel(call)
        CallHistoryStatus.MISSED -> "تماس بی‌پاسخ"
        CallHistoryStatus.REJECTED -> "تماس رد شده"
    }

    fun directionLabel(call: CallHistoryItem): String = when (call.direction) {
        CallDirection.INCOMING -> "تماس ورودی"
        CallDirection.OUTGOING -> "تماس خروجی"
    }

    /** Accessible description of the leading direction icon. */
    fun directionDescription(call: CallHistoryItem): String = when (call.status) {
        CallHistoryStatus.MISSED -> "تماس بی‌پاسخ"
        CallHistoryStatus.REJECTED -> "تماس رد شده"
        CallHistoryStatus.COMPLETED -> directionLabel(call)
    }

    fun directionIconRes(call: CallHistoryItem): Int = when {
        call.status == CallHistoryStatus.MISSED -> R.drawable.ic_phone_missed
        call.status == CallHistoryStatus.REJECTED -> R.drawable.ic_call_end
        call.direction == CallDirection.INCOMING -> R.drawable.ic_call_received
        else -> R.drawable.ic_call_made
    }

    /** Extension dialed by the call-again action (always the peer's). */
    fun callAgainExtension(call: CallHistoryItem): String = call.peerExtension
}
