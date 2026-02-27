package com.kinandcarta.create.proxytoggle.repository.profile

import androidx.datastore.core.DataStore
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyProfile
import com.kinandcarta.create.proxytoggle.datastore.AppData
import com.kinandcarta.create.proxytoggle.datastore.ProxyProfileProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<AppData>
) : ProfileRepository {

    private val appData: Flow<AppData> by lazy {
        dataStore.data.catch { exception ->
            if (exception is IOException) {
                emit(AppData.getDefaultInstance())
            } else {
                throw exception
            }
        }
    }

    override val profiles: Flow<List<ProxyProfile>> by lazy {
        appData.map { data ->
            data.profilesList.map { it.toDomain() }
                .sortedByDescending { it.lastUsedAt }
        }.distinctUntilChanged()
    }

    override val selectedProfileId: Flow<String?> by lazy {
        appData.map { data ->
            data.selectedProfileId.takeIf { it.isNotEmpty() }
        }.distinctUntilChanged()
    }

    override val selectedProfile: Flow<ProxyProfile?> by lazy {
        appData.map { data ->
            val selectedId = data.selectedProfileId
            if (selectedId.isNotEmpty()) {
                data.profilesList.find { it.id == selectedId }?.toDomain()
            } else {
                null
            }
        }.distinctUntilChanged()
    }

    override suspend fun saveProfile(profile: ProxyProfile) {
        dataStore.updateData { data ->
            val existingIndex = data.profilesList.indexOfFirst { it.id == profile.id }
            val updatedProfiles = data.profilesList.toMutableList()

            if (existingIndex >= 0) {
                updatedProfiles[existingIndex] = profile.toProto()
            } else {
                updatedProfiles.add(profile.toProto())
            }

            data.toBuilder()
                .clearProfiles()
                .addAllProfiles(updatedProfiles)
                .build()
        }
    }

    override suspend fun deleteProfile(profileId: String) {
        dataStore.updateData { data ->
            val updatedProfiles = data.profilesList.filter { it.id != profileId }
            val builder = data.toBuilder()
                .clearProfiles()
                .addAllProfiles(updatedProfiles)

            // Clear selected profile if it was deleted
            if (data.selectedProfileId == profileId) {
                builder.clearSelectedProfileId()
            }

            builder.build()
        }
    }

    override suspend fun getProfile(profileId: String): ProxyProfile? {
        return appData.first().profilesList
            .find { it.id == profileId }
            ?.toDomain()
    }

    override suspend fun selectProfile(profileId: String?) {
        dataStore.updateData { data ->
            val builder = data.toBuilder()
            if (profileId != null) {
                builder.selectedProfileId = profileId
            } else {
                builder.clearSelectedProfileId()
            }
            builder.build()
        }
    }

    override suspend fun updateLastUsed(profileId: String) {
        dataStore.updateData { data ->
            val updatedProfiles = data.profilesList.map { profile ->
                if (profile.id == profileId) {
                    profile.toBuilder()
                        .setLastUsedAt(System.currentTimeMillis())
                        .build()
                } else {
                    profile
                }
            }
            data.toBuilder()
                .clearProfiles()
                .addAllProfiles(updatedProfiles)
                .build()
        }
    }

    private fun ProxyProfileProto.toDomain(): ProxyProfile {
        return ProxyProfile(
            id = id,
            name = name,
            address = address,
            port = port,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt
        )
    }

    private fun ProxyProfile.toProto(): ProxyProfileProto {
        return ProxyProfileProto.newBuilder()
            .setId(id)
            .setName(name)
            .setAddress(address)
            .setPort(port)
            .setCreatedAt(createdAt)
            .setLastUsedAt(lastUsedAt)
            .build()
    }
}
