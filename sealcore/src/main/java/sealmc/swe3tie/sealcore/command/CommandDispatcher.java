package sealmc.swe3tie.sealcore.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Walks the {@link CommandNode} tree for one invocation.
 *
 * <p>Kept free of Bukkit's command API so the routing rules can be unit tested
 * with a fake sender; {@link SealCoreCommand} is the thin Bukkit adapter.
 */
public final class CommandDispatcher {

    private final CommandNode root;
    private final Logger logger;

    public CommandDispatcher(CommandNode root, Logger logger) {
        this.root = root;
        this.logger = logger;
    }

    public void dispatch(CommandSender sender, String label, List<String> args) {
        if (!permitted(sender).test(root)) {
            sender.sendMessage(Component.text("You do not have permission."));
            return;
        }

        CommandNode node = root;
        List<CommandNode> path = new ArrayList<>();
        path.add(root);
        int index = 0;
        while (index < args.size()) {
            CommandNode child = node.findChild(args.get(index));
            if (child == null) {
                break;
            }
            if (!permitted(sender).test(child)) {
                sender.sendMessage(Component.text("You do not have permission."));
                return;
            }
            node = child;
            path.add(child);
            index++;
        }

        List<String> remaining = args.subList(index, args.size());
        CommandHandler handler = node.handler();
        if (handler == null) {
            sendUsage(sender, node, path);
            return;
        }
        if (node.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used in game."));
            return;
        }

        try {
            handler.handle(new CommandContext(sender, label, remaining));
        } catch (RuntimeException error) {
            logger.warning("Command /" + join(path) + " failed: " + error.getMessage());
            sender.sendMessage(Component.text("That command failed, see the console."));
        }
    }

    public List<String> complete(CommandSender sender, List<String> args) {
        Predicate<CommandNode> permitted = permitted(sender);
        if (args.isEmpty()) {
            return root.suggest("", permitted);
        }

        CommandNode node = root;
        int index = 0;
        while (index < args.size() - 1) {
            CommandNode child = node.findChild(args.get(index));
            if (child == null) {
                break;
            }
            node = child;
            index++;
        }
        List<String> children = node.suggest(args.get(args.size() - 1), permitted);
        if (!children.isEmpty()) {
            return children;
        }
        // No subcommand matched, so this is an argument: complete it with
        // whatever the node knows about.
        if (node.argSuggestions() == null) {
            return List.of();
        }
        String partial = args.get(args.size() - 1);
        List<String> suggestions = new ArrayList<>();
        for (String candidate : node.argSuggestions().apply(partial)) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) {
                suggestions.add(candidate);
            }
        }
        suggestions.sort(String::compareTo);
        return suggestions;
    }

    private static Predicate<CommandNode> permitted(CommandSender sender) {
        return node -> (node.permission() == null || sender.hasPermission(node.permission()))
            && (!node.playerOnly() || sender instanceof Player);
    }

    private void sendUsage(CommandSender sender, CommandNode node, List<CommandNode> path) {
        String full = join(path);
        if (!node.usage().isEmpty()) {
            sender.sendMessage(Component.text("Usage: /" + full + " " + node.usage()));
            return;
        }
        sender.sendMessage(Component.text("Usage: /" + full + " <subcommand>"));
        for (CommandNode child : node.children()) {
            String suffix = child.permission() != null && !sender.hasPermission(child.permission())
                ? ""
                : " - " + child.description();
            sender.sendMessage(Component.text("  " + child.name() + suffix));
        }
    }

    private static String join(List<CommandNode> path) {
        StringBuilder builder = new StringBuilder();
        for (CommandNode node : path) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(node.name());
        }
        return builder.toString();
    }
}
