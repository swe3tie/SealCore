package sealmc.swe3tie.sealcore.platform

import org.bukkit.Location
import org.bukkit.entity.Player

/** A scheduled task that has not run yet. */
fun interface CancellableTask {
    fun cancel()
}

/**
 * The only way SealCore schedules work.
 *
 * Nothing in the plugin may call [org.bukkit.scheduler.BukkitScheduler] directly:
 * on Folia that throws `UnsupportedOperationException`. Every implementation
 * routes through the regionized schedulers, which Paper also implements.
 *
 * Thread contract:
 * - [sync], [syncLater], [region] and [player] run on a server thread.
 * - [async], [asyncLater] and [repeating] run off any server thread, so shared
 *   state must be guarded by the caller.
 */
interface TaskScheduler {

    /** Runs on the global region thread, like a normal `runTask`. */
    fun sync(task: () -> Unit)

    fun syncLater(delayTicks: Long, task: () -> Unit)

    /** Runs off the server threads entirely. */
    fun async(task: () -> Unit)

    fun asyncLater(delayTicks: Long, task: () -> Unit)

    /** Repeats off the server threads until cancelled. */
    fun repeating(delayTicks: Long, periodTicks: Long, task: () -> Unit): CancellableTask

    /**
     * Runs on the thread owning the player. [onRetired] fires instead when the
     * player left the server or their entity was removed, so callers can clean
     * up without polling `isOnline`.
     */
    fun player(player: Player, task: () -> Unit, onRetired: () -> Unit = {})

    fun playerLater(player: Player, delayTicks: Long, task: () -> Unit, onRetired: () -> Unit = {})

    fun playerAsync(player: Player, task: () -> Unit)

    /** Runs on the thread owning the region at [location]. */
    fun region(location: Location, task: () -> Unit)

    fun regionLater(location: Location, delayTicks: Long, task: () -> Unit)

    /** Cancels every task this scheduler created. Called on disable. */
    fun cancelAll()
}
