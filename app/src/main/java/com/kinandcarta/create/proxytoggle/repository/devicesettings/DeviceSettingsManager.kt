package com.kinandcarta.create.proxytoggle.repository.devicesettings

import android.content.Intent
import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyMode
import kotlinx.coroutines.flow.StateFlow

interface DeviceSettingsManager {
    val proxySetting: StateFlow<Proxy>
    val currentMode: StateFlow<ProxyMode>
    val isVpnActive: StateFlow<Boolean>
    suspend fun enableProxy(proxy: Proxy, mode: ProxyMode = ProxyMode.GLOBAL_HTTP_PROXY)
    fun disableProxy()

    /**
     * Prepare VPN connection. Returns an Intent that must be launched via
     * startActivityForResult if VPN permission is needed, or null if already authorized.
     */
    fun prepareVpn(): Intent?

    /**
     * Check if VPN mode is supported on the current device (requires API 29+).
     */
    fun isVpnSupported(): Boolean
}
