package com.hp.vpn

import android.app.Application
import com.hp.proxy.mobile.Mobile
import com.hp.vpn.logging.ProcessLog

class HpApplication : Application() {
    companion object {
        @Volatile private var nativeReady = false
        private val nativeLock = Any()

        fun ensureNativeReady() {
            if (nativeReady) return
            synchronized(nativeLock) {
                if (nativeReady) return
                Mobile.touch()
                Mobile.stop()
                Class.forName("com.hp.vpn.vpn.HevNative")
                ProcessLog.restoreOutput()
                nativeReady = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLog.start()
    }
}
