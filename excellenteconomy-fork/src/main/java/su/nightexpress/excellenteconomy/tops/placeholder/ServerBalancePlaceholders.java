package su.nightexpress.excellenteconomy.tops.placeholder;

import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.tops.TopManager;
import su.nightexpress.nightcore.bridge.placeholder.PlaceholderProvider;
import su.nightexpress.nightcore.bridge.placeholder.PlaceholderRegistry;
import su.nightexpress.nightcore.util.text.night.NightMessage;

public class ServerBalancePlaceholders implements PlaceholderProvider {

    private final TopManager topManager;

    public ServerBalancePlaceholders(@NonNull TopManager topManager) {
        this.topManager = topManager;
    }

    @Override
    public void addPlaceholders(@NonNull PlaceholderRegistry registry) {
        registry.registerMapped("server_balance_short_clean", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.stripTags(currency.formatCompact(this.topManager.getTotalBalance(currency)));
        });

        registry.registerMapped("server_balance_short_legacy", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.asLegacy(currency.formatCompact(this.topManager.getTotalBalance(currency)));
        });

        registry.registerMapped("server_balance_short", ExcellentCurrency.class, (player, currency) -> {
            return currency.formatCompact(this.topManager.getTotalBalance(currency));
        });

        registry.registerMapped("server_balance_clean", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.stripTags(currency.format(this.topManager.getTotalBalance(currency)));
        });

        registry.registerMapped("server_balance_legacy", ExcellentCurrency.class, (player, currency) -> {
            return NightMessage.asLegacy(currency.format(this.topManager.getTotalBalance(currency)));
        });

        registry.registerMapped("server_balance_raw", ExcellentCurrency.class, (player, currency) -> {
            return currency.formatRaw(this.topManager.getTotalBalance(currency));
        });

        registry.registerMapped("server_balance", ExcellentCurrency.class, (player, currency) -> {
            return currency.format(this.topManager.getTotalBalance(currency));
        });
    }
}
