package sealmc.swe3tie.sealcore.command;

import java.util.List;

/** Resolved path of a command invocation, used for help and error messages. */
public record CommandResult(CommandNode node, List<CommandNode> path, List<String> remaining) {
}
