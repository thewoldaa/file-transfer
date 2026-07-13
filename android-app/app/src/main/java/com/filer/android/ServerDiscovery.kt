package com.filer.android

import android.content.Context
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServerDiscovery(private val context: Context) {

    private val port = 5000

    suspend fun scan(): String = withContext(Dispatchers.IO) {
        val subnet = getSubnet() ?: return@withContext ""
        val candidates = (1..254).map { "$subnet.$it" }

        for (ip in candidates) {
            try {
                val addr = InetAddress.getByName(ip)
                if (addr.isReachable(500)) {
                    val url = URL("http://$ip:$port/")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 300
                    conn.readTimeout = 300
                    val code = conn.responseCode
                    if (code == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        if (body.contains("FileTransferServer", ignoreCase = true)) {
                            return@withContext "http://$ip:$port"
                        }
                    }
                    conn.disconnect()
                }
            } catch (_: Exception) { }
        }
        ""
    }

    private fun getSubnet(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val host = addr.hostAddress ?: continue
                    if (host.contains(".") && !host.startsWith("127.") && !host.contains(":")) {
                        val parts = host.split(".")
                        if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
                    }
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
