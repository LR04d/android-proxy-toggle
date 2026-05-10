package com.kinandcarta.create.proxytoggle.core.common.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.kinandcarta.create.proxytoggle.core.common.R
import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * VPN service that applies an HTTP proxy to the device using Android's VPN framework.
 *
 * How it works:
 * - Establishes a VPN interface with [Builder.setHttpProxy] (API 29+)
 * - The VPN becomes the device's default network (higher priority than WiFi/cellular)
 * - Android advertises the proxy via the VPN network's [android.net.LinkProperties]
 * - Apps that respect [android.net.ConnectivityManager.getDefaultProxy] (Chrome, OkHttp,
 *   HttpURLConnection, Retrofit, etc.) will automatically route through the proxy
 *
 * IMPORTANT: This does NOT intercept/forward raw packets. Traffic is NOT routed through
 * the TUN interface. Only the proxy hint is set on the VPN network. Apps that ignore
 * the system proxy will connect directly.
 *
 * Requires VPN permission: call [prepare] first and handle the result before [startVpn].
 */
@Suppress("TooManyFunctions")
@RequiresApi(Build.VERSION_CODES.Q)
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

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentProxy = MutableStateFlow<Proxy?>(null)
        val currentProxy: StateFlow<Proxy?> = _currentProxy.asStateFlow()

        /**
         * Check if VPN mode is supported on this device (API 29+).
         */
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /**
         * Prepare VPN permission. Returns an Intent to launch if user consent is needed,
         * or null if already authorized. Must be called from an Activity context.
         */
        fun prepare(context: Context): Intent? {
            return VpnService.prepare(context)
        }

        fun startVpn(context: Context, proxy: Proxy) {
            if (!isSupported()) {
                Log.w(TAG, "VPN mode requires Android 10 (API 29) or higher")
                return
            }
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROXY_ADDRESS, proxy.address)
                putExtra(EXTRA_PROXY_PORT, proxy.port)
            }
            context.startForegroundService(intent)
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
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
            Log.e(TAG, "Missing proxy address or port in start intent")
            return START_NOT_STICKY
        }
        startVpnConnection(Proxy(address, port))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnConnection()
    }

    override fun onRevoke() {
        // Called when user revokes VPN permission from system settings
        stopVpnConnection()
        super.onRevoke()
    }

    private fun startVpnConnection(proxy: Proxy) {
        if (vpnInterface != null) {
            Log.d(TAG, "VPN already running, updating proxy to ${proxy.address}:${proxy.port}")
            stopVpnConnection()
        }

        try {
            // Configure VPN with proxy hint only — NO route capture.
            // We assign a TUN address (required by VpnService) but do NOT add any routes.
            // This means no traffic enters the TUN interface — data flows through the
            // underlying network normally. The VPN's only purpose is to advertise the
            // HTTP proxy via setHttpProxy(), making it the system default proxy.
            val builder = Builder()
                .setSession("ProxyToggle")
                .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                .setMetered(false)

            // Set HTTP proxy on the VPN network — this is the key mechanism.
            // Android advertises this proxy to all apps via the VPN's LinkProperties.
            // Apps using ConnectivityManager.getDefaultProxy() will see this proxy.
            builder.setHttpProxy(
                ProxyInfo.buildDirectProxy(proxy.address, proxy.port.toInt())
            )

            // Exclude our own app from VPN to prevent routing loops
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not exclude app from VPN", e)
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _isRunning.value = true
                _currentProxy.value = proxy
                startForeground(NOTIFICATION_ID, createNotification(proxy))
                Log.i(TAG, "VPN started with proxy ${proxy.address}:${proxy.port}")
            } else {
                Log.e(TAG, "VPN establish() returned null - permission may not be granted")
                stopSelf()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting VPN - permission not granted?", e)
            stopVpnConnection()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid VPN configuration", e)
            stopVpnConnection()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "VPN service not in valid state", e)
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
            Log.i(TAG, "VPN stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
    }

    private fun createNotificationChannel() {
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

        // Open app when tapping the notification
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val launchPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Proxy VPN Active")
            .setContentText("${proxy.address}:${proxy.port}")
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setOngoing(true)
            .setContentIntent(launchPendingIntent)
            .addAction(R.drawable.ic_stop, "Disconnect", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

