package com.kinandcarta.create.proxytoggle.repository.devicesettings

import android.content.Context
import android.provider.Settings
import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyMode
import com.kinandcarta.create.proxytoggle.core.common.proxyupdate.ProxyUpdateNotifier
import com.kinandcarta.create.proxytoggle.core.common.vpn.ProxyVpnService
import com.kinandcarta.create.proxytoggle.repository.appdata.AppDataRepository
import com.kinandcarta.create.proxytoggle.repository.proxymapper.ProxyMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceSettingsManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proxyMapper: ProxyMapper,
    private val proxyUpdateNotifier: ProxyUpdateNotifier,
    private val appDataRepository: AppDataRepository
) : DeviceSettingsManager {

    private val contentResolver by lazy { context.contentResolver }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _proxySetting = MutableStateFlow(Proxy.Disabled)
    override val proxySetting = _proxySetting.asStateFlow()

    private val _currentMode = MutableStateFlow(ProxyMode.GLOBAL_HTTP_PROXY)
    override val currentMode = _currentMode.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    override val isVpnActive = _isVpnActive.asStateFlow()

    init {
        updateProxyData()
        // Monitor VPN state
        scope.launch {
            ProxyVpnService.isRunning.collect { running ->
                _isVpnActive.value = running
                if (running) {
                    ProxyVpnService.currentProxy.value?.let { proxy ->
                        _proxySetting.value = proxy
                        _currentMode.value = ProxyMode.VPN
                    }
                } else if (_currentMode.value == ProxyMode.VPN) {
                    _proxySetting.value = Proxy.Disabled
                }
                proxyUpdateNotifier.notifyProxyChanged()
            }
        }
    }

    override suspend fun enableProxy(proxy: Proxy, mode: ProxyMode) {
        when (mode) {
            ProxyMode.GLOBAL_HTTP_PROXY -> {
                // Disable VPN if it was active
                if (_isVpnActive.value) {
                    ProxyVpnService.stopVpn(context)
                }
                Settings.Global.putString(
                    contentResolver,
                    Settings.Global.HTTP_PROXY,
                    proxy.toString()
                )
                _currentMode.value = ProxyMode.GLOBAL_HTTP_PROXY
            }
            ProxyMode.VPN -> {
                // Disable global proxy if it was active
                Settings.Global.putString(
                    contentResolver,
                    Settings.Global.HTTP_PROXY,
                    Proxy.Disabled.toString()
                )
                ProxyVpnService.startVpn(context, proxy)
                _currentMode.value = ProxyMode.VPN
            }
        }
        appDataRepository.saveProxy(proxy)
        updateProxyData()
    }

    override fun disableProxy() {
        // Disable both modes
        if (_isVpnActive.value) {
            ProxyVpnService.stopVpn(context)
        }
        Settings.Global.putString(
            contentResolver,
            Settings.Global.HTTP_PROXY,
            Proxy.Disabled.toString()
        )
        updateProxyData()
    }

    private fun updateProxyData() {
        // Check global proxy setting
        val proxySetting = Settings.Global.getString(contentResolver, Settings.Global.HTTP_PROXY)
        val globalProxy = proxyMapper.from(proxySetting)

        // Determine current state
        if (_isVpnActive.value && ProxyVpnService.currentProxy.value != null) {
            _proxySetting.value = ProxyVpnService.currentProxy.value!!
            _currentMode.value = ProxyMode.VPN
        } else if (globalProxy.isEnabled) {
            _proxySetting.value = globalProxy
            _currentMode.value = ProxyMode.GLOBAL_HTTP_PROXY
        } else {
            _proxySetting.value = Proxy.Disabled
        }
        proxyUpdateNotifier.notifyProxyChanged()
    }
}
