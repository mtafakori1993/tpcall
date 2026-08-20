package ir.tafakoritech.tpcall

import android.app.Application
import ir.tafakoritech.tpcall.di.AppContainer

class TpCallApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Register TP Call with Android Telecom (Core-Telecom) once, centrally.
        // Idempotent; safe to call again from the service on device reboots.
        container.telecomManager.register()
        // Validate any stored session and restore authenticated state.
        container.callController.initialize()
    }
}
