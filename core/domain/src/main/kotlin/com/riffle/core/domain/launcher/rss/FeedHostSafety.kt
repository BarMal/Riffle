package com.riffle.core.domain.launcher.rss

/**
 * Cheap, literal-only host screening for feed URLs: rejects local names and private, loopback, link-local
 * and other non-public address literals. It does not resolve DNS, so it is a guard rail, not a sandbox.
 */
object FeedHostSafety {
    private const val IPV4_PARTS = 4
    private const val MAX_OCTET = 255
    private const val CGNAT_FIRST = 64
    private const val CGNAT_LAST = 127
    private const val LINK_LOCAL_FIRST = 169
    private const val LINK_LOCAL_SECOND = 254
    private const val PRIVATE_172_FIRST = 16
    private const val PRIVATE_172_LAST = 31
    private const val PRIVATE_192_SECOND = 168
    private const val MULTICAST_FIRST = 224

    private val localSuffixes = listOf(".localhost", ".local", ".internal", ".lan", ".home", ".localdomain")

    fun isPublicHost(host: String): Boolean {
        val name = host.trim().trimEnd('.').lowercase()
        return when {
            name.isEmpty() -> false
            name.startsWith("[") || ':' in name -> false
            name == "localhost" || localSuffixes.any(name::endsWith) -> false
            looksLikeIpv4(name) -> isPublicIpv4(name)
            name.all { it.isDigit() || it == '.' } -> false
            else -> '.' in name
        }
    }

    private fun looksLikeIpv4(name: String): Boolean {
        val parts = name.split('.')
        return parts.size == IPV4_PARTS && parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) }
    }

    private fun isPublicIpv4(name: String): Boolean {
        val octets = name.split('.').map { it.toIntOrNull() ?: -1 }
        val first = octets[0]
        val second = octets[1]
        val nonPublic =
            octets.any { it !in 0..MAX_OCTET } ||
                first == 0 || first == 10 || first == 127 || first >= MULTICAST_FIRST ||
                (first == 100 && second in CGNAT_FIRST..CGNAT_LAST) ||
                (first == LINK_LOCAL_FIRST && second == LINK_LOCAL_SECOND) ||
                (first == 172 && second in PRIVATE_172_FIRST..PRIVATE_172_LAST) ||
                (first == 192 && second == PRIVATE_192_SECOND)
        return !nonPublic
    }
}
