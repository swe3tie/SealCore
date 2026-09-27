package sealmc.swe3tie.sealcore.economy;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import sealmc.swe3tie.sealcore.config.SealCoreConfig;
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge;
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyProvider;
import sealmc.swe3tie.sealcore.economy.provider.NoopEconomyProvider;

/**
 * Entry point for every currency operation in SealCore.
 *
 * <p>Holds the single active {@link EconomyProvider} and translates SealCore
 * currency keys into provider specific ids from {@code config.yml}. A provider
 * is always present, so feature modules call this without checking anything
 * first. Every call returns a future: await it on the async scheduler with
 * {@code sealmc.swe3tie.sealcore.util.Async}, never on a server thread.
 */
public final class EconomyService {

    private final SealCoreConfig.Economy config;
    private final Logger logger;

    private EconomyProvider provider = new NoopEconomyProvider();

    public EconomyService(SealCoreConfig.Economy config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public EconomyProvider provider() {
        return provider;
    }

    public boolean isReady() {
        return provider.isAvailable();
    }

    /**
     * Installs the ExcellentEconomy backed provider. Passing null leaves the
     * no-op provider in place, which is the correct state when the plugin is
     * missing.
     *
     * @return whether the provider is usable.
     */
    public boolean install(ExcellentEconomyBridge bridge) {
        if (bridge == null) {
            provider = new NoopEconomyProvider();
            if (config.required()) {
                logger.severe("config.yml sets economy.required=true but ExcellentEconomy is unavailable.");
            } else {
                logger.warning("ExcellentEconomy unavailable; currency features are disabled.");
            }
            return false;
        }

        Map<CurrencyKey, String> mapped = new HashMap<>();
        for (CurrencyKey currency : CurrencyKey.BUILT_IN) {
            mapped.put(currency, config.currencyId(currency.id()));
        }
        ExcellentEconomyProvider excellent = new ExcellentEconomyProvider(bridge, mapped, logger);

        Set<String> known = excellent.currencies();
        Set<String> missing = new LinkedHashSet<>();
        for (String id : mapped.values()) {
            if (!known.isEmpty() && !known.contains(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            logger.warning("ExcellentEconomy does not know these configured currencies: " + String.join(", ", missing));
        }

        provider = excellent;
        logger.info("Economy provider: " + excellent.displayName() + " (" + known.size() + " currencies)");
        return true;
    }

    public Set<String> knownCurrencies() {
        return provider.currencies();
    }

    /**
     * Whether the provider knows the currency this key maps onto.
     *
     * <p>The check is against the mapped id, not the SealCore key: a provider
     * only ever knows the ids {@code config.yml} hands it, so
     * {@code currency: money} mapped onto {@code credits} has to be judged as
     * {@code credits}. A provider that reports no currencies at all cannot be
     * checked, and is taken at its word.
     */
    public boolean hasCurrency(CurrencyKey currency) {
        Set<String> known = provider.currencies();
        return known.isEmpty() || known.contains(providerId(currency));
    }

    public String providerId(CurrencyKey currency) {
        return config.currencyId(currency.id());
    }

    public CompletableFuture<Double> balance(UUID playerId, CurrencyKey currency) {
        return provider.balance(playerId, currency);
    }

    public CompletableFuture<EconomyResult> deposit(UUID playerId, CurrencyKey currency, double amount, String reason) {
        return provider.deposit(playerId, currency, amount, reason);
    }

    public CompletableFuture<EconomyResult> withdraw(UUID playerId, CurrencyKey currency, double amount, String reason) {
        return provider.withdraw(playerId, currency, amount, reason);
    }

    public CompletableFuture<EconomyResult> transfer(
        UUID from,
        UUID to,
        CurrencyKey currency,
        double amount,
        String reason
    ) {
        return provider.transfer(from, to, currency, amount, reason);
    }

    public String format(CurrencyKey currency, double amount) {
        return provider.format(currency, amount);
    }
}
