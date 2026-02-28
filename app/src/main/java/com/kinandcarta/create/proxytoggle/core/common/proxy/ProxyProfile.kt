package com.kinandcarta.create.proxytoggle.core.common.proxy

import java.util.UUID

/**
 * Represents a saved proxy profile configuration
 */
data class ProxyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val port: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
) {
    val proxy: Proxy
        get() = Proxy(address, port)

    val isValid: Boolean
        get() = name.isNotBlank() && address.isNotBlank() && port.isNotBlank()

    companion object {
        val Empty = ProxyProfile(
            id = "",
            name = "",
            address = "",
            port = ""
        )
    }
}
