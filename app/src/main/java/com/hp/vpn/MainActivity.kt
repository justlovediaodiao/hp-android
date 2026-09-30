package com.hp.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.hp.vpn.config.AppConfig
import com.hp.vpn.config.ConfigStore
import com.hp.vpn.config.RouteMode
import com.hp.vpn.ui.HpVpnApp
import com.hp.vpn.ui.theme.HpVpnTheme
import com.hp.vpn.vpn.TunnelVpnService
import com.hp.vpn.vpn.VpnConnectionState
import com.hp.vpn.vpn.VpnStatus

class MainActivity : ComponentActivity() {
    private val store by lazy { ConfigStore(this) }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startForegroundService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.START))
        } else {
            VpnStatus.set(VpnConnectionState.STOPPED)
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        prepareVpn()
    }

    private fun connect(config: AppConfig) {
        if (config.mode == RouteMode.INCLUDE && config.packages.isEmpty()) {
            Toast.makeText(this, R.string.include_apps_required, Toast.LENGTH_SHORT).show()
            return
        }
        VpnStatus.set(VpnConnectionState.STARTING)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            prepareVpn()
        }
    }

    private fun prepareVpn() {
        val request = VpnService.prepare(this)
        if (request != null) {
            vpnPermission.launch(request)
        } else {
            startForegroundService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.START))
        }
    }

    private fun disconnect() {
        VpnStatus.set(VpnConnectionState.STOPPING)
        startService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.STOP))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HpVpnTheme {
                HpVpnApp(
                    store = store,
                    onConnect = ::connect,
                    onDisconnect = ::disconnect,
                )
            }
        }
    }
}
