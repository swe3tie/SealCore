package su.nightexpress.excellenteconomy.api.currency.operation;

import java.util.Optional;

/**
 * Test stand-in for ExcellentEconomy's {@code OperationExecutor}.
 *
 * <p>Mirrors the upstream interface, including the {@code custom(String)} factory
 * the bridge calls to attribute a transaction to a SealCore feature in
 * ExcellentEconomy's operation log. Upstream types {@code getBukkitSender} on
 * {@code org.bukkit.command.CommandSender}, which is left out here because the
 * bridge never asks for it and the server API is not needed to stand in.
 */
public interface OperationExecutor {

    String getName();

    Optional<Object> getBukkitSender();

    static OperationExecutor custom(String name) {
        return new OperationExecutor() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Optional<Object> getBukkitSender() {
                return Optional.empty();
            }
        };
    }
}
