package sealmc.swe3tie.sealcore.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.Nullable;

/** Handle to a scheduled task. {@link #NONE} represents a task that never got scheduled. */
public final class SealTask {

    public static final SealTask NONE = new SealTask(null);

    private final ScheduledTask handle;

    SealTask(@Nullable ScheduledTask handle) {
        this.handle = handle;
    }

    public void cancel() {
        if (this.handle != null) this.handle.cancel();
    }

    public boolean isCancelled() {
        return this.handle == null || this.handle.isCancelled();
    }

    public boolean isScheduled() {
        return this.handle != null;
    }
}
