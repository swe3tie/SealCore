package su.nightexpress.excellenteconomy.tops.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.tops.TopManager;
import su.nightexpress.nightcore.manager.AbstractListener;

public class TopListener extends AbstractListener<EconomyPlugin> {

    private final TopManager manager;

    public TopListener(@NotNull EconomyPlugin plugin, @NotNull TopManager manager) {
        super(plugin);
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.manager.handleJoin(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.manager.handleQuit(event);
    }
}
