package sealmc.swe3tie.sealcore.platform

import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Test double for [TaskScheduler] that runs everything inline and records what
 * was asked for, so unit tests can assert routing without a running server.
 */
class RecordingTaskScheduler : TaskScheduler {

    enum class Kind { SYNC, SYNC_LATER, ASYNC, ASYNC_LATER, REPEATING, PLAYER, PLAYER_LATER, PLAYER_ASYNC, REGION, REGION_LATER }

    data class Call(val kind: Kind, val target: String? = null, val delayTicks: Long = 0L, val periodTicks: Long = 0L)

    val calls = mutableListOf<Call>()

    var cancelledRepeatingTasks = 0
        private set

    override fun sync(task: () -> Unit) = record(Kind.SYNC).also { task() }

    override fun syncLater(delayTicks: Long, task: () -> Unit) = record(Kind.SYNC_LATER, delayTicks = delayTicks).also { task() }

    override fun async(task: () -> Unit) = record(Kind.ASYNC).also { task() }

    override fun asyncLater(delayTicks: Long, task: () -> Unit) = record(Kind.ASYNC_LATER, delayTicks = delayTicks).also { task() }

    override fun repeating(delayTicks: Long, periodTicks: Long, task: () -> Unit): CancellableTask {
        record(Kind.REPEATING, delayTicks = delayTicks, periodTicks = periodTicks)
        return CancellableTask { cancelledRepeatingTasks++ }
    }

    override fun player(player: Player, task: () -> Unit, onRetired: () -> Unit) = record(Kind.PLAYER, player.name).also { task() }

    override fun playerLater(player: Player, delayTicks: Long, task: () -> Unit, onRetired: () -> Unit) =
        record(Kind.PLAYER_LATER, player.name, delayTicks).also { task() }

    override fun playerAsync(player: Player, task: () -> Unit) = record(Kind.PLAYER_ASYNC, player.name).also { task() }

    override fun region(location: Location, task: () -> Unit) = record(Kind.REGION, location.world?.name).also { task() }

    override fun regionLater(location: Location, delayTicks: Long, task: () -> Unit) =
        record(Kind.REGION_LATER, location.world?.name, delayTicks).also { task() }

    override fun cancelAll() {
        calls += Call(Kind.REPEATING, target = "cancelAll")
    }

    private fun record(kind: Kind, target: String? = null, delayTicks: Long = 0L, periodTicks: Long = 0L) {
        calls += Call(kind, target, delayTicks, periodTicks)
    }
}
