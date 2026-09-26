package sealmc.swe3tie.sealcore.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/**
 * Exposes the same `%prefix_key%` tokens through PlaceholderAPI.
 *
 * Registered only when PlaceholderAPI is installed, and unregistered on
 * disable so a reload does not leave a stale expansion behind.
 */
class SealCoreExpansion(
    private val plugin: org.bukkit.plugin.Plugin,
    private val engine: PlaceholderEngine,
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "sealcore"

    override fun getAuthor(): String = plugin.pluginMeta.authors.joinToString(", ").ifEmpty { "sealmc.swe3tie" }

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(
        offlinePlayer: OfflinePlayer?,
        params: String,
    ): String? = engine.resolve(offlinePlayer, "%${params.lowercase()}%").takeIf { !it.startsWith("%") }

    override fun onPlaceholderRequest(player: Player?, params: String): String? =
        onRequest(player, params)
}
