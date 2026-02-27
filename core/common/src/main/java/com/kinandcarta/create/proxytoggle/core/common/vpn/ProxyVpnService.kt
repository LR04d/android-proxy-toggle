package com.kinandcarta.create.proxytoggle.core.common.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kinandcarta.create.proxytoggle.core.common.R
import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppress("TooManyFunctions")
@AndroidEntryPoint
class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        private const val TAG = "ProxyVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "proxy_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.kinandcarta.proxytoggle.START_VPN"
        private const val ACTION_STOP = "com.kinandcarta.proxytoggle.STOP_VPN"
        private const val EXTRA_PROXY_ADDRESS = "proxy_address"
        private const val EXTRA_PROXY_PORT = "proxy_port"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val MTU_VALUE = 1500

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentProxy = MutableStateFlow<Proxy?>(null)
        val currentProxy: StateFlow<Proxy?> = _currentProxy.asStateFlow()

        fun startVpn(context: Context, proxy: Proxy) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROXY_ADDRESS, proxy.address)
                putExtra(EXTRA_PROXY_PORT, proxy.port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun prepare(context: Context): Intent? {
            return VpnService.prepare(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> handleStartAction(intent)
            ACTION_STOP -> {
                stopVpnConnection()
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private fun handleStartAction(intent: Intent): Int {
        val address = intent.getStringExtra(EXTRA_PROXY_ADDRESS)
        val port = intent.getStringExtra(EXTRA_PROXY_PORT)
        if (address == null || port == null) {
            return START_NOT_STICKY
        }
        startVpnConnection(Proxy(address, port))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnConnection()
    }

    private fun startVpnConnection(proxy: Proxy) {
        if (vpnInterface != null) {
            return // Already running
        }

        try {
            val builder = Builder()
                .setSession("Proxy Toggle VPN")
                .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(MTU_VALUE)
                .setBlocking(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            // Configure HTTP proxy
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setHttpProxy(
                    android.net.ProxyInfo.buildDirectProxy(proxy.address, proxy.port.toInt())
                )
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _isRunning.value = true
                _currentProxy.value = proxy
                startForeground(NOTIFICATION_ID, createNotification(proxy))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting VPN", e)
            stopVpnConnection()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid VPN configuration", e)
            stopVpnConnection()
        }
    }

    private fun stopVpnConnection() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            _isRunning.value = false
            _currentProxy.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping VPN", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Proxy VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when VPN proxy is active"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(proxy: Proxy): Notification {
        val stopIntent = Intent(this, ProxyVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Proxy VPN Active")
            .setContentText("Connected to ${proxy.address}:${proxy.port}")
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Disconnect", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
