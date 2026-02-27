package com.kinandcarta.create.proxytoggle.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyMode
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyProfile
import com.kinandcarta.create.proxytoggle.repository.devicesettings.DeviceSettingsManager
import com.kinandcarta.create.proxytoggle.repository.profile.ProfileRepository
import com.kinandcarta.create.proxytoggle.repository.proxymode.ProxyModeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val proxyModeRepository: ProxyModeRepository,
    private val deviceSettingsManager: DeviceSettingsManager
) : ViewModel() {

    val profiles: Flow<List<ProxyProfile>> = profileRepository.profiles

    val selectedProfileId: Flow<String?> = profileRepository.selectedProfileId

    val selectedProfile: Flow<ProxyProfile?> = profileRepository.selectedProfile

    val proxyMode: Flow<ProxyMode> = proxyModeRepository.proxyMode

    private val _editingProfile = MutableStateFlow<ProxyProfile?>(null)
    val editingProfile: StateFlow<ProxyProfile?> = _editingProfile.asStateFlow()

    private val _uiEvent = MutableStateFlow<UiEvent?>(null)
    val uiEvent: StateFlow<UiEvent?> = _uiEvent.asStateFlow()

    fun saveProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            profileRepository.saveProfile(profile)
            _uiEvent.value = UiEvent.ProfileSaved
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
            _uiEvent.value = UiEvent.ProfileDeleted
        }
    }

    fun selectProfile(profileId: String?) {
        viewModelScope.launch {
            profileRepository.selectProfile(profileId)
        }
    }

    fun connectWithProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            val mode = proxyModeRepository.proxyMode.first()
            profileRepository.selectProfile(profile.id)
            profileRepository.updateLastUsed(profile.id)
            deviceSettingsManager.enableProxy(profile.proxy, mode)
        }
    }

    fun setProxyMode(mode: ProxyMode) {
        viewModelScope.launch {
            proxyModeRepository.setProxyMode(mode)
        }
    }

    fun setEditingProfile(profile: ProxyProfile?) {
        _editingProfile.value = profile
    }

    fun clearUiEvent() {
        _uiEvent.value = null
    }

    sealed class UiEvent {
        object ProfileSaved : UiEvent()
        object ProfileDeleted : UiEvent()
    }
}
