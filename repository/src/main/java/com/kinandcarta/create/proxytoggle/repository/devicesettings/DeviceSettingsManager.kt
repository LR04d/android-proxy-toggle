package com.kinandcarta.create.proxytoggle.repository.devicesettings

import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyMode
import kotlinx.coroutines.flow.StateFlow

interface DeviceSettingsManager {
    val proxySetting: StateFlow<Proxy>
    val currentMode: StateFlow<ProxyMode>
    val isVpnActive: StateFlow<Boolean>
    suspend fun enableProxy(proxy: Proxy, mode: ProxyMode = ProxyMode.GLOBAL_HTTP_PROXY)
    fun disableProxy()
}
