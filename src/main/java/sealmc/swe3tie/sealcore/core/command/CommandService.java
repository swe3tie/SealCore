package sealmc.swe3tie.sealcore.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.SealCore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Registers Brigadier commands for all modules.
 * <p>
 * Paper only accepts command registration while the server fires its COMMANDS lifecycle event, which
 * happens once at startup. Modules therefore register while enabling, and {@link #install()} flushes
 * everything in that event. A command stays registered when its module is disabled at runtime; its
 * {@code requires} check hides it and rejects use until the module is enabled again.
 * Command names and aliases are read from config at startup (a restart applies changes).
 */
public final class CommandService {

    private record Entry(LiteralArgumentBuilder<CommandSourceStack> node, String description, List<String> aliases) {
    }

    private final SealCore core;
    private final List<Entry> pending = new ArrayList<>();
    private final Set<String> registered = new HashSet<>();
    private boolean flushed;

    public CommandService(@NotNull SealCore core) {
        this.core = core;
    }

    /** Hooks the lifecycle event. Call once from onEnable, before modules are enabled. */
    public void install() {
        this.core.getServer().getPluginManager().registerEvents(new CommandHideListener(), this.core);
        this.core.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (Entry entry : this.pending) {
                registrar.register(entry.node.build(), entry.description, entry.aliases);
            }
            this.pending.clear();
            this.flushed = true;
        });
    }

    /**
     * Queues a command.
     *
     * @param active     whether the owning feature is currently enabled
     * @param permission required permission, or null for everyone
     */
    public void register(@NotNull BooleanSupplier active, @NotNull LiteralArgumentBuilder<CommandSourceStack> node,
                         @NotNull String description, @NotNull List<String> aliases, @Nullable String permission) {
        String name = node.getLiteral();
        if (!this.registered.add(name)) return; // already registered by an earlier enable

        node.requires(source -> active.getAsBoolean()
            && (permission == null || permission.isEmpty() || source.getSender().hasPermission(permission)));

        if (this.flushed) {
            this.core.getLogger().warning("Command /" + name + " was added after startup and needs a server restart to appear.");
            return;
        }
        this.pending.add(new Entry(node, description, List.copyOf(aliases)));
    }

    private final class CommandHideListener implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onCommandSend(PlayerCommandSendEvent event) {
            Player player = event.getPlayer();
            if (player.isOp() || player.hasPermission("sealcore.admin")) return;
            event.getCommands().removeIf(cmd -> cmd.regionMatches(true, 0, "sealcore:", 0, 9));
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
            Player player = event.getPlayer();
            if (player.isOp() || player.hasPermission("sealcore.admin")) return;
            String msg = event.getMessage();
            if (msg.length() > 9 && msg.regionMatches(true, 1, "sealcore:", 0, 9)) {
                event.setCancelled(true);
                core.lang().send(player, "core.no-permission");
            }
        }
    }
}
