package sealmc.swe3tie.sealcore.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
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
}
