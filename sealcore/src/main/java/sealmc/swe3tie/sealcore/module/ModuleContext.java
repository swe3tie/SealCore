package sealmc.swe3tie.sealcore.module;

import java.util.logging.Logger;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.config.Messages;
import sealmc.swe3tie.sealcore.economy.EconomyService;
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine;
import sealmc.swe3tie.sealcore.platform.TaskScheduler;
import sealmc.swe3tie.sealcore.storage.PlayerRepository;

/**
 * What a feature module is allowed to touch.
 *
 * <p>The registry, the economy service and the scheduler are passed in rather
 * than reached through the plugin, which is what lets a command handler be
 * built and exercised in a unit test with no server running.
 *
 * <p>Every method here is safe to call from a command handler, which runs on a
 * server thread: work that suspends or hits storage belongs on the scheduler's
 * async side, and only the replies come back through {@link #reply} and
 * {@link #actionbar}.
 */
public final class ModuleContext {

    private final Messages messages;
    private final EconomyService economy;
    private final TaskScheduler scheduler;
    private final PlaceholderEngine placeholders;
    private final ModuleRegistry registry;
    private final Supplier<PlayerRepository> storage;
    private final Logger logger;

    public ModuleContext(
        Messages messages,
        EconomyService economy,
        TaskScheduler scheduler,
        PlaceholderEngine placeholders,
        ModuleRegistry registry,
        Supplier<PlayerRepository> storage,
        Logger logger
    ) {
        this.messages = messages;
        this.economy = economy;
        this.scheduler = scheduler;
        this.placeholders = placeholders;
        this.registry = registry;
        this.storage = storage;
        this.logger = logger;
    }

    public Messages messages() {
        return messages;
    }

    public EconomyService economy() {
        return economy;
    }

    public TaskScheduler scheduler() {
        return scheduler;
    }

    public PlaceholderEngine placeholders() {
        return placeholders;
    }

    public Logger logger() {
        return logger;
    }

    /** The spec currently applied for the module, or null when it is not active. */
    public <C extends ModuleSpec> C specOf(SealModule<C> module) {
        return registry.specOf(module.id());
    }

    public boolean isActive(String moduleId) {
        ModuleReport report = registry.report(moduleId);
        return report != null && report.isActive();
    }

    /**
     * The profile store, or null when storage failed to start. A module has to
     * degrade rather than fail: on a server without storage the online paths
     * still work.
     */
    public PlayerRepository profiles() {
        return storage.get();
    }

    /** Sends a message on a thread the recipient owns, as Folia requires. */
    public void reply(CommandSender sender, Component component) {
        if (sender instanceof Player player) {
            scheduler.player(player, () -> player.sendMessage(component));
        } else {
            scheduler.sync(() -> sender.sendMessage(component));
        }
    }

    public void actionbar(Player player, Component component) {
        scheduler.player(player, () -> player.sendActionBar(component));
    }
}
