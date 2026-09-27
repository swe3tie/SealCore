package sealmc.swe3tie.sealcore.platform;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Test double for {@link TaskScheduler} that runs everything inline and records
 * what was asked for, so unit tests can assert routing without a running server.
 */
public final class RecordingTaskScheduler implements TaskScheduler {

    public enum Kind {
        SYNC,
        SYNC_LATER,
        ASYNC,
        ASYNC_LATER,
        REPEATING,
        PLAYER,
        PLAYER_LATER,
        PLAYER_ASYNC,
        REGION,
        REGION_LATER
    }

    /** One recorded scheduling request. */
    public record Call(Kind kind, String target, long delayTicks, long periodTicks) {
        public Call(Kind kind) {
            this(kind, null, 0L, 0L);
        }

        public Call(Kind kind, String target) {
            this(kind, target, 0L, 0L);
        }

        public Call(Kind kind, String target, long delayTicks) {
            this(kind, target, delayTicks, 0L);
        }
    }

    private final List<Call> calls = new ArrayList<>();

    private int cancelledRepeatingTasks;

    public List<Call> calls() {
        return calls;
    }

    public int cancelledRepeatingTasks() {
        return cancelledRepeatingTasks;
    }

    @Override
    public void sync(Runnable task) {
        record(new Call(Kind.SYNC));
        task.run();
    }

    @Override
    public void syncLater(long delayTicks, Runnable task) {
        record(new Call(Kind.SYNC_LATER, null, delayTicks));
        task.run();
    }

    @Override
    public void async(Runnable task) {
        record(new Call(Kind.ASYNC));
        task.run();
    }

    @Override
    public void asyncLater(long delayTicks, Runnable task) {
        record(new Call(Kind.ASYNC_LATER, null, delayTicks));
        task.run();
    }

    @Override
    public CancellableTask repeating(long delayTicks, long periodTicks, Runnable task) {
        record(new Call(Kind.REPEATING, null, delayTicks, periodTicks));
        return () -> cancelledRepeatingTasks++;
    }

    @Override
    public void player(Player player, Runnable task, Runnable onRetired) {
        record(new Call(Kind.PLAYER, player.getName()));
        task.run();
    }

    @Override
    public void playerLater(Player player, long delayTicks, Runnable task, Runnable onRetired) {
        record(new Call(Kind.PLAYER_LATER, player.getName(), delayTicks));
        task.run();
    }

    @Override
    public void playerAsync(Player player, Runnable task) {
        record(new Call(Kind.PLAYER_ASYNC, player.getName()));
        task.run();
    }

    @Override
    public void region(Location location, Runnable task) {
        World world = location.getWorld();
        record(new Call(Kind.REGION, world == null ? null : world.getName()));
        task.run();
    }

    @Override
    public void regionLater(Location location, long delayTicks, Runnable task) {
        World world = location.getWorld();
        record(new Call(Kind.REGION_LATER, world == null ? null : world.getName(), delayTicks));
        task.run();
    }

    @Override
    public void cancelAll() {
        calls.add(new Call(Kind.REPEATING, "cancelAll"));
    }

    private void record(Call call) {
        calls.add(call);
    }
}
