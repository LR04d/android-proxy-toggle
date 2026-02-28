package com.kinandcarta.create.proxytoggle.repository.userprefs

import androidx.datastore.core.DataStore
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kinandcarta.create.proxytoggle.datastore.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryImplTest {

    private lateinit var fakeDataStore: FakeUserPreferencesStore
    private lateinit var subject: UserPreferencesRepositoryImpl

    @Before
    fun setUp() {
        fakeDataStore = FakeUserPreferencesStore()
        subject = UserPreferencesRepositoryImpl(fakeDataStore)
    }

    @Test
    fun `isNightMode - when dataStore LIGHT then false`() = runTest {
        fakeDataStore.updateData { userPrefsWithTheme(UserPreferences.ThemeMode.LIGHT) }
        subject.isNightMode.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `isNightMode - when dataStore DARK then true`() = runTest {
        fakeDataStore.updateData { userPrefsWithTheme(UserPreferences.ThemeMode.DARK) }
        subject.isNightMode.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `isNightMode - when dataStore UNSPECIFIED then false`() = runTest {
        fakeDataStore.updateData { userPrefsWithTheme(UserPreferences.ThemeMode.UNSPECIFIED) }
        subject.isNightMode.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `GIVEN dataStore LIGHT, WHEN toggleTheme(), THEN isNightMode becomes true`() = runTest {
        fakeDataStore.updateData { userPrefsWithTheme(UserPreferences.ThemeMode.LIGHT) }
        subject.toggleTheme()
        subject.isNightMode.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `GIVEN dataStore DARK, WHEN toggleTheme(), THEN isNightMode becomes false`() = runTest {
        fakeDataStore.updateData { userPrefsWithTheme(UserPreferences.ThemeMode.DARK) }
        subject.toggleTheme()
        subject.isNightMode.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `GIVEN dataStore UNSPECIFIED, WHEN toggleTheme(), THEN isNightMode becomes true`() = runTest {
        fakeDataStore.updateData { userPrefsWithTheme(UserPreferences.ThemeMode.UNSPECIFIED) }
        subject.toggleTheme()
        subject.isNightMode.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    private fun userPrefsWithTheme(themeMode: UserPreferences.ThemeMode): UserPreferences {
        return UserPreferences.newBuilder()
            .setThemeMode(themeMode)
            .build()
    }

    /**
     * In-memory DataStore implementation that avoids file system operations.
     * Works reliably on all platforms including Windows.
     */
    private class FakeUserPreferencesStore : DataStore<UserPreferences> {
        private val state = MutableStateFlow(UserPreferences.getDefaultInstance())

        override val data = state

        override suspend fun updateData(
            transform: suspend (t: UserPreferences) -> UserPreferences
        ): UserPreferences {
            val newData = transform(state.value)
            state.value = newData
            return newData
        }
    }
}
