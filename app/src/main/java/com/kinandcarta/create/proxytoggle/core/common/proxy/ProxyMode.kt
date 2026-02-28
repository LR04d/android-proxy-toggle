package com.kinandcarta.create.proxytoggle.core.common.proxy

/**
 * Represents the mode in which proxy is applied
 */
enum class ProxyMode {
    /**
     * Uses system-wide HTTP_PROXY setting via Settings.Global
     * Requires WRITE_SECURE_SETTINGS permission
     */
    GLOBAL_HTTP_PROXY,

    /**
     * Uses VPN service to route traffic through proxy
     * Requires user approval for VPN connection
     */
    VPN
}
