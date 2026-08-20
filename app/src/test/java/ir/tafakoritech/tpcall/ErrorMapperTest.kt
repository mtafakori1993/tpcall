package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.util.ErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorMapperTest {

    @Test
    fun `maps known backend codes to Persian messages`() {
        assertEquals("داخلی یا رمز عبور اشتباه است", ErrorMapper.mapError("invalid_credentials"))
        assertEquals("این داخلی قبلاً ثبت شده است", ErrorMapper.mapError("extension_already_exists"))
        assertEquals("داخلی موردنظر وجود ندارد", ErrorMapper.mapError("user_not_found"))
        assertEquals("نمی‌توانید با داخلی خودتان تماس بگیرید", ErrorMapper.mapError("cannot_call_self"))
        assertEquals("وضعیت تماس تغییر کرده است", ErrorMapper.mapError("invalid_call_state"))
        assertEquals("این داخلی در دستگاه دیگری وارد شده است", ErrorMapper.mapError("session_replaced"))
    }

    @Test
    fun `unknown codes map to null`() {
        assertNull(ErrorMapper.mapError("some_new_error"))
        assertNull(ErrorMapper.mapError(null))
    }

    @Test
    fun `call ended reasons map correctly`() {
        assertEquals("پاسخ داده نشد", ErrorMapper.callEndedMessage("no_answer"))
        assertEquals("ارتباط قطع شد", ErrorMapper.callEndedMessage("peer_disconnected"))
        assertNull(ErrorMapper.callEndedMessage("remote_hangup"))
    }
}
