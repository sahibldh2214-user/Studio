package com.example.data.remote

import com.example.model.DiscoveredLanHost
import com.example.model.LocalNetworkSummary
import com.example.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.apache.commons.net.DefaultSocketFactory
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.util.SubnetUtils
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Local Area Network (LAN) device & file-share discovery utility powered by Apache Commons Net
 * ([SubnetUtils], [DefaultSocketFactory], and [FTPClient]).
 */
class LanNetworkScanner {

    private val socketFactory = DefaultSocketFactory()

    data class LocalInterfaceAddress(
        val interfaceName: String,
        val ipAddress: String,
        val prefixLength: Int
    )

    fun getLocalNetworkSummary(): LocalNetworkSummary {
        val local = findLocalIpv4Interface() ?: return LocalNetworkSummary()
        val cidr = computeScanCidr(local.ipAddress, local.prefixLength)
        val addresses = enumerateSubnetHosts(cidr, excludeIp = local.ipAddress)
        return LocalNetworkSummary(
            interfaceName = local.interfaceName,
            localIp = local.ipAddress,
            subnetCidr = cidr,
            hostCount = addresses.size
        )
    }

    suspend fun scanLocalNetwork(
        portTimeoutMs: Int = 180
    ): Pair<LocalNetworkSummary, List<DiscoveredLanHost>> = withContext(Dispatchers.IO) {
        val local = findLocalIpv4Interface()
            ?: return@withContext LocalNetworkSummary() to emptyList()

        val cidr = computeScanCidr(local.ipAddress, local.prefixLength)
        val candidateIps = enumerateSubnetHosts(cidr, excludeIp = local.ipAddress)
        val summary = LocalNetworkSummary(
            interfaceName = local.interfaceName,
            localIp = local.ipAddress,
            subnetCidr = cidr,
            hostCount = candidateIps.size
        )

        val targetPorts = listOf(445, 21, 2121, 8080, 5005, 80)

        val discovered = coroutineScope {
            candidateIps.chunked(32).flatMap { batch ->
                batch.map { ip ->
                    async {
                        probeHost(ip, targetPorts, portTimeoutMs)
                    }
                }.awaitAll().filterNotNull()
            }
        }.sortedWith(compareBy<DiscoveredLanHost> { it.latencyMs }.thenBy { it.ipAddress })

        summary to discovered
    }

    private fun probeHost(
        ip: String,
        targetPorts: List<Int>,
        timeoutMs: Int
    ): DiscoveredLanHost? {
        val openPorts = mutableListOf<Int>()
        var fastestLatency = Long.MAX_VALUE

        for (port in targetPorts) {
            val startNs = System.nanoTime()
            val reachable = runCatching {
                socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    true
                }
            }.getOrDefault(false)

            if (reachable) {
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                if (elapsedMs < fastestLatency) fastestLatency = elapsedMs
                openPorts.add(port)
            }
        }

        if (openPorts.isEmpty()) return null

        val resolvedHost = runCatching {
            val addr = InetAddress.getByName(ip)
            val canonical = addr.canonicalHostName
            if (canonical.isNullOrBlank() || canonical == ip) ip else canonical
        }.getOrDefault(ip)

        val ftpPort = openPorts.firstOrNull { it == 21 || it == 2121 }
        val ftpProbe = if (ftpPort != null) probeFtpService(ip, ftpPort) else null

        val suggestedProtocol = when {
            445 in openPorts -> SourceType.SMB
            ftpPort != null -> SourceType.FTP
            else -> SourceType.HTTP_WEBDAV
        }

        val bannerParts = mutableListOf<String>()
        if (445 in openPorts) bannerParts.add("SMB/CIFS (:445)")
        if (ftpProbe != null) {
            val guestTag = if (ftpProbe.anonymousAllowed) " · Guest Ready" else ""
            bannerParts.add("FTP (:$ftpPort$guestTag)")
        } else if (ftpPort != null) {
            bannerParts.add("FTP (:$ftpPort)")
        }
        val httpPorts = openPorts.filter { it in listOf(8080, 5005, 80) }
        if (httpPorts.isNotEmpty()) {
            bannerParts.add("WebDAV/HTTP (${httpPorts.joinToString(", ") { ":$it" }})")
        }
        if (ftpProbe?.systemBanner?.isNotBlank() == true) {
            bannerParts.add(ftpProbe.systemBanner)
        }

        return DiscoveredLanHost(
            ipAddress = ip,
            hostname = resolvedHost,
            openPorts = openPorts,
            suggestedProtocol = suggestedProtocol,
            serviceBanner = bannerParts.joinToString(" · "),
            supportsAnonymousFtp = ftpProbe?.anonymousAllowed == true,
            latencyMs = if (fastestLatency == Long.MAX_VALUE) 1L else fastestLatency
        )
    }

    private data class FtpProbeResult(
        val anonymousAllowed: Boolean,
        val systemBanner: String
    )

    private fun probeFtpService(host: String, port: Int): FtpProbeResult? {
        val client = FTPClient().apply {
            connectTimeout = 900
            defaultTimeout = 1200
        }
        return try {
            client.connect(host, port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                return null
            }
            val welcome = client.replyString
                ?.lineSequence()
                ?.firstOrNull()
                ?.removePrefix("220")
                ?.trim()
                ?.take(40)
                .orEmpty()

            val anonOk = runCatching {
                client.login("anonymous", "vaulttv@local")
            }.getOrDefault(false)

            val sysType = if (anonOk) {
                runCatching { client.systemType?.trim().orEmpty() }.getOrDefault("")
            } else ""

            val banner = listOf(welcome, sysType).filter { it.isNotBlank() }.distinct().joinToString(" / ")
            FtpProbeResult(anonymousAllowed = anonOk, systemBanner = banner)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    /**
     * Computes a scan CIDR using at most a /24 range (254 hosts) so large enterprise/VPN subnets
     * do not stall LAN scanning on TV.
     */
    fun computeScanCidr(ipAddress: String, prefixLength: Int): String {
        val safePrefix = if (prefixLength in 24..30) prefixLength else 24
        return "$ipAddress/$safePrefix"
    }

    /**
     * Uses Apache Commons Net [SubnetUtils] to enumerate all host IPv4 addresses inside [cidr].
     */
    fun enumerateSubnetHosts(cidr: String, excludeIp: String? = null): List<String> {
        return runCatching {
            val utils = SubnetUtils(cidr).apply { isInclusiveHostCount = false }
            utils.info.allAddresses.filter { it != excludeIp }
        }.getOrElse {
            val prefix = cidr.substringBefore('/').substringBeforeLast('.')
            if (prefix.count { it == '.' } == 2) {
                (1..254).map { "$prefix.$it" }.filter { it != excludeIp }
            } else {
                emptyList()
            }
        }
    }

    fun findLocalIpv4Interface(): LocalInterfaceAddress? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        val candidates = mutableListOf<LocalInterfaceAddress>()

        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
            for (ifaceAddr in iface.interfaceAddresses) {
                val addr = ifaceAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val hostIp = addr.hostAddress ?: continue
                    candidates.add(
                        LocalInterfaceAddress(
                            interfaceName = iface.displayName ?: iface.name,
                            ipAddress = hostIp,
                            prefixLength = ifaceAddr.networkPrefixLength.toInt()
                        )
                    )
                }
            }
        }

        // Prefer Ethernet / Wi-Fi private LAN interfaces first
        return candidates.firstOrNull {
            it.ipAddress.startsWith("192.168.") || it.ipAddress.startsWith("10.") || it.ipAddress.startsWith("172.")
        } ?: candidates.firstOrNull()
    }
}
