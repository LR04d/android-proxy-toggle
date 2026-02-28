package com.kinandcarta.create.proxytoggle.repository.appdata

import androidx.datastore.core.DataStore
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kinandcarta.create.proxytoggle.core.common.proxy.Proxy
import com.kinandcarta.create.proxytoggle.datastore.AppData
import com.kinandcarta.create.proxytoggle.datastore.PastProxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AppDataRepositoryImplTest {

    private lateinit var fakeDataStore: FakeAppDataStore
    private lateinit var subject: AppDataRepositoryImpl

    @Before
    fun setUp() {
        fakeDataStore = FakeAppDataStore()
        subject = AppDataRepositoryImpl(fakeDataStore)
    }

    @Test
    fun `GIVEN 0 past proxies, WHEN saveProxy(), THEN pastProxies returns that proxy`() = runTest {
        val newProxy = testProxies.first().toProxy()
        subject.saveProxy(newProxy)
        subject.pastProxies.test {
            assertThat(awaitItem()).isEqualTo(listOf(newProxy))
        }
    }

    @Test
    fun `GIVEN 4 past proxies, WHEN I save new proxy, THEN pastProxies = previous 4 + new, time-sorted`() =
        runTest {
            val pastProxies = testProxies.take(4)
            val newProxy = testProxies.last().toProxy()
            fakeDataStore.updateData { appDataWithProxies(pastProxies) }
            subject.saveProxy(newProxy)
            subject.pastProxies.test {
                assertThat(awaitItem()).isEqualTo(
                    listOf(newProxy) +
                        pastProxies.sortedByDescending { it.timestamp }.map { it.toProxy() }
                )
            }
        }

    @Test
    fun `GIVEN 5 past proxies, WHEN I save new proxy, THEN pastProxies = 4 most recent previous + new, time-sorted`() =
        runTest {
            val newProxy = Proxy("42.42.42.42", "42")
            fakeDataStore.updateData { appDataWithProxies(testProxies) }
            subject.saveProxy(newProxy)
            subject.pastProxies.test {
                assertThat(awaitItem()).isEqualTo(
                    listOf(newProxy) +
                        testProxies.sortedByDescending { it.timestamp }.take(4).map { it.toProxy() }
                )
            }
        }

    @Test
    fun `GIVEN 5 past proxies, WHEN I save existing proxy, THEN pastProxies = all previous, time-sorted`() =
        runTest {
            val newProxy = testProxies.last().toProxy()
            fakeDataStore.updateData { appDataWithProxies(testProxies) }
            subject.saveProxy(newProxy)
            subject.pastProxies.test {
                assertThat(awaitItem()).isEqualTo(
                    listOf(newProxy) +
                        testProxies.take(4).sortedByDescending { it.timestamp }.map { it.toProxy() }
                )
            }
        }

    private fun appDataWithProxies(proxies: List<PastProxy>): AppData {
        return AppData.newBuilder()
            .addAllPastProxies(proxies.toMutableList())
            .build()
    }

    private val yesterday = Instant.now().minus(1, ChronoUnit.DAYS).epochSecond

    private val testProxies = listOf(
        pastProxy("1.1.1.1", "1", yesterday - 1),
        pastProxy("2.2.2.2", "2", yesterday + 2),
        pastProxy("3.3.3.3", "3", yesterday - 3),
        pastProxy("4.4.4.4", "4", yesterday + 4),
        pastProxy("5.5.5.5", "5", yesterday - 5)
    )

    private fun pastProxy(address: String, port: String, epochSecond: Long): PastProxy {
        return PastProxy.newBuilder()
            .setAddress(address)
            .setPort(port)
            .setTimestamp(epochSecond)
            .build()
    }

    private fun PastProxy.toProxy(): Proxy {
        return Proxy(this.address, this.port)
    }

    /**
     * In-memory DataStore implementation that avoids file system operations.
     * Works reliably on all platforms including Windows.
     */
    private class FakeAppDataStore : DataStore<AppData> {
        private val state = MutableStateFlow(AppData.getDefaultInstance())

        override val data = state

        override suspend fun updateData(transform: suspend (t: AppData) -> AppData): AppData {
            val newData = transform(state.value)
            state.value = newData
            return newData
        }
    }
}
