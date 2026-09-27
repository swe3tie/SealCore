package sealmc.swe3tie.sealcore.command;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import sealmc.swe3tie.sealcore.module.ModuleContext;
import sealmc.swe3tie.sealcore.module.SealCommand;

/**
 * Bukkit adapter for a {@link SealCommand}: binds one module's command tree to
 * the {@code Command} the server resolved from {@code plugin.yml}.
 *
 * <p>The tree is built once, lazily, because building it reads the module's active
 * spec and a module that is switched off has none. The active check runs before
 * that, so a disabled module answers with a message instead of a crash.
 */
public final class SealCommandAdapter implements CommandExecutor, TabCompleter {

    private final SealCommand command;
    private final ModuleContext context;
    private final Logger logger;

    // Not synchronised on purpose: two threads racing here both build the same
    // tree from the same spec, and blocking a command thread on a lock would cost
    // more than the discarded second build.
    private volatile CommandNode node;
    private volatile CommandDispatcher dispatcher;

    public SealCommandAdapter(SealCommand command, ModuleContext context, Logger logger) {
        this.command = command;
        this.context = context;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command bukkitCommand,
        String label,
        String[] args
    ) {
        if (!active(sender)) {
            return true;
        }
        dispatcher().dispatch(sender, label, Arrays.asList(args));
        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command bukkitCommand,
        String label,
        String[] args
    ) {
        if (!active(sender)) {
            return List.of();
        }
        return dispatcher().complete(sender, Arrays.asList(args));
    }

    private CommandDispatcher dispatcher() {
        CommandDispatcher current = dispatcher;
        if (current == null) {
            current = new CommandDispatcher(node(), logger);
            dispatcher = current;
        }
        return current;
    }

    private CommandNode node() {
        CommandNode current = node;
        if (current == null) {
            current = command.build(context);
            node = current;
        }
        return current;
    }

    /** Tells the sender the module is off, instead of running a dead tree. */
    private boolean active(CommandSender sender) {
        if (context.isActive(command.module().id())) {
            return true;
        }
        context.reply(sender, context.messages().component("core.module-disabled", "module", command.module().id()));
        return false;
    }
}
