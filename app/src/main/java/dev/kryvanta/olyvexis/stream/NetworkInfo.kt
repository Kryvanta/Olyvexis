package dev.kryvanta.olyvexis.stream

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfo {
    /** Tiền tố tên giao diện mạng nội bộ: Wi-Fi, điểm phát Wi-Fi, chia sẻ qua USB/Ethernet. */
    private val LAN_PREFIXES = listOf("wlan", "swlan", "ap", "softap", "rndis", "usb", "eth", "ncm")

    /**
     * Các IPv4 trong mạng nội bộ mà laptop/TV có thể kết nối tới, Wi-Fi đứng đầu.
     *
     * Không lấy theo "mạng đang hoạt động", vì khi bật VPN thì đó là VPN. Có cả điểm phát
     * Wi-Fi của chính điện thoại: laptop bắt Wi-Fi của điện thoại là cách dùng phổ biến khi
     * không có router, và giao diện đó (ap0/swlan0) không hiện trong ConnectivityManager.
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
