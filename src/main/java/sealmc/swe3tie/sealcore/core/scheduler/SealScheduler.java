package sealmc.swe3tie.sealcore.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * One scheduling API for Paper and Folia.
 * <p>
 * Paper implements the same region-aware schedulers as Folia (running them on the main thread), so
 * SealCore never branches on the platform and never uses the legacy {@code BukkitScheduler}.
 * <ul>
 *     <li>{@code global*}: world-independent work (global region on Folia).</li>
 *     <li>{@code at*}: work touching blocks/chunks at a location.</li>
 *     <li>{@code entity*}: work touching an entity or player. Follows the entity across regions.</li>
 *     <li>{@code async*}: I/O and CPU work that must not touch the world.</li>
 * </ul>
 * Tick values below 1 are clamped to 1 (Folia rejects 0 delays).
 */
public final class SealScheduler {

    private final Plugin plugin;

    public SealScheduler(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    // Global ------------------------------------------------------------------------------------

    @NotNull
    public SealTask global(@NotNull Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().run(this.plugin, t -> task.run()));
    }

    @NotNull
    public SealTask globalLater(@NotNull Runnable task, long delayTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, t -> task.run(), ticks(delayTicks)));
    }

    @NotNull
    public SealTask globalTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, t -> task.run(), ticks(delayTicks), ticks(periodTicks)));
    }

    // Region ------------------------------------------------------------------------------------

    @NotNull
    public SealTask at(@NotNull Location location, @NotNull Runnable task) {
        return wrap(Bukkit.getRegionScheduler().run(this.plugin, location, t -> task.run()));
    }

    @NotNull
    public SealTask atLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return wrap(Bukkit.getRegionScheduler().runDelayed(this.plugin, location, t -> task.run(), ticks(delayTicks)));
    }

    @NotNull
    public SealTask atTimer(@NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, location, t -> task.run(), ticks(delayTicks), ticks(periodTicks)));
    }

    // Entity ------------------------------------------------------------------------------------

    /**
     * Runs on the thread owning the entity.
     *
     * @param retired run instead if the entity is removed before the task executes (may be null)
     * @return the task, or {@link SealTask#NONE} if the entity was already removed
     */
    @NotNull
    public SealTask entity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return wrapNullable(entity.getScheduler().run(this.plugin, t -> task.run(), retired));
    }

    @NotNull
    public SealTask entity(@NotNull Entity entity, @NotNull Runnable task) {
        return this.entity(entity, task, null);
    }

    @NotNull
    public SealTask entityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return wrapNullable(entity.getScheduler().runDelayed(this.plugin, t -> task.run(), retired, ticks(delayTicks)));
    }

    @NotNull
    public SealTask entityTimer(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
                                long delayTicks, long periodTicks) {
        return wrapNullable(entity.getScheduler().runAtFixedRate(this.plugin, t -> task.run(), retired, ticks(delayTicks), ticks(periodTicks)));
    }

    // Async -------------------------------------------------------------------------------------

    @NotNull
    public SealTask async(@NotNull Runnable task) {
        return wrap(Bukkit.getAsyncScheduler().runNow(this.plugin, t -> task.run()));
    }

    @NotNull
    public SealTask asyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        return wrap(Bukkit.getAsyncScheduler().runDelayed(this.plugin, t -> task.run(), Math.max(1, delay.toMillis()), TimeUnit.MILLISECONDS));
    }

    @NotNull
    public SealTask asyncTimer(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin, t -> task.run(),
            Math.max(1, delay.toMillis()), Math.max(1, period.toMillis()), TimeUnit.MILLISECONDS));
    }

    // Utilities ---------------------------------------------------------------------------------

    /** {@code true} if the current thread owns the entity (always true on the Paper main thread). */
    public boolean owns(@NotNull Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    /** Cancels every task SealCore scheduled. Entity tasks die with their entities or on shutdown. */
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(this.plugin);
        Bukkit.getAsyncScheduler().cancelTasks(this.plugin);
    }

    private static long ticks(long ticks) {
        return Math.max(1L, ticks);
    }

    private static SealTask wrap(@NotNull ScheduledTask task) {
        return new SealTask(task);
    }

    private static SealTask wrapNullable(@Nullable ScheduledTask task) {
        return task == null ? SealTask.NONE : new SealTask(task);
    }
}
