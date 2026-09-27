package sealmc.swe3tie.sealcore.platform;

import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@link TaskScheduler} built exclusively on the regionized scheduler API.
 *
 * <p>The same implementation is correct for Paper and Folia: Paper ships the
 * regionized schedulers and executes the global region scheduler on its main
 * thread, so a plugin written this way needs no platform branching at all.
 */
public final class RegionizedTaskScheduler implements TaskScheduler {

    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;

    public RegionizedTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sync(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    @Override
    public void syncLater(long delayTicks, Runnable task) {
        if (delayTicks <= 0L) {
            sync(task);
            return;
        }
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), delayTicks);
    }

    @Override
    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    @Override
    public void asyncLater(long delayTicks, Runnable task) {
        if (delayTicks <= 0L) {
            async(task);
            return;
        }
        Bukkit.getAsyncScheduler().runDelayed(plugin, ignored -> task.run(), toMillis(delayTicks), TimeUnit.MILLISECONDS);
    }

    @Override
    public CancellableTask repeating(long delayTicks, long periodTicks, Runnable task) {
        var handle = Bukkit.getAsyncScheduler().runAtFixedRate(
            plugin,
            ignored -> task.run(),
            toMillis(delayTicks),
            toMillis(Math.max(1L, periodTicks)),
            TimeUnit.MILLISECONDS);
        return () -> handle.cancel();
    }

    @Override
    public void player(Player player, Runnable task, Runnable onRetired) {
        // A retired entity never runs the task; the retired callback is the only
        // supported way to learn that, so cleanup does not need isOnline polling.
        player.getScheduler().run(plugin, ignored -> task.run(), onRetired::run);
    }

    @Override
    public void playerLater(Player player, long delayTicks, Runnable task, Runnable onRetired) {
        player.getScheduler().runDelayed(plugin, ignored -> task.run(), onRetired::run, Math.max(1L, delayTicks));
    }

    @Override
    public void playerAsync(Player player, Runnable task) {
        player.getScheduler().run(plugin, ignored -> task.run(), null);
    }

    @Override
    public void region(Location location, Runnable task) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Bukkit.getRegionScheduler().execute(
            plugin,
            world,
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4,
            task);
    }

    @Override
    public void regionLater(Location location, long delayTicks, Runnable task) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Bukkit.getRegionScheduler().runDelayed(
            plugin,
            world,
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4,
            ignored -> task.run(),
            Math.max(1L, delayTicks));
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private static long toMillis(long ticks) {
        return ticks * MILLIS_PER_TICK;
    }
}
