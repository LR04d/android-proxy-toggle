package com.kinandcarta.create.proxytoggle.repository.profile

import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing proxy profiles
 */
interface ProfileRepository {
    /**
     * Flow of all saved profiles
     */
    val profiles: Flow<List<ProxyProfile>>

    /**
     * Flow of the currently selected profile
     */
    val selectedProfile: Flow<ProxyProfile?>

    /**
     * Flow of the selected profile ID
     */
    val selectedProfileId: Flow<String?>

    /**
     * Save a new profile or update an existing one
     */
    suspend fun saveProfile(profile: ProxyProfile)

    /**
     * Delete a profile by ID
     */
    suspend fun deleteProfile(profileId: String)

    /**
     * Get a profile by ID
     */
    suspend fun getProfile(profileId: String): ProxyProfile?

    /**
     * Set the selected profile
     */
    suspend fun selectProfile(profileId: String?)

    /**
     * Update the last used timestamp for a profile
     */
    suspend fun updateLastUsed(profileId: String)
}
