package com.hp.vpn.vpn

object HevNative {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    // JNI_OnLoad registers all four methods, including those not called by the app.
    external fun TProxyStartService(configPath: String, fd: Int): Boolean
    external fun TProxyStopService(): Boolean
    external fun TProxyIsRunning(): Boolean
    external fun TProxyGetStats(): LongArray
}
