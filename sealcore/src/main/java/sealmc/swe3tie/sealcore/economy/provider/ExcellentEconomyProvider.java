package sealmc.swe3tie.sealcore.economy.provider;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.economy.EconomyProvider;
import sealmc.swe3tie.sealcore.economy.EconomyResult;

/**
 * {@link EconomyProvider} backed by ExcellentEconomy.
 *
 * <p>Amounts are validated here so an obviously bad request never reaches the
 * provider, and a failed withdrawal during a transfer refunds the sender
 * instead of minting money. The chain is built with futures rather than
 * blocking, so a caller can await on an async worker or compose further.
 *
 * <p>The balance rule lives here rather than in the commands, because ExcellentEconomy
 * does not enforce one. Its API-level withdrawal, the one this plugin reflects into,
 * is {@code CurrencyManager.remove}, which subtracts the amount and reports success
 * without ever asking whether the player can afford it; only its own {@code /pay}
 * command checks first, and a command cannot be trusted to be the only caller. So the
 * provider checks the balance itself, and holds each player's operations one after
 * another so two concurrent payments cannot both pass that check and mint the
 * difference.
 */
public final class ExcellentEconomyProvider implements EconomyProvider {

    private final ExcellentEconomyBridge bridge;
    private final Map<CurrencyKey, String> currencyIds;
    private final Logger logger;
    private final String displayName;

    /** Tail of each player's operation queue, so a check and its withdrawal cannot interleave. */
    private final Map<UUID, CompletableFuture<Void>> queues = new ConcurrentHashMap<>();

    public ExcellentEconomyProvider(
        ExcellentEconomyBridge bridge,
        Map<CurrencyKey, String> currencyIds,
        Logger logger
    ) {
        this(bridge, currencyIds, logger, "ExcellentEconomy");
    }

    public ExcellentEconomyProvider(
        ExcellentEconomyBridge bridge,
        Map<CurrencyKey, String> currencyIds,
        Logger logger,
        String displayName
    ) {
        this.bridge = bridge;
        this.currencyIds = currencyIds;
        this.logger = logger;
        this.displayName = displayName;
    }

    @Override
    public String id() {
        return "excellenteconomy";
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<String> currencies() {
        return bridge.currencyIds();
    }

    @Override
    public CompletableFuture<Double> balance(UUID playerId, CurrencyKey currency) {
        return readBalance(playerId, currency)
            .exceptionally(error -> {
                logger.warning("Balance lookup failed for " + playerId + ": " + error.getMessage());
                return 0.0;
            });
    }

    /**
     * The balance as the provider reports it, with a failed read left as a failure.
     *
     * <p>The check before a withdrawal needs the difference: turning a read error
     * into zero here would report a player with money as unable to pay.
     */
    private CompletableFuture<Double> readBalance(UUID playerId, CurrencyKey currency) {
        return bridge.getBalanceAsync(playerId, providerId(currency));
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(
        UUID playerId,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        if (!isValidAmount(amount)) {
            return invalidAmount();
        }
        return bridge.depositAsync(playerId, providerId(currency), amount, reason)
            .thenCompose(result -> {
                if (!bridge.isSuccess(result)) {
                    return CompletableFuture.completedFuture(
                        EconomyResult.failure(EconomyResult.Reason.ERROR, "deposit returned " + result));
                }
                return balance(playerId, currency).thenApply(EconomyResult::success);
            });
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(
        UUID playerId,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        if (!isValidAmount(amount)) {
            return invalidAmount();
        }
        return queued(playerId, () -> withdrawChecked(playerId, currency, amount, reason));
    }

    /**
     * Checks the balance and only then withdraws.
     *
     * <p>Callers must already hold this player's queue, so nothing can spend the
     * balance between the check and the withdrawal.
     */
    private CompletableFuture<EconomyResult> withdrawChecked(
        UUID playerId,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        return readBalance(playerId, currency).thenCompose(current -> {
            if (current < amount) {
                return CompletableFuture.completedFuture(overdraft(current, amount));
            }
            return bridge.withdrawAsync(playerId, providerId(currency), amount, reason)
                .handle((result, error) -> error == null && bridge.isSuccess(result))
                .thenCompose(withdrawn -> withdrawn
                    ? confirmNotOverdrawn(playerId, currency, amount, reason)
                    : CompletableFuture.completedFuture(
                        EconomyResult.failure(EconomyResult.Reason.ERROR, "withdraw was refused")));
        });
    }

    /**
     * Hands the amount back if the withdrawal left the player in the red.
     *
     * <p>The balance was in hand a moment ago and this player's own operations are
     * queued, so a negative balance here means something outside this provider spent
     * it in between. Refunding is what keeps one plugin's race from becoming another
     * plugin's loss.
     */
    private CompletableFuture<EconomyResult> confirmNotOverdrawn(
        UUID playerId,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        return readBalance(playerId, currency).thenCompose(after -> {
            if (after >= 0.0) {
                return CompletableFuture.completedFuture(EconomyResult.success(after));
            }
            return refund(playerId, currency, amount, reason + ":overdraft-refund").thenApply(refunded -> {
                if (refunded) {
                    return overdraft(after, amount);
                }
                return EconomyResult.failure(
                    EconomyResult.Reason.ERROR, "overdrawn and the refund was refused");
            });
        });
    }

    private CompletableFuture<Boolean> refund(UUID playerId, CurrencyKey currency, double amount, String reason) {
        return bridge.depositAsync(playerId, providerId(currency), amount, reason)
            .handle((result, error) -> error == null && bridge.isSuccess(result));
    }

    private static EconomyResult overdraft(double balance, double amount) {
        return EconomyResult.failure(
            EconomyResult.Reason.INSUFFICIENT_FUNDS, "balance " + balance + " is under " + amount);
    }

    @Override
    public CompletableFuture<EconomyResult> transfer(
        UUID from,
        UUID to,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        if (!isValidAmount(amount)) {
            return invalidAmount();
        }
        // The sender's queue is held across both halves. Re-entering withdraw or
        // deposit here would wait on the queue this call already owns and never
        // finish, so the unchecked forms are used directly.
        return queued(from, () -> withdrawChecked(from, currency, amount, reason + ":transfer-out")
            .thenCompose(withdrawn -> {
                if (withdrawn instanceof EconomyResult.Failure failure) {
                    return CompletableFuture.completedFuture((EconomyResult) failure);
                }
                return deposit(to, currency, amount, reason + ":transfer-in").thenCompose(deposited -> {
                    if (deposited instanceof EconomyResult.Failure failure) {
                        // Give the money back, otherwise it vanishes from the economy.
                        // Withdrawing again, which is what this used to do, would
                        // charge the sender a second time for one payment.
                        refund(from, currency, amount, reason + ":transfer-refund");
                        return CompletableFuture.completedFuture(EconomyResult.failure(
                            failure.reason(), "refunded sender: " + failure.detail()));
                    }
                    return CompletableFuture.completedFuture(deposited);
                });
            }));
    }

    /**
     * Runs work once every earlier operation on that player has finished.
     *
     * <p>Without this, two payments of the same size issued at the same moment can
     * both read a balance that covers one of them and both withdraw, and the player
     * ends up paying twice for money they had once.
     */
    private CompletableFuture<EconomyResult> queued(UUID playerId, Supplier<CompletableFuture<EconomyResult>> work) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> previous = queues.put(playerId, gate);
        CompletableFuture<Void> start = previous == null
            ? CompletableFuture.completedFuture(null)
            : previous.exceptionally(error -> null);

        CompletableFuture<EconomyResult> result = start.thenCompose(ignored -> work.get());
        result.whenComplete((value, error) -> {
            gate.complete(null);
            // Only drop the entry if this operation is still the tail, otherwise the
            // queue a later operation already installed would be lost.
            queues.remove(playerId, gate);
        });
        return result;
    }

    @Override
    public String format(CurrencyKey currency, double amount) {
        return bridge.format(providerId(currency), amount);
    }

    private String providerId(CurrencyKey currency) {
        String mapped = currencyIds.get(currency);
        return mapped == null ? currency.id() : mapped;
    }

    private static boolean isValidAmount(double amount) {
        return !Double.isNaN(amount) && !Double.isInfinite(amount) && amount > 0.0;
    }

    private static CompletableFuture<EconomyResult> invalidAmount() {
        return CompletableFuture.completedFuture(EconomyResult.failure(EconomyResult.Reason.INVALID_AMOUNT));
    }
}
