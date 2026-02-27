package com.kinandcarta.create.proxytoggle.repository.proxymode

import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing proxy mode preference
 */
interface ProxyModeRepository {
    /**
     * Flow of the current proxy mode setting
     */
    val proxyMode: Flow<ProxyMode>

    /**
     * Set the proxy mode
     */
    suspend fun setProxyMode(mode: ProxyMode)
}
