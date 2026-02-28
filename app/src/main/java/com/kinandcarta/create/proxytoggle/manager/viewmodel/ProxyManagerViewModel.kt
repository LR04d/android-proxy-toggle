package com.kinandcarta.create.proxytoggle.manager.viewmodel

import android.content.Intent
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kinandcarta.create.proxytoggle.R
import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyMode
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyProfile
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyValidator
import com.kinandcarta.create.proxytoggle.repository.appdata.AppDataRepository
import com.kinandcarta.create.proxytoggle.repository.devicesettings.DeviceSettingsManager
import com.kinandcarta.create.proxytoggle.repository.profile.ProfileRepository
import com.kinandcarta.create.proxytoggle.repository.proxymode.ProxyModeRepository
import com.kinandcarta.create.proxytoggle.repository.userprefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class ProxyManagerViewModel @Inject constructor(
    private val deviceSettingsManager: DeviceSettingsManager,
    private val proxyValidator: ProxyValidator,
    private val appDataRepository: AppDataRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val profileRepository: ProfileRepository,
    private val proxyModeRepository: ProxyModeRepository
) : ViewModel() {

    private var _uiState = mutableStateOf<UiState>(
        UiState.Disconnected(
            addressState = TextFieldState(text = ""),
            portState = TextFieldState(text = ""),
            pastProxies = emptyList()
        )
    )
    val uiState: State<UiState> = _uiState

    // Profile related flows
    val profiles: Flow<List<ProxyProfile>> = profileRepository.profiles
    val selectedProfile: Flow<ProxyProfile?> = profileRepository.selectedProfile
    val proxyMode: Flow<ProxyMode> = proxyModeRepository.proxyMode

    private val _currentProxyMode = MutableStateFlow(ProxyMode.GLOBAL_HTTP_PROXY)
    val currentProxyMode: StateFlow<ProxyMode> = _currentProxyMode.asStateFlow()

    private val _showProfileSelector = MutableStateFlow(false)
    val showProfileSelector: StateFlow<Boolean> = _showProfileSelector.asStateFlow()

    // VPN permission request flow
    private val _vpnPermissionRequest = MutableSharedFlow<Intent>()
    val vpnPermissionRequest: SharedFlow<Intent> = _vpnPermissionRequest.asSharedFlow()

    // Pending proxy to connect after VPN permission is granted
    private var pendingVpnProxy: Proxy? = null

    val isVpnSupported: Boolean
        get() = deviceSettingsManager.isVpnSupported()

    init {
        viewModelScope.launch {
            combine(
                deviceSettingsManager.proxySetting,
                appDataRepository.pastProxies,
                proxyModeRepository.proxyMode
            ) { proxy: Proxy, pastProxies: List<Proxy>, mode: ProxyMode ->
                Triple(proxy, pastProxies, mode)
            }.collect { (proxy, pastProxies, mode) ->
                _currentProxyMode.value = mode
                if (proxy.isEnabled) {
                    _uiState.value = UiState.Connected(
                        addressState = TextFieldState(text = proxy.address),
                        portState = TextFieldState(text = proxy.port),
                        proxyMode = mode
                    )
                } else {
                    val (addressText, portText) = pastProxies.firstOrNull()?.let {
                        Pair(it.address, it.port)
                    } ?: Pair("", "")
                    _uiState.value = UiState.Disconnected(
                        addressState = TextFieldState(text = addressText),
                        portState = TextFieldState(text = portText),
                        pastProxies = pastProxies
                    )
                }
            }
        }
    }

    fun onUserInteraction(userInteraction: UserInteraction) {
        when (userInteraction) {
            UserInteraction.ToggleProxyClicked -> toggleProxy()
            UserInteraction.SwitchThemeClicked -> toggleTheme()
            is UserInteraction.AddressChanged -> onAddressChanged(userInteraction.newAddress)
            is UserInteraction.PortChanged -> onPortChanged(userInteraction.newPort)
            is UserInteraction.ProxyFromDropDownSelected -> onProxySelected(userInteraction.proxy)
            UserInteraction.ShowProfileSelector -> _showProfileSelector.value = true
            UserInteraction.HideProfileSelector -> _showProfileSelector.value = false
            is UserInteraction.ProxyModeChanged -> onProxyModeChanged(userInteraction.mode)
            is UserInteraction.ConnectWithProfile -> connectWithProfile(userInteraction.profile)
            is UserInteraction.SaveAsProfile -> saveCurrentAsProfile(userInteraction.name)
        }
    }

    fun onForceFocusExecuted() {
        updateDisconnectedState {
            it.copy(
                addressState = it.addressState.copy(forceFocus = false),
                portState = it.portState.copy(forceFocus = false)
            )
        }
    }

    private fun toggleProxy() {
        if (deviceSettingsManager.proxySetting.value.isEnabled) {
            deviceSettingsManager.disableProxy()
        } else {
            enableProxyIfNoErrors()
        }
    }

    private fun toggleTheme() {
        viewModelScope.launch {
            userPreferencesRepository.toggleTheme()
        }
    }

    private fun onAddressChanged(newText: String) {
        val newTextFiltered = newText.filter { it.isDigit() || it == '.' }
        updateDisconnectedState {
            it.copy(addressState = it.addressState.copy(text = newTextFiltered))
        }
    }

    private fun onPortChanged(newText: String) {
        val newTextFiltered = newText
            .filter(Char::isDigit)
            .take(ProxyValidator.MAX_PORT.toString().length)
        updateDisconnectedState {
            it.copy(portState = it.portState.copy(text = newTextFiltered))
        }
    }

    private fun onProxySelected(proxy: Proxy) {
        updateDisconnectedState {
            UiState.Disconnected(
                addressState = TextFieldState(text = proxy.address),
                portState = TextFieldState(text = proxy.port),
                pastProxies = it.pastProxies
            )
        }
    }

    private fun onProxyModeChanged(mode: ProxyMode) {
        viewModelScope.launch {
            proxyModeRepository.setProxyMode(mode)
        }
    }

    private fun connectWithProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            val mode = proxyModeRepository.proxyMode.first()
            profileRepository.selectProfile(profile.id)
            profileRepository.updateLastUsed(profile.id)
            if (mode == ProxyMode.VPN) {
                enableVpnProxy(profile.proxy)
            } else {
                deviceSettingsManager.enableProxy(profile.proxy, mode)
            }
            _showProfileSelector.value = false
        }
    }

    private fun saveCurrentAsProfile(name: String) {
        viewModelScope.launch {
            val address = uiState.value.addressState.text
            val port = uiState.value.portState.text
            if (address.isNotBlank() && port.isNotBlank()) {
                val profile = ProxyProfile(
                    name = name,
                    address = address,
                    port = port
                )
                profileRepository.saveProfile(profile)
            }
        }
    }

    private fun enableProxyIfNoErrors() {
        updateErrors(ProxyManagerError.NoError)
        updateDisconnectedState {
            it.copy(
                addressState = it.addressState.copy(forceFocus = false),
                portState = it.portState.copy(forceFocus = false)
            )
        }

        val address = uiState.value.addressState.text
        val port = uiState.value.portState.text

        viewModelScope.launch {
            when {
                proxyValidator.isValidIP(address).not() -> {
                    delay(ERROR_DELAY)
                    updateErrors(ProxyManagerError.InvalidAddress)
                }

                proxyValidator.isValidPort(port).not() -> {
                    delay(ERROR_DELAY)
                    updateErrors(ProxyManagerError.InvalidPort)
                }

                else -> {
                    val mode = proxyModeRepository.proxyMode.first()
                    val proxy = Proxy(address, port)
                    if (mode == ProxyMode.VPN) {
                        enableVpnProxy(proxy)
                    } else {
                        deviceSettingsManager.enableProxy(proxy, mode)
                    }
                }
            }
        }
    }

    /**
     * Enable proxy in VPN mode. Checks for VPN permission first.
     * If permission is needed, emits a request to the UI and saves the proxy as pending.
     */
    private fun enableVpnProxy(proxy: Proxy) {
        viewModelScope.launch {
            val prepareIntent = deviceSettingsManager.prepareVpn()
            if (prepareIntent != null) {
                // Need VPN permission - save pending proxy and request permission from UI
                pendingVpnProxy = proxy
                _vpnPermissionRequest.emit(prepareIntent)
            } else {
                // Permission already granted, proceed
                deviceSettingsManager.enableProxy(proxy, ProxyMode.VPN)
            }
        }
    }

    /**
     * Called by the UI after VPN permission is granted by the user.
     */
    fun onVpnPermissionGranted() {
        val proxy = pendingVpnProxy ?: return
        pendingVpnProxy = null
        viewModelScope.launch {
            deviceSettingsManager.enableProxy(proxy, ProxyMode.VPN)
        }
    }

    /**
     * Called by the UI when VPN permission is denied.
     */
    fun onVpnPermissionDenied() {
        pendingVpnProxy = null
    }

    private fun updateDisconnectedState(updateFunc: (UiState.Disconnected) -> UiState.Disconnected) {
        (uiState.value as? UiState.Disconnected)?.let {
            _uiState.value = updateFunc(it)
        }
    }

    private fun updateErrors(errorState: ProxyManagerError) {
        updateDisconnectedState {
            when (errorState) {
                ProxyManagerError.InvalidAddress -> {
                    it.copy(
                        addressState = it.addressState.copy(
                            error = R.string.error_invalid_address,
                            forceFocus = true
                        )
                    )
                }

                ProxyManagerError.InvalidPort -> {
                    it.copy(
                        portState = it.portState.copy(
                            error = R.string.error_invalid_port,
                            forceFocus = true
                        )
                    )
                }

                ProxyManagerError.NoError -> {
                    it.copy(
                        addressState = it.addressState.copy(error = null),
                        portState = it.portState.copy(error = null)
                    )
                }
            }
        }
    }

    @VisibleForTesting
    fun getInternalUiState(): MutableState<UiState> {
        return _uiState
    }

    sealed class UiState {
        abstract val addressState: TextFieldState
        abstract val portState: TextFieldState

        data class Connected(
            override val addressState: TextFieldState,
            override val portState: TextFieldState,
            val proxyMode: ProxyMode = ProxyMode.GLOBAL_HTTP_PROXY
        ) : UiState()

        data class Disconnected(
            override val addressState: TextFieldState,
            override val portState: TextFieldState,
            val pastProxies: List<Proxy>
        ) : UiState()
    }

    data class TextFieldState(
        val text: String,
        @StringRes val error: Int? = null,
        val forceFocus: Boolean = false
    )

    sealed class UserInteraction {
        object ToggleProxyClicked : UserInteraction()
        object SwitchThemeClicked : UserInteraction()
        data class AddressChanged(val newAddress: String) : UserInteraction()
        data class PortChanged(val newPort: String) : UserInteraction()
        data class ProxyFromDropDownSelected(val proxy: Proxy) : UserInteraction()
        object ShowProfileSelector : UserInteraction()
        object HideProfileSelector : UserInteraction()
        data class ProxyModeChanged(val mode: ProxyMode) : UserInteraction()
        data class ConnectWithProfile(val profile: ProxyProfile) : UserInteraction()
        data class SaveAsProfile(val name: String) : UserInteraction()
    }

    private sealed class ProxyManagerError {
        object InvalidAddress : ProxyManagerError()
        object InvalidPort : ProxyManagerError()
        object NoError : ProxyManagerError()
    }

    companion object {
        // NOTE: necessary delay to refocus & announce existing error on next attempt to connect!
        private const val ERROR_DELAY = 50L
    }
}
