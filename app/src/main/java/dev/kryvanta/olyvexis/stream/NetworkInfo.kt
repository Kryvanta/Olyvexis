package dev.kryvanta.olyvexis.stream

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfo {
    /** Local network interface prefixes: Wi-Fi, hotspot, USB/Ethernet tethering. */
    private val LAN_PREFIXES = listOf("wlan", "swlan", "ap", "softap", "rndis", "usb", "eth", "ncm")

    /**
     * Local IPv4 addresses reachable from a laptop/TV, with Wi-Fi listed first.
     *
     * Avoids querying only the "active network" because VPNs mask the LAN interface. Includes the
     * phone's own Wi-Fi hotspot (ap0/swlan0), which ConnectivityManager omits.
     */
    @Suppress("DEPRECATION")
    fun localIpv4s(context: Context): List<String> {
        val result = linkedSetOf<String>()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) continue
            cm.getLinkProperties(network)?.linkAddresses
                ?.map { it.address }
                ?.filter { it is Inet4Address && !it.isLoopbackAddress }
                ?.forEach { result += it.hostAddress!! }
        }
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { nif -> nif.isUp && !nif.isLoopback && LAN_PREFIXES.any { nif.name.startsWith(it) } }
                .flatMap { it.inetAddresses.toList() }
                .filter { it is Inet4Address && it.isSiteLocalAddress }
                .forEach { result += it.hostAddress!! }
        }
        return result.toList()
    }
}
