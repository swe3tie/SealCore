package sealmc.swe3tie.sealcore.placeholder

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import sealmc.swe3tie.sealcore.economy.CurrencyKey
import sealmc.swe3tie.sealcore.economy.EconomyService
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves `%prefix_key%` tokens inside GUI titles, HUD lines and messages.
 *
 * Registrations are prefix based, so a feature module adds its own namespace
 * without touching the engine. Lookups are synchronous, so a resolver must not
 * call a blocking API: currency values come from a short lived cache that the
 * framework refreshes off the server thread.
 */
class PlaceholderEngine(private val economy: EconomyService?) {

    private val resolvers = ConcurrentHashMap<String, Resolver>()

    /** Cached balances so a HUD tick never blocks on a storage round trip. */
    private val balanceCache = ConcurrentHashMap<CacheKey, Double>()

    private data class CacheKey(val playerId: UUID, val currency: CurrencyKey)

    fun interface Resolver {
        /** Returns null when the key is unknown, so the engine can try another prefix. */
        fun resolve(viewer: OfflinePlayer?, key: String): String?
    }

    fun register(prefix: String, resolver: Resolver) {
        resolvers[prefix.lowercase(Locale.ROOT)] = resolver
    }

    fun unregister(prefix: String) {
        resolvers.remove(prefix.lowercase(Locale.ROOT))
    }

    fun prefixes(): Set<String> = resolvers.keys

    fun resolve(viewer: OfflinePlayer?, text: String): String {
        if (text.isEmpty() || !text.contains('%')) return text
        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val start = text.indexOf('%', index)
            if (start < 0) {
                result.append(text, index, text.length)
                break
            }
            val end = text.indexOf('%', start + 1)
            if (end < 0) {
                result.append(text, index, text.length)
                break
            }
            result.append(text, index, start)
            val token = text.substring(start + 1, end)
            result.append(resolveToken(viewer, token))
            index = end + 1
        }
        return result.toString()
    }

    private fun resolveToken(viewer: OfflinePlayer?, token: String): String {
        if (token.isEmpty()) return "%"
        val separator = token.indexOf('_')
        if (separator <= 0) return "%$token%"
        val prefix = token.substring(0, separator).lowercase(Locale.ROOT)
        val key = token.substring(separator + 1)
        val resolver = resolvers[prefix] ?: return "%$token%"
        return runCatching { resolver.resolve(viewer, key) }.getOrNull() ?: "%$token%"
    }

    // --- built in placeholders -------------------------------------------------

    fun installBuiltins() {
        register("player") { viewer, key ->
            when (key.lowercase(Locale.ROOT)) {
                "name" -> viewer?.name ?: "unknown"
                "uuid" -> viewer?.uniqueId?.toString() ?: "unknown"
                "world" -> viewer?.player?.world?.name ?: "unknown"
                "x" -> viewer?.location?.blockX?.toString() ?: "0"
                "y" -> viewer?.location?.blockY?.toString() ?: "0"
                "z" -> viewer?.location?.blockZ?.toString() ?: "0"
                "health" -> (viewer?.player?.health ?: 0.0).toString()
                "food" -> (viewer?.player?.foodLevel ?: 0).toString()
                "ping" -> viewer?.player?.ping?.toString() ?: "0"
                "online" -> viewer?.isOnline?.toString() ?: "false"
                else -> null
            }
        }
        register("server") { _, key ->
            when (key.lowercase(Locale.ROOT)) {
                "online" -> Bukkit.getOnlinePlayers().size.toString()
                "max" -> Bukkit.getMaxPlayers().toString()
                "version" -> Bukkit.getMinecraftVersion()
                "brand" -> Bukkit.getName()
                else -> null
            }
        }
        register("sealcore") { viewer, key ->
            when (key.lowercase(Locale.ROOT)) {
                "money", "shards", "coins" -> viewer?.let { format(it, CurrencyKey.of(key)) } ?: "0"
                else -> null
            }
        }
    }

    fun format(viewer: OfflinePlayer, currency: CurrencyKey): String {
        val service = economy ?: return "0"
        val balance = cachedBalance(viewer.uniqueId, currency)
        return runCatching { service.format(currency, balance) }.getOrDefault(balance.toString())
    }

    /**
     * Balance as of the last refresh. Returns the stale value rather than
     * blocking, which is the right trade-off for anything rendered per tick.
     */
    fun cachedBalance(playerId: UUID, currency: CurrencyKey): Double =
        balanceCache[CacheKey(playerId, currency)] ?: 0.0

    /** Refreshes one balance off the server thread. Never call from a render path. */
    suspend fun refreshBalance(playerId: UUID, currency: CurrencyKey) {
        val service = economy ?: return
        runCatching { service.balance(playerId, currency) }
            .onSuccess { balanceCache[CacheKey(playerId, currency)] = it }
    }

    suspend fun refreshAllBalances(currencies: List<CurrencyKey>) {
        for (player in Bukkit.getOnlinePlayers()) {
            for (currency in currencies) {
                refreshBalance(player.uniqueId, currency)
            }
        }
    }

    fun invalidate(playerId: UUID) {
        balanceCache.keys.removeIf { it.playerId == playerId }
    }

    fun clearCache() {
        balanceCache.clear()
    }
}
