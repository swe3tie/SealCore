package sealmc.swe3tie.sealcore.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One node of the command tree.
 *
 * <p>Nodes are resolved by walking children; a node with a handler is a leaf
 * unless it also has children, in which case the handler only runs when no
 * child matches.
 */
public final class CommandNode {

    private final String name;
    private final String description;
    private final String permission;
    private final boolean playerOnly;
    private final String usage;
    private final List<CommandNode> children = new ArrayList<>();
    private final CommandHandler handler;

    /**
     * Completes this node's own argument, for example a player name. Only
     * consulted when no child matches, so a node with subcommands never has to
     * decide what an argument looks like.
     */
    private Function<String, List<String>> argSuggestions;

    public CommandNode(String name) {
        this(name, "", null, false, "", null);
    }

    public CommandNode(
        String name,
        String description,
        String permission,
        boolean playerOnly,
        String usage,
        CommandHandler handler
    ) {
        this.name = name;
        this.description = description;
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.usage = usage;
        this.handler = handler;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return permission;
    }

    public boolean playerOnly() {
        return playerOnly;
    }

    public String usage() {
        return usage;
    }

    public CommandHandler handler() {
        return handler;
    }

    public List<CommandNode> children() {
        return children;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public CommandNode child(
        String name,
        String description,
        String permission,
        boolean playerOnly,
        String usage,
        CommandHandler handler
    ) {
        CommandNode node = new CommandNode(name, description, permission, playerOnly, usage, handler);
        children.add(node);
        return node;
    }

    public Function<String, List<String>> argSuggestions() {
        return argSuggestions;
    }

    public CommandNode argSuggestions(Function<String, List<String>> argSuggestions) {
        this.argSuggestions = argSuggestions;
        return this;
    }

    public CommandNode findChild(String name) {
        for (CommandNode child : children) {
            if (child.name.equalsIgnoreCase(name)) {
                return child;
            }
        }
        return null;
    }

    /** Suggestions for tab completion, filtered by what the sender may use. */
    public List<String> suggest(String partial, java.util.function.Predicate<CommandNode> permitted) {
        List<String> names = new ArrayList<>();
        for (CommandNode child : children) {
            if (permitted.test(child) && child.name.toLowerCase(java.util.Locale.ROOT)
                .startsWith(partial.toLowerCase(java.util.Locale.ROOT))) {
                names.add(child.name);
            }
        }
        names.sort(String::compareTo);
        return names;
    }
}
