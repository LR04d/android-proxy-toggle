package com.kinandcarta.create.proxytoggle.repository.proxymode

import androidx.datastore.core.DataStore
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyMode
import com.kinandcarta.create.proxytoggle.datastore.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyModeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<AppData>
) : ProxyModeRepository {

    private val appData: Flow<AppData> by lazy {
        dataStore.data.catch { exception ->
            if (exception is IOException) {
                emit(AppData.getDefaultInstance())
            } else {
                throw exception
            }
        }
    }

    override val proxyMode: Flow<ProxyMode> by lazy {
        appData.map { data ->
            when (data.proxyMode) {
                PROXY_MODE_VPN -> ProxyMode.VPN
                else -> ProxyMode.GLOBAL_HTTP_PROXY
            }
        }.distinctUntilChanged()
    }

    override suspend fun setProxyMode(mode: ProxyMode) {
        dataStore.updateData { data ->
            val modeString = when (mode) {
                ProxyMode.VPN -> PROXY_MODE_VPN
                ProxyMode.GLOBAL_HTTP_PROXY -> PROXY_MODE_GLOBAL
            }
            data.toBuilder()
                .setProxyMode(modeString)
                .build()
        }
    }

    companion object {
        private const val PROXY_MODE_GLOBAL = "global"
        private const val PROXY_MODE_VPN = "vpn"
    }
}
