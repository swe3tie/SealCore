package sealmc.swe3tie.sealcore.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.SealCore;
import sealmc.swe3tie.sealcore.core.config.ConfigException;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.module.SealModule;

import java.util.List;

/** {@code /sealcore reload | module <list|enable|disable|reload> [id] | version | debug}. */
public final class SealCommand {

    private static final String BASE = "sealcore.command";
    private static final String ADMIN = "sealcore.admin";

    private final SealCore core;

    public SealCommand(@NotNull SealCore core) {
        this.core = core;
    }

    private static boolean isAdmin(@NotNull CommandSender sender) {
        return sender.isOp() || sender.hasPermission(ADMIN) || sender.hasPermission(BASE);
    }

    public void register(@NotNull CommandService commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sealcore")
            .executes(ctx -> this.executeRoot(ctx.getSource().getSender()))
            .then(Commands.literal("version")
                .executes(ctx -> this.version(ctx.getSource().getSender())))
            .then(Commands.literal("reload")
                .requires(s -> isAdmin(s.getSender()) || s.getSender().hasPermission(BASE + ".reload"))
                .executes(ctx -> this.reload(ctx.getSource().getSender())))
            .then(Commands.literal("debug")
                .requires(s -> isAdmin(s.getSender()) || s.getSender().hasPermission(BASE + ".debug"))
                .executes(ctx -> this.debug(ctx.getSource().getSender())))
            .then(this.moduleNode());

        commands.register(() -> true, root, "SealCore administration", List.of("sc"), null);
    }

    private LiteralArgumentBuilder<CommandSourceStack> moduleNode() {
        return Commands.literal("module")
            .requires(s -> isAdmin(s.getSender()) || s.getSender().hasPermission(BASE + ".module"))
            .executes(ctx -> {
                CommandUsage.send(this.core.lang(), ctx.getSource().getSender(), "/sealcore module <list|enable|disable|reload> [id]");
                return 0;
            })
            .then(Commands.literal("list").executes(ctx -> this.listModules(ctx.getSource().getSender())))
            .then(this.moduleAction("enable", this::enableModule))
            .then(this.moduleAction("disable", this::disableModule))
            .then(this.moduleAction("reload", this::reloadModule));
    }

    private LiteralArgumentBuilder<CommandSourceStack> moduleAction(String name, ModuleAction action) {
        return Commands.literal(name)
            .executes(ctx -> {
                CommandUsage.send(this.core.lang(), ctx.getSource().getSender(), "/sealcore module " + name + " <id>");
                return 0;
            })
            .then(Commands.argument("module", StringArgumentType.word())
            .suggests((ctx, builder) -> {
                this.core.modules().all().forEach(m -> {
                    if (m.id().startsWith(builder.getRemainingLowerCase())) builder.suggest(m.id());
                });
                return builder.buildFuture();
            })
            .executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                String id = StringArgumentType.getString(ctx, "module");
                SealModule module = this.core.modules().find(id);
                if (module == null) {
                    this.core.lang().send(sender, "core.module.unknown", Placeholder.unparsed("module", id));
                    return 0;
                }
                action.run(sender, module);
                return 1;
            }));
    }

    @FunctionalInterface
    private interface ModuleAction {
        void run(CommandSender sender, SealModule module);
    }

    private int executeRoot(CommandSender sender) {
        if (isAdmin(sender)) {
            return this.help(sender);
        }
        return this.version(sender);
    }

    private int help(CommandSender sender) {
        this.core.lang().send(sender, "core.help", Placeholder.unparsed("version", this.core.getPluginMeta().getVersion()));
        return 1;
    }

    private int version(CommandSender sender) {
        this.core.lang().send(sender, "core.version",
            Placeholder.unparsed("version", this.core.getPluginMeta().getVersion()),
            Placeholder.unparsed("server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion()));
        return 1;
    }

    private int reload(CommandSender sender) {
        long start = System.nanoTime();
        try {
            this.core.reload();
            this.core.lang().send(sender, "core.reload.success",
                Placeholder.unparsed("time", String.valueOf((System.nanoTime() - start) / 1_000_000L)));
        } catch (ConfigException e) {
            this.core.lang().send(sender, "core.reload.failed", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
            this.core.getLogger().warning("Reload failed: " + e.getMessage());
        }
        return 1;
    }

    private int listModules(CommandSender sender) {
        var modules = this.core.modules().all();
        this.core.lang().send(sender, "core.module.list-header", Placeholder.unparsed("count", String.valueOf(modules.size())));
        for (SealModule module : modules) {
            this.core.lang().send(sender, "core.module.list-entry",
                Placeholder.unparsed("module", module.id()),
                Placeholder.parsed("state", module.state().tag() + module.state().name()));
        }
        return 1;
    }

    private void enableModule(CommandSender sender, SealModule module) {
        String error = this.core.modules().enable(module);
        if (error == null) {
            this.core.lang().send(sender, "core.module.enabled", Placeholder.unparsed("module", module.id()));
        } else {
            this.core.lang().send(sender, "core.module.failed",
                Placeholder.unparsed("module", module.id()), Placeholder.unparsed("error", error));
        }
    }

    private void disableModule(CommandSender sender, SealModule module) {
        this.core.modules().disable(module);
        this.core.lang().send(sender, "core.module.disabled", Placeholder.unparsed("module", module.id()));
    }

    private void reloadModule(CommandSender sender, SealModule module) {
        String error = this.core.modules().reload(module);
        if (error == null) {
            this.core.lang().send(sender, "core.module.reloaded", Placeholder.unparsed("module", module.id()));
        } else {
            this.core.lang().send(sender, "core.module.failed",
                Placeholder.unparsed("module", module.id()), Placeholder.unparsed("error", error));
        }
    }

    private int debug(CommandSender sender) {
        sender.sendMessage("SealCore " + this.core.getPluginMeta().getVersion() + " on " + Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        for (Hook hook : Hook.values()) {
            sender.sendMessage(" hook " + hook.pluginName() + ": " + (this.core.hooks().isAvailable(hook) ? "hooked" : "not hooked"));
        }
        sender.sendMessage(" storage: " + this.core.storage().dialect() + (this.core.storage().isRunning() ? " (running)" : " (stopped)"));
        sender.sendMessage(" language: " + this.core.settings().lang());
        for (SealModule module : this.core.modules().all()) {
            String error = this.core.modules().lastError(module.id());
            sender.sendMessage(" module " + module.id() + ": " + module.state() + (error == null ? "" : " (" + error + ")"));
        }
        return 1;
    }
}
