package su.nightexpress.excellenteconomy.currency.placeholder;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.currency.CurrencyManager;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.nightcore.bridge.placeholder.PlaceholderProvider;
import su.nightexpress.nightcore.bridge.placeholder.PlaceholderRegistry;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.util.text.night.NightMessage;

public class PlayerBalancePlaceholders implements PlaceholderProvider {

    private final CurrencyRegistry currencyRegistry;
    private final CurrencyManager  manager;

    public PlayerBalancePlaceholders(@NonNull CurrencyRegistry currencyRegistry, @NonNull CurrencyManager manager) {
        this.currencyRegistry = currencyRegistry;
        this.manager = manager;
    }

    @Override
    public void addPlaceholders(@NonNull PlaceholderRegistry registry) {
        registry.addResolver(ExcellentCurrency.class, (player, payload) -> this.currencyRegistry.getById(payload));

        registry.registerMapped("payments_state", ExcellentCurrency.class, (player, currency) -> {
            return CoreLang.STATE_ENABLED_DISALBED.get(this.manager.getPaymentsState(player, currency));
        });

        registry.registerMapped("balance_short_clean", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.stripTags(currency.formatCompact(this.manager.getBalance(player, currency)));
        });

        registry.registerMapped("balance_short_legacy", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.asLegacy(currency.formatCompact(this.manager.getBalance(player, currency)));
        });

        registry.registerMapped("balance_short", ExcellentCurrency.class, (player, currency) -> {
            return currency.formatCompact(this.manager.getBalance(player, currency));
        });

        registry.registerMapped("balance_clean", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.stripTags(currency.format(this.manager.getBalance(player, currency)));
        });

        registry.registerMapped("balance_legacy", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.asLegacy(currency.format(this.manager.getBalance(player, currency)));
        });

        registry.registerMapped("balance_raw", ExcellentCurrency.class, (player, currency) -> {
            return currency.formatRaw(this.manager.getBalance(player, currency));
        });

        registry.registerMapped("balance", ExcellentCurrency.class, (player, currency) -> {
            return currency.format(this.manager.getBalance(player, currency));
        });
    }
}
