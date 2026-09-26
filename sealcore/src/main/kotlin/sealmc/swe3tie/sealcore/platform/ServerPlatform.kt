package sealmc.swe3tie.sealcore.platform

import org.bukkit.Bukkit

/**
 * The server implementation SealCore is running on.
 *
 * SealCore only ever talks to the regionized schedulers, which Paper implements
 * as well, so the same code paths serve both platforms. The distinction is kept
 * for logging, for feature gating and for the one place where behaviour really
 * does differ (threaded access to shared state).
 */
enum class ServerPlatform(val displayName: String) {
    PAPER("Paper"),
    FOLIA("Folia"),
    UNKNOWN("Unknown");

    val isFolia: Boolean get() = this == FOLIA

    /** True when the regionized scheduler API can be used. */
    val supportsRegions: Boolean get() = this == PAPER || this == FOLIA

    companion object {
        fun detect(): ServerPlatform {
            val brand = runCatching { Bukkit.getName() }.getOrNull()?.lowercase() ?: return UNKNOWN
            return when {
                brand.contains("folia") -> FOLIA
                brand.contains("paper") || brand.contains("purpur") -> PAPER
                else -> UNKNOWN
            }
        }
    }
}
