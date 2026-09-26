package sealmc.swe3tie.sealcore.gui

import org.bukkit.entity.Player
import sealmc.swe3tie.sealcore.economy.CurrencyKey
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine
import java.util.UUID

/**
 * Everything a screen may need while it renders or handles a click.
 *
 * Screens never reach for the plugin singleton directly, which keeps them
 * testable with plain fakes.
 */
class GuiContext(
    val player: Player,
    val playerId: UUID,
    val session: GuiSession,
    val placeholders: PlaceholderEngine?,
    val scheduler: (Runnable) -> Unit,
) {

    fun placeholder(key: String): String = placeholders?.resolve(player, key) ?: key

    fun component(text: String, vararg pairs: Pair<String, String>): net.kyori.adventure.text.Component =
        sealmc.swe3tie.sealcore.text.Text.render(text, *pairs)

    fun balance(currency: CurrencyKey): Double =
        placeholders?.cachedBalance(playerId, currency) ?: 0.0

    /** Runs [task] on the thread owning the player. */
    fun onPlayerThread(task: () -> Unit) {
        scheduler(Runnable { task() })
    }
}
