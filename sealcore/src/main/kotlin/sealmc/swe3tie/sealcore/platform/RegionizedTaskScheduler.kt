package sealmc.swe3tie.sealcore.platform

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * [TaskScheduler] built exclusively on the regionized scheduler API.
 *
 * The same implementation is correct for Paper and Folia: Paper ships the
 * regionized schedulers and executes the global region scheduler on its main
 * thread, so a plugin written this way needs no platform branching at all.
 */
class RegionizedTaskScheduler(private val plugin: Plugin) : TaskScheduler {

    private val global get() = Bukkit.getGlobalRegionScheduler()

    private val asyncScheduler get() = Bukkit.getAsyncScheduler()

    private val regionScheduler get() = Bukkit.getRegionScheduler()

    override fun sync(task: () -> Unit) {
        global.run(plugin, Consumer { task() })
    }

    override fun syncLater(delayTicks: Long, task: () -> Unit) {
        if (delayTicks <= 0L) {
            sync(task)
        } else {
            global.runDelayed(plugin, Consumer { task() }, delayTicks)
        }
    }

    override fun async(task: () -> Unit) {
        asyncScheduler.runNow(plugin, Consumer { task() })
    }

    override fun asyncLater(delayTicks: Long, task: () -> Unit) {
        if (delayTicks <= 0L) {
            async(task)
        } else {
            asyncScheduler.runDelayed(plugin, Consumer { task() }, toMillis(delayTicks), TimeUnit.MILLISECONDS)
        }
    }

    override fun repeating(delayTicks: Long, periodTicks: Long, task: () -> Unit): CancellableTask {
        val handle = asyncScheduler.runAtFixedRate(
            plugin,
            Consumer { task() },
            toMillis(delayTicks),
            toMillis(periodTicks.coerceAtLeast(1L)),
            TimeUnit.MILLISECONDS,
        )
        return CancellableTask { handle.cancel() }
    }

    override fun player(player: Player, task: () -> Unit, onRetired: () -> Unit) {
        // A retired entity never runs the task; the retired callback is the only
        // supported way to learn that, so cleanup does not need isOnline polling.
        player.scheduler.run(plugin, Consumer { task() }, Runnable { onRetired() })
    }

    override fun playerLater(player: Player, delayTicks: Long, task: () -> Unit, onRetired: () -> Unit) {
        player.scheduler.runDelayed(
            plugin,
            Consumer { task() },
            Runnable { onRetired() },
            delayTicks.coerceAtLeast(1L),
        )
    }

    override fun playerAsync(player: Player, task: () -> Unit) {
        player.scheduler.run(plugin, Consumer { task() }, null)
    }

    override fun region(location: Location, task: () -> Unit) {
        val world = location.world ?: return
        regionScheduler.execute(plugin, world, location.blockX shr 4, location.blockZ shr 4, Runnable { task() })
    }

    override fun regionLater(location: Location, delayTicks: Long, task: () -> Unit) {
        val world = location.world ?: return
        regionScheduler.runDelayed(
            plugin,
            world,
            location.blockX shr 4,
            location.blockZ shr 4,
            Consumer { task() },
            delayTicks.coerceAtLeast(1L),
        )
    }

    override fun cancelAll() {
        global.cancelTasks(plugin)
        asyncScheduler.cancelTasks(plugin)
    }

    private fun toMillis(ticks: Long): Long = ticks * MILLIS_PER_TICK

    private companion object {
        const val MILLIS_PER_TICK = 50L
    }
}
