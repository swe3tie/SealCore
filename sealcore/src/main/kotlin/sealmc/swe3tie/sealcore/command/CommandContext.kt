package sealmc.swe3tie.sealcore.command

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import sealmc.swe3tie.sealcore.text.Text

/** Everything a command handler needs, plus typed argument access. */
class CommandContext(
    val sender: CommandSender,
    val label: String,
    val args: List<String>,
) {

    val isPlayer: Boolean get() = sender is Player

    val player: Player? get() = sender as? Player

    fun arg(index: Int): String? = args.getOrNull(index)

    fun string(index: Int, fallback: String = ""): String = arg(index) ?: fallback

    fun int(index: Int): Int? = arg(index)?.toIntOrNull()

    fun long(index: Int): Long? = arg(index)?.toLongOrNull()

    fun double(index: Int): Double? = arg(index)?.toDoubleOrNull()

    fun join(from: Int = 0): String = args.drop(from).joinToString(" ")

    fun reply(message: Component) {
        sender.sendMessage(message)
    }

    fun reply(text: String) {
        sender.sendMessage(Text.parse(text))
    }

    fun replySuccess(text: String) {
        reply("<green>$text")
    }

    fun replyError(text: String) {
        reply("<red>$text")
    }
}

/** What a command node does when it is reached. */
fun interface CommandHandler {
    fun handle(context: CommandContext)
}
