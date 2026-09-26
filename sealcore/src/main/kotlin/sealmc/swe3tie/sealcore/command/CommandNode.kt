package sealmc.swe3tie.sealcore.command

/**
 * One node of the command tree.
 *
 * Nodes are resolved by walking children; a node with a handler is a leaf
 * unless it also has children, in which case the handler only runs when no
 * child matches.
 */
class CommandNode(
    val name: String,
    val description: String = "",
    val permission: String? = null,
    val playerOnly: Boolean = false,
    val usage: String = "",
    handler: CommandHandler? = null,
) {

    private val childrenList = mutableListOf<CommandNode>()

    var handler: CommandHandler? = handler
        private set

    val children: List<CommandNode> get() = childrenList

    val isLeaf: Boolean get() = childrenList.isEmpty()

    fun child(
        name: String,
        description: String = "",
        permission: String? = null,
        playerOnly: Boolean = false,
        usage: String = "",
        handler: CommandHandler? = null,
    ): CommandNode = CommandNode(name, description, permission, playerOnly, usage, handler)
        .also { childrenList += it }

    fun findChild(name: String): CommandNode? = childrenList.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Suggestions for tab completion, filtered by what the sender may use. */
    fun suggest(partial: String, permitted: (CommandNode) -> Boolean): List<String> =
        childrenList
            .filter { permitted(it) }
            .map { it.name }
            .filter { it.startsWith(partial, ignoreCase = true) }
            .sorted()
}

/** Resolved path of a command invocation, used for help and error messages. */
data class CommandResult(
    val node: CommandNode,
    val path: List<CommandNode>,
    val remaining: List<String>,
)
