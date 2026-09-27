package su.nightexpress.excellenteconomy.api.currency.operation;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * Test stand-in for ExcellentEconomy's {@code OperationContext}.
 *
 * <p>Reproduces the upstream behaviour the bridge relies on: a fresh context
 * notifies everyone, and {@code silentFor} removes targets. SealCore builds one of
 * these per transaction and silences the two chat targets, because SealCore sends
 * its own messages and ExcellentEconomy's default would double every one of them.
 */
public class OperationContext {

    private final OperationExecutor executor;
    private final EnumSet<NotificationTarget> notificationTargets;

    private OperationContext(OperationExecutor executor) {
        this.executor = executor;
        this.notificationTargets = EnumSet.allOf(NotificationTarget.class);
    }

    public static OperationContext of(OperationExecutor executor) {
        return new OperationContext(executor);
    }

    public OperationContext silent() {
        return this.silentFor(NotificationTarget.values());
    }

    public OperationContext silentFor(NotificationTarget... targets) {
        Arrays.asList(targets).forEach(this.notificationTargets::remove);
        return this;
    }

    public boolean shouldNotify(NotificationTarget target) {
        return this.notificationTargets.contains(target);
    }

    public boolean shouldNotifyLogger() {
        return this.shouldNotify(NotificationTarget.CONSOLE_LOGGER) || this.shouldNotify(NotificationTarget.FILE_LOGGER);
    }

    public OperationExecutor getExecutor() {
        return this.executor;
    }
}
