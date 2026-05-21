package kernel.http.server

import java.net.Inet4Address
import java.net.NetworkInterface

object HttpServerAddressResolver {
    fun resolveAccessibleUrls(bindHost: String, port: Int): List<String> {
        val normalizedHost = bindHost.trim().ifBlank { "0.0.0.0" }

        val hosts = when (normalizedHost) {
            "0.0.0.0", "::", "::0" -> listOf("localhost") + localIpv4Hosts()
            else -> listOf(normalizedHost)
        }

        return hosts
            .map { host -> "http://$host:$port" }
            .distinct()
    }

    private fun localIpv4Hosts(): List<String> {
        return NetworkInterface.getNetworkInterfaces()
            .toList()
            .asSequence()
            .filter { network ->
                runCatching { network.isUp && !network.isLoopback && !network.isVirtual }.getOrDefault(false)
            }
            .flatMap { network ->
                network.inetAddresses.toList().asSequence()
            }
            .filterIsInstance<Inet4Address>()
            .map { address -> address.hostAddress }
            .filter { address ->
                address.isNotBlank() && address != "127.0.0.1"
            }
            .toList()
    }
}
