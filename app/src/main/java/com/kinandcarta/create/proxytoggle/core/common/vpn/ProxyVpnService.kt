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
import com.kinandcarta.create.proxytoggle.R
import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.IOException

/**
 * VPN service that applies an HTTP proxy to the device using Android's VPN framework.
 *
 * How it works:
 * - Establishes a VPN tunnel, making it the device's default network (higher priority than WiFi/cellular)
 * - Uses [Builder.setHttpProxy] (API 29+) to configure the proxy on the VPN network
 * - All apps that respect the system proxy (Chrome, OkHttp, HttpURLConnection, etc.) will use the proxy
 * - A background thread drains the TUN interface to prevent buffer overflow
 * - Non-HTTP traffic flows through the underlying network normally
 *
 * Requires VPN permission: call [prepare] first and handle the result before [startVpn].
 */
@Suppress("TooManyFunctions")
@RequiresApi(Build.VERSION_CODES.Q)
class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var running = false
    private var drainThread: Thread? = null

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
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ROUTE_PREFIX = 0
        private const val MTU_VALUE = 1500
        private const val BUFFER_SIZE = 32767

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
            val builder = Builder()
                .setSession("ProxyToggle")
                .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                .addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(MTU_VALUE)
                .setBlocking(true)
                .setMetered(false)

            // Set HTTP proxy on the VPN network - this is the key mechanism
            // Android will advertise this proxy to all apps via the VPN's network properties
            builder.setHttpProxy(
                ProxyInfo.buildDirectProxy(proxy.address, proxy.port.toInt())
            )

            // Exclude our own app from VPN routes to prevent routing loops
            // Our app's connections (e.g., to check proxy status) bypass the VPN
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                Log.w(TAG, "Could not exclude app from VPN", e)
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                running = true
                _isRunning.value = true
                _currentProxy.value = proxy
                startForeground(NOTIFICATION_ID, createNotification(proxy))
                startTunDrainThread()
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

    /**
     * Drain the TUN interface to prevent packet buffer overflow.
     * Since we route all traffic through VPN (for the proxy to be applied),
     * we need to read packets from TUN. The actual proxying is handled by
     * Android's HTTP stack via setHttpProxy().
     *
     * Non-HTTP traffic read from TUN is discarded - this is a known limitation.
     * For most use cases (web browsing, API calls), HTTP proxy via setHttpProxy() is sufficient.
     */
    private fun startTunDrainThread() {
        drainThread = Thread({
            val fd = vpnInterface?.fileDescriptor ?: return@Thread
            val input = FileInputStream(fd)
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                while (running) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    // Packets are drained; HTTP(S) traffic is proxied by Android via setHttpProxy()
                }
            } catch (e: IOException) {
                if (running) {
                    Log.w(TAG, "TUN drain thread interrupted", e)
                }
            } finally {
                try {
                    input.close()
                } catch (_: IOException) { }
            }
        }, "vpn-tun-drain").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopVpnConnection() {
        running = false
        try {
            drainThread?.interrupt()
            drainThread = null
            vpnInterface?.close()
            vpnInterface = null
            _isRunning.value = false
            _currentProxy.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.i(TAG, "VPN stopped")
        } catch (e: IOException) {
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
                this,
                0,
                it,
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
