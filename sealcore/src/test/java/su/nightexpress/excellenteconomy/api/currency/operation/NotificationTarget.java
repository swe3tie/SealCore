package su.nightexpress.excellenteconomy.api.currency.operation;

/**
 * Test stand-in for ExcellentEconomy's {@code NotificationTarget}.
 *
 * <p>The reflective bridge resolves this by name, so this fake pins the exact
 * contract the plugin depends on. The order and the members match upstream 2.8.0:
 * the first two are chat, the last two are the operation log, and conflating the
 * two would either spam players or silently drop the audit trail.
 */
public enum NotificationTarget {
    USER,
    EXECUTOR,
    FILE_LOGGER,
    CONSOLE_LOGGER,
}
