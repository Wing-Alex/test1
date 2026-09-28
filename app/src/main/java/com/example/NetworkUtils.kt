package com.example

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    data class NetworkIpInfo(
        val interfaceName: String,
        val ipAddress: String,
        val isLikelyHotspot: Boolean
    )

    /**
     * Finds all active IPv4 addresses on device network interfaces.
     * Hotspot interfaces on Android typically include 'ap0', 'softap0', 'wlan', or 'rndis'.
     * The standard Android Hotspot gateway IP is 192.168.43.1.
     */
    fun getLocalIPv4Addresses(): List<NetworkIpInfo> {
        val list = mutableListOf<NetworkIpInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        val name = intf.name.lowercase()
                        val isHotspot = name.contains("ap") ||
                                name.contains("softap") ||
                                hostAddress == "192.168.43.1" ||
                                hostAddress.startsWith("192.168.43.")
                        list.add(
                            NetworkIpInfo(
                                interfaceName = intf.name,
                                ipAddress = hostAddress,
                                isLikelyHotspot = isHotspot
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Returns best guess for Server IP (Hotspot IP preferred, then Wi-Fi, fallback 192.168.43.1).
     */
    fun getPrimaryIp(): String {
        val ips = getLocalIPv4Addresses()
        val hotspot = ips.firstOrNull { it.isLikelyHotspot }
        if (hotspot != null) return hotspot.ipAddress
        val wifi = ips.firstOrNull { it.interfaceName.lowercase().contains("wlan") }
        if (wifi != null) return wifi.ipAddress
        return ips.firstOrNull()?.ipAddress ?: "192.168.43.1"
    }
}
