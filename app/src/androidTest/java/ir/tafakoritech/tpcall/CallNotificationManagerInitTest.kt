package ir.tafakoritech.tpcall

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.tafakoritech.tpcall.notification.CallNotificationManager
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the app-startup crash in
 * [CallNotificationManager] (TpCallApplication.onCreate → AppContainer →
 * CallNotificationManager.<init> → createChannels()).
 *
 * The constructor previously read a `by lazy` property from an `init` block
 * that ran BEFORE the lazy delegate was initialized, throwing an NPE on
 * `kotlin.Lazy.getValue()`. This test verifies construction succeeds and that
 * channel creation is idempotent across repeated app starts.
 */
@RunWith(AndroidJUnit4::class)
class CallNotificationManagerInitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun construction_createsAllChannelsWithoutCrashing() {
        val manager = CallNotificationManager(context)
        assertNotNull(manager)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(nm.getNotificationChannel(CallNotificationManager.CHANNEL_SIGNALING))
        assertNotNull(nm.getNotificationChannel(CallNotificationManager.CHANNEL_INCOMING_CALL))
        assertNotNull(nm.getNotificationChannel(CallNotificationManager.CHANNEL_ACTIVE_CALL))
    }

    @Test
    fun repeatedConstruction_isIdempotent() {
        // Simulates the app starting multiple times; must never throw and must
        // leave the current channels in place (legacy "calls" channel cleanup
        // is guarded so repeated deletion is safe).
        repeat(3) {
            val manager = CallNotificationManager(context)
            assertNotNull(manager)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(nm.getNotificationChannel(CallNotificationManager.CHANNEL_INCOMING_CALL))
        assertNotNull(nm.getNotificationChannel(CallNotificationManager.CHANNEL_ACTIVE_CALL))
    }
}
