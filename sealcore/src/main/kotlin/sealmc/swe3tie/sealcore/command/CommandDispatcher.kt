package sealmc.swe3tie.sealcore.command

import org.bukkit.command.CommandSender
import java.util.logging.Logger

/**
 * Walks the [CommandNode] tree for one invocation.
 *
 * Kept free of Bukkit's command API so the routing rules can be unit tested
 * with a fake sender; [SealCoreCommand] is the thin Bukkit adapter.
 */
class CommandDispatcher(
    private val root: CommandNode,
    private val logger: Logger,
) {

    fun dispatch(sender: CommandSender, label: String, args: List<String>) {
        val permitted: (CommandNode) -> Boolean = { node ->
            val allowed = node.permission == null || sender.hasPermission(node.permission)
            allowed && (!node.playerOnly || sender is org.bukkit.entity.Player)
        }

        if (!permitted(root)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("You do not have permission."))
            return
        }

        var node = root
        val path = mutableListOf(root)
        var index = 0
        while (index < args.size) {
            val child = node.findChild(args[index]) ?: break
            if (!permitted(child)) {
                sender.sendMessage(net.kyori.adventure.text.Component.text("You do not have permission."))
                return
            }
            node = child
            path += child
            index++
        }

        val remaining = args.drop(index)
        val handler = node.handler
        if (handler == null) {
            sendUsage(sender, node, path)
            return
        }
        if (node.playerOnly && sender !is org.bukkit.entity.Player) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("This command can only be used in game."))
            return
        }

        runCatching { handler.handle(CommandContext(sender, label, remaining)) }
            .onFailure { error ->
                logger.warning("Command /${path.joinToString(" ") { it.name }} failed: ${error.message}")
                sender.sendMessage(net.kyori.adventure.text.Component.text("That command failed, see the console."))
            }
    }

    fun complete(sender: CommandSender, args: List<String>): List<String> {
        val permitted = permittedFor(sender)
        if (args.isEmpty()) return root.suggest("", permitted)

        var node = root
        var index = 0
        while (index < args.size - 1) {
            val child = node.findChild(args[index]) ?: break
            node = child
            index++
        }
        return node.suggest(args.last(), permitted)
    }

    private fun permittedFor(sender: CommandSender): (CommandNode) -> Boolean = { node ->
        (node.permission == null || sender.hasPermission(node.permission)) &&
            (!node.playerOnly || sender is org.bukkit.entity.Player)
    }

    private fun sendUsage(sender: CommandSender, node: CommandNode, path: List<CommandNode>) {
        val full = path.joinToString(" ") { it.name }
        if (node.usage.isNotEmpty()) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Usage: /$full ${node.usage}"))
            return
        }
        sender.sendMessage(net.kyori.adventure.text.Component.text("Usage: /$full <subcommand>"))
        for (child in node.children) {
            val suffix = if (child.permission != null && !sender.hasPermission(child.permission)) "" else " - ${child.description}"
            sender.sendMessage(net.kyori.adventure.text.Component.text("  ${child.name}$suffix"))
        }
    }
}
