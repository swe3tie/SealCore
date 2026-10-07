package sealmc.swe3tie.sealcore.core.hook.economy;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Currency operations for every SealCore module, addressed by SealCore currency keys
 * ({@code money}, {@code shards}, {@code coins}...) as mapped in config.yml.
 * All methods are thread-safe.
 */
public interface CurrencyService {

    /** Configured SealCore currency keys, in config order. */
    @NotNull
    Set<String> keys();

    @NotNull
    String defaultKey();

    /** {@code true} if the key is mapped and the backing currency currently exists. */
    boolean exists(@NotNull String key);

    /** Display name of the currency (MiniMessage-compatible), or the key itself. */
    @NotNull
    String displayName(@NotNull String key);

    /** Fully formatted amount, e.g. {@code $1,234.50}. */
    @NotNull
    String format(@NotNull String key, double amount);

    /** Compact format, e.g. {@code $1.2K}. */
    @NotNull
    String formatCompact(@NotNull String key, double amount);

    /** Cached balance of an online player. */
    double balance(@NotNull Player player, @NotNull String key);

    @NotNull
    CompletableFuture<Double> balance(@NotNull UUID playerId, @NotNull String key);

    @NotNull
    CompletableFuture<Boolean> deposit(@NotNull UUID playerId, @NotNull String key, double amount, @NotNull String reason);

    /**
     * Atomically checks the balance and withdraws. Completes with {@code false} (and changes
     * nothing) when the balance is insufficient.
     */
    @NotNull
    CompletableFuture<Boolean> withdraw(@NotNull UUID playerId, @NotNull String key, double amount, @NotNull String reason);

    /** Moves money between two players; refunds the sender if the deposit fails. */
    @NotNull
    CompletableFuture<TransferResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull String key, double amount,
                                               @NotNull String reason);

    /** Leaderboard for a currency, or empty when the backend has leaderboards disabled. */
    @NotNull
    Optional<List<LeaderboardEntry>> leaderboard(@NotNull String key);

    record LeaderboardEntry(int position, @NotNull String name, @Nullable UUID playerId, double balance) {
    }

    enum TransferResult {
        SUCCESS, NOT_ENOUGH, FAILED
    }
}
