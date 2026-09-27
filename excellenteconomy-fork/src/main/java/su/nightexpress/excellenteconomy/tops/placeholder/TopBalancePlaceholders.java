package su.nightexpress.excellenteconomy.tops.placeholder;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.excellenteconomy.tops.TopEntry;
import su.nightexpress.excellenteconomy.tops.TopManager;
import su.nightexpress.nightcore.bridge.placeholder.PlaceholderProvider;
import su.nightexpress.nightcore.bridge.placeholder.PlaceholderRegistry;
import su.nightexpress.nightcore.util.Numbers;
import su.nightexpress.nightcore.util.text.night.NightMessage;

import java.util.List;
import java.util.Optional;

public class TopBalancePlaceholders implements PlaceholderProvider {

    private final CurrencyRegistry currencyRegistry;
    private final TopManager       manager;

    public TopBalancePlaceholders(@NonNull CurrencyRegistry currencyRegistry, @NonNull TopManager manager) {
        this.currencyRegistry = currencyRegistry;
        this.manager = manager;
    }

    @Override
    public void addPlaceholders(@NonNull PlaceholderRegistry registry) {
        registry.registerMapped("leaderboard_position", ExcellentCurrency.class, (player, currency) -> {
            return Optional.ofNullable(this.manager.getTopEntry(currency, player.getName())).map(TopEntry::getPosition)
                .map(String::valueOf).orElse("?");
        });

        this.addTopPlaceholder(registry, "top_balance_short_clean", (entry, currency, position) -> NightMessage
            .stripTags(currency.formatCompact(entry.getBalance())));
        this.addTopPlaceholder(registry, "top_balance_short_legacy", (entry, currency, position) -> NightMessage
            .asLegacy(currency.formatCompact(entry.getBalance())));
        this.addTopPlaceholder(registry, "top_balance_short", (entry, currency, position) -> currency.formatCompact(
            entry.getBalance()));

        this.addTopPlaceholder(registry, "top_balance_clean", (entry, currency, position) -> NightMessage.stripTags(
            currency.formatCompact(entry.getBalance())));
        this.addTopPlaceholder(registry, "top_balance_legacy", (entry, currency, position) -> NightMessage.asLegacy(
            currency.format(entry.getBalance())));
        this.addTopPlaceholder(registry, "top_balance_raw", (entry, currency, position) -> currency.formatRaw(entry
            .getBalance()));
        this.addTopPlaceholder(registry, "top_balance", (entry, currency, position) -> currency.format(entry
            .getBalance()));

        this.addTopPlaceholder(registry, "top_player", (entry, currency, position) -> entry.getName());
        this.addTopPlaceholder(registry, "top_player_name", (entry, currency, position) -> entry.getName());
        this.addTopPlaceholder(registry, "top_player_uuid", (entry, currency, position) -> entry.getPlayerId()
            .toString());
    }

    private void addTopPlaceholder(@NonNull PlaceholderRegistry registry, @NonNull String key,
                                   @NonNull TopEntryPlaceholder placeholder) {
        registry.registerRaw(key, (player, payload) -> {
            int index = payload.indexOf('_');
            if (index < 0) return null;

            String posRaw = payload.substring(0, index);
            String currencyId = payload.substring(index + 1);

            ExcellentCurrency currency = this.currencyRegistry.getById(currencyId);
            if (currency == null) return null;

            int position = Numbers.getIntegerAbs(posRaw);
            if (position <= 0) return null;

            List<TopEntry> baltop = this.manager.getTopEntries(currency);
            if (position > baltop.size()) return Lang.OTHER_NO_TOP_ENTRY.text();

            TopEntry topEntry = baltop.get(position - 1);

            return placeholder.parse(topEntry, currency, position);
        });
    }

    @FunctionalInterface
    public interface TopEntryPlaceholder {

        @NonNull
        String parse(@NonNull TopEntry entry, @NonNull ExcellentCurrency currency, int position);
    }
}
