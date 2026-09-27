package sealmc.swe3tie.sealcore.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.config.ConfigManager;
import sealmc.swe3tie.sealcore.module.ModuleReport;
import sealmc.swe3tie.sealcore.module.ModuleState;

/**
 * The {@code /sealcore} command: framework introspection and maintenance.
 *
 * <p>Feature modules get their own commands; this one only covers the framework
 * itself, which is what an operator needs when something looks wrong.
 */
public final class SealCoreCommand implements CommandExecutor, TabCompleter {

    private final SealCore plugin;
    private final CommandNode root;
    private final CommandDispatcher dispatcher;

    public SealCoreCommand(SealCore plugin) {
        this.plugin = plugin;
        this.root = buildRoot();
        this.dispatcher = new CommandDispatcher(root, plugin.getLogger());
    }

    private CommandNode buildRoot() {
        CommandNode node = new CommandNode("sealcore", "SealCore framework commands", "sealcore.command", false, "", null);
        node.child("version", "Show the loaded version", "sealcore.command.version", false, "",
            context -> context.replySuccess(
                "SealCore " + plugin.getPluginMeta().getVersion() + " on " + plugin.platform().displayName()));
        node.child("reload", "Reload config, modules and language", "sealcore.command.reload", false, "",
            context -> reportReload(context, plugin.reloadPlugin()));
        node.child("modules", "List module state", "sealcore.command.debug", false, "",
            context -> printModules(context));
        node.child("debug", "Print framework state", "sealcore.command.debug", false,
            "<platform|economy|storage|gui|hud|config>", context -> printDebug(context));
        return node;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        dispatcher.dispatch(sender, label, Arrays.asList(args));
        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        return dispatcher.complete(sender, Arrays.asList(args));
    }

    /**
     * Reports what a reload actually did. A module that failed keeps its last good
     * settings, so saying "reloaded" alone would be a lie the operator cannot act
     * on.
     */
    private void reportReload(CommandContext context, ConfigManager.ReloadReport report) {
        long active = report.modules().stream().filter(module -> module.state() == ModuleState.ACTIVE).count();
        String header = "SealCore reloaded: " + active + "/" + report.modules().size() + " modules active.";
        if (report.clean()) {
            context.replySuccess(header);
        } else {
            context.replyError(header);
        }
        for (ModuleReport module : report.broken()) {
            context.replyError(module.id() + " " + module.state().name().toLowerCase(Locale.ROOT) + ": "
                + String.join("; ", module.problems()));
        }
        for (String key : report.restartRequired()) {
            context.replyError("config.yml: '" + key + "' changed and applies on the next restart.");
        }
        for (ModuleReport module : report.modules()) {
            for (String key : module.restartRequired()) {
                context.replyError(module.id() + ": '" + key + "' changed and applies on the next restart.");
            }
        }
    }

    private void printModules(CommandContext context) {
        List<ModuleReport> reports = plugin.modules().reports();
        if (reports.isEmpty()) {
            context.reply("<gray>No modules registered.</gray>");
            return;
        }
        context.reply("<gray>Modules:</gray> <white>" + plugin.modules().activeCount() + "/" + reports.size() + " active</white>");
        for (ModuleReport report : reports) {
            context.reply(describe(report));
        }
    }

    private static String describe(ModuleReport report) {
        String colour = switch (report.state()) {
            case ACTIVE -> "<green>";
            case DISABLED -> "<gray>";
            default -> "<red>";
        };
        String head = colour + report.state().name().toLowerCase(Locale.ROOT) + "</color> <white>" + report.id()
            + "</white> <dark_gray>" + report.file() + "</dark_gray>";
        if (report.problems().isEmpty()) {
            return head;
        }
        return head + " <red>" + String.join("; ", report.problems()) + "</red>";
    }

    private void printDebug(CommandContext context) {
        switch (context.string(0, "").toLowerCase(Locale.ROOT)) {
            case "platform" -> context.reply(
                "<gray>Platform:</gray> <white>" + plugin.platform().displayName() + "</white> "
                    + "<gray>MC:</gray> <white>" + plugin.getServer().getMinecraftVersion() + "</white>");

            case "economy" -> {
                var service = plugin.economy();
                context.reply("<gray>Provider:</gray> <white>" + service.provider().displayName() + "</white>");
                context.reply("<gray>Available:</gray> <white>" + service.isReady() + "</white>");
                context.reply("<gray>Currencies:</gray> <white>" + String.join(", ", service.knownCurrencies()) + "</white>");
            }

            case "storage" -> {
                context.reply("<gray>Storage:</gray> <white>" + plugin.config().storage().type() + "</white>");
                context.reply("<gray>Open:</gray> <white>" + plugin.isStorageReady() + "</white>");
                if (plugin.isStorageReady()) {
                    context.reply("<gray>Dialect:</gray> <white>" + plugin.database().dialect().id() + "</white>");
                    context.reply("<gray>Players:</gray> <white>" + plugin.players().count() + "</white>");
                }
            }

            case "gui" -> {
                context.reply("<gray>PacketEvents:</gray> <white>" + plugin.bridge().packetEventsVersion() + "</white>");
                context.reply("<gray>Enabled:</gray> <white>" + plugin.hasGui() + "</white>");
                if (plugin.hasGui()) {
                    context.reply("<gray>Open screens:</gray> <white>" + plugin.gui().openSessions() + "</white>");
                }
            }

            case "hud" -> context.reply(
                "<gray>HUD enabled:</gray> <white>" + plugin.hasHud() + "</white> "
                    + "<gray>interval:</gray> <white>" + plugin.config().hud().updateIntervalTicks() + "t</white>");

            case "config" -> {
                var messages = plugin.messages();
                context.reply("<gray>Language:</gray> <white>" + messages.language() + "</white>");
                context.reply("<gray>Fallback:</gray> <white>"
                    + (messages.fallbackLanguage() == null ? "none" : messages.fallbackLanguage()) + "</white>");
                context.reply("<gray>Modules:</gray> <white>" + plugin.modules().activeCount() + "/"
                    + plugin.modules().ids().size() + " active</white>");
                List<String> pending = plugin.configManager().restartPending();
                context.reply("<gray>Pending restart:</gray> <white>"
                    + (pending.isEmpty() ? "nothing" : String.join(", ", pending)) + "</white>");
            }

            default -> context.replyError("Usage: /sealcore debug <platform|economy|storage|gui|hud|config>");
        }
    }
}
