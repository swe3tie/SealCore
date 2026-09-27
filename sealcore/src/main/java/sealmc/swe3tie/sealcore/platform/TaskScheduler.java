package sealmc.swe3tie.sealcore.platform;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The only way SealCore schedules work.
 *
 * <p>Nothing in the plugin may call {@code org.bukkit.scheduler.BukkitScheduler}
 * directly: on Folia that throws {@code UnsupportedOperationException}. Every
 * implementation routes through the regionized schedulers, which Paper also
 * implements.
 *
 * <p>Thread contract:
 * <ul>
 *   <li>{@code sync}, {@code syncLater}, {@code region} and {@code player} run
 *       on a server thread.</li>
 *   <li>{@code async}, {@code asyncLater} and {@code repeating} run off any
 *       server thread, so shared state must be guarded by the caller.</li>
 * </ul>
 */
public interface TaskScheduler {

    /** Runs on the global region thread, like a normal {@code runTask}. */
    void sync(Runnable task);

    void syncLater(long delayTicks, Runnable task);

    /** Runs off the server threads entirely. */
    void async(Runnable task);

    void asyncLater(long delayTicks, Runnable task);

    /** Repeats off the server threads until cancelled. */
    CancellableTask repeating(long delayTicks, long periodTicks, Runnable task);

    /**
     * Runs on the thread owning the player. {@code onRetired} fires instead when
     * the player left the server or their entity was removed, so callers can
     * clean up without polling {@code isOnline}.
     */
    void player(Player player, Runnable task, Runnable onRetired);

    void playerLater(Player player, long delayTicks, Runnable task, Runnable onRetired);

    void playerAsync(Player player, Runnable task);

    /** Runs on the thread owning the region at the location. */
    void region(Location location, Runnable task);

    void regionLater(Location location, long delayTicks, Runnable task);

    /** Cancels every task this scheduler created. Called on disable. */
    void cancelAll();

    /** Convenience for the common case where the player vanishing is not a concern. */
    default void player(Player player, Runnable task) {
        player(player, task, () -> { });
    }
}
