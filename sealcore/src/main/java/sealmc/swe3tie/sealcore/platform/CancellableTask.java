package sealmc.swe3tie.sealcore.platform;

/** A scheduled task that has not run yet. */
@FunctionalInterface
public interface CancellableTask {

    void cancel();
}
