package com.hp.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.hp.proxy.mobile.Mobile
import com.hp.vpn.HpApplication
import com.hp.vpn.config.AppConfig
import com.hp.vpn.config.ConfigStore
import com.hp.vpn.config.RouteMode
import com.hp.vpn.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import android.util.Base64
import java.io.File
import java.security.SecureRandom

enum class VpnConnectionState { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

data class VpnStatusValue(
    val state: VpnConnectionState,
    val detail: String? = null,
)

object VpnStatus {
    private val mutable = MutableStateFlow(VpnStatusValue(VpnConnectionState.STOPPED))
    val state = mutable.asStateFlow()
    fun set(state: VpnConnectionState, detail: String? = null) {
        mutable.value = VpnStatusValue(state, detail)
    }
}

class TunnelVpnService : VpnService() {
    companion object {
        const val START = "com.hp.vpn.START"
        const val STOP = "com.hp.vpn.STOP"
        private const val CHANNEL = "hp-vpn-status"
        private const val NOTIFICATION = 1001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val control = Mutex()
    private var tun: ParcelFileDescriptor? = null
    private var hevTun: ParcelFileDescriptor? = null
    private var hevConfig: File? = null
    private var goRunning = false
    private var hevRunning = false
    private var startFailed = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(com.hp.vpn.R.string.vpn_channel), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> {
                startForeground(NOTIFICATION, notification(getString(com.hp.vpn.R.string.vpn_channel)))
                scope.launch { control.withLock { startVpn() } }
            }
            STOP -> scope.launch { control.withLock { stopVpn(); stopSelf() } }
        }
        return START_NOT_STICKY
    }

    private suspend fun startVpn() {
        stopResources()
        startFailed = false
        VpnStatus.set(VpnConnectionState.STARTING)
        try {
            HpApplication.ensureNativeReady()
            val config = ConfigStore(this).read()
            val profile = config.profiles.firstOrNull { it.id == config.selectedId }
                ?: error("Create and select a profile first")
            val cert = if (profile.cert.isNotBlank()) profile.cert else platformRoots()
            val port = 32768 + SecureRandom().nextInt(28232)
            Mobile.start("127.0.0.1:$port", profile.server, profile.password, cert)
            goRunning = true
            AppLog.write("VPN", "SOCKS5 listening on 127.0.0.1:$port")

            val builder = Builder().setSession("HP VPN").setMtu(1500)
                .addAddress("198.18.0.1", 15)
                .addRoute("0.0.0.0", 0)
                .addAddress("fc00::1", 64)
                .addRoute("::", 0)
                .addDnsServer("198.18.0.2")
            applyRouting(builder, config)
            tun = builder.establish() ?: error("Android could not establish the VPN interface")
            val yaml = """
                tunnel:
                  mtu: 1500
                  ipv4: 198.18.0.1
                  ipv6: 'fc00::1'
                socks5:
                  address: 127.0.0.1
                  port: $port
                  udp: udp
                mapdns:
                  address: 198.18.0.2
                  port: 53
                  network: 100.64.0.0
                  netmask: 255.192.0.0
                  cache-size: 10000
                misc:
                  log-file: stderr
                  log-level: warn
            """.trimIndent()
            hevConfig = File(cacheDir, "hev-runtime.yml").apply { writeText(yaml) }
            hevTun = ParcelFileDescriptor.dup(tun!!.fileDescriptor)
            if (!HevNative.TProxyStartService(hevConfig!!.absolutePath, hevTun!!.fd)) {
                error("hev failed to start")
            }
            hevRunning = true
            VpnStatus.set(VpnConnectionState.RUNNING)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION, notification(getString(com.hp.vpn.R.string.connected)))
            AppLog.write("VPN", "Tunnel established; mapdns=198.18.0.2")
        } catch (e: Exception) {
            failStart(e)
        } catch (e: LinkageError) {
            failStart(e)
        }
    }

    private fun failStart(error: Throwable) {
        val reason = error.message ?: error.javaClass.simpleName
        startFailed = true
        AppLog.write("VPN", "Start failed: $reason")
        VpnStatus.set(VpnConnectionState.FAILED, reason)
        stopResources()
        stopSelf()
    }

    private fun applyRouting(builder: Builder, config: AppConfig) {
        val installed = packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
        when (config.mode) {
            RouteMode.GLOBAL -> builder.addDisallowedApplication(packageName)
            RouteMode.INCLUDE -> {
                val selected = config.packages.filter { it in installed && it != packageName }
                require(selected.isNotEmpty()) { "Select at least one installed app for selected-app routing" }
                selected.forEach { builder.addAllowedApplication(it) }
            }
            RouteMode.EXCLUDE -> {
                builder.addDisallowedApplication(packageName)
                config.packages.filter { it in installed && it != packageName }
                    .forEach { builder.addDisallowedApplication(it) }
            }
        }
    }

    private fun platformRoots(): String {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        val manager = factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: error("Could not read system certificates")
        val roots = manager.acceptedIssuers
        require(roots.isNotEmpty()) { "The system certificate list is empty" }
        return roots.joinToString("\n") { cert: X509Certificate ->
            val raw = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
            "-----BEGIN CERTIFICATE-----\n" + raw.chunked(64).joinToString("\n") +
                "\n-----END CERTIFICATE-----"
        }
    }

    private suspend fun stopVpn() {
        VpnStatus.set(VpnConnectionState.STOPPING)
        stopResources()
        VpnStatus.set(VpnConnectionState.STOPPED)
        AppLog.write("VPN", "Stopped")
    }

    private fun stopResources() {
        if (hevRunning) {
            try { HevNative.TProxyStopService() } catch (e: Exception) { AppLog.write("hev", "Stop failed: ${e.message}") }
            hevRunning = false
        }
        hevTun?.close()
        hevTun = null
        hevConfig?.delete()
        hevConfig = null
        tun?.close()
        tun = null
        if (goRunning) {
            try { Mobile.stop() } catch (e: Exception) { AppLog.write("Go", "Stop failed: ${e.message}") }
            goRunning = false
        }
    }

    override fun onRevoke() { scope.launch { control.withLock { stopVpn(); stopSelf() } } }

    override fun onDestroy() {
        if (!startFailed) VpnStatus.set(VpnConnectionState.STOPPED)
        if (hevRunning || goRunning || tun != null) {
            scope.launch {
                control.withLock { stopResources() }
                scope.cancel()
            }
        } else {
            scope.cancel()
        }
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val stop = PendingIntent.getService(this, 0, Intent(this, TunnelVpnService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("HP VPN")
            .setContentText(text)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(com.hp.vpn.R.string.stop), stop)
            .build()
    }
}
