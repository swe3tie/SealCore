package su.nightexpress.excellenteconomy.currency;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.currency.impl.AbstractCurrency;
import su.nightexpress.excellenteconomy.currency.impl.NormalCurrency;
import su.nightexpress.excellenteconomy.data.DataHandler;
import su.nightexpress.excellenteconomy.user.UserManager;

import java.nio.file.Path;

public class CurrencyFactory {

    private CurrencyFactory() {
    }

    @NonNull
    public static AbstractCurrency createEconomy(@NonNull Path path,
                                                 @NonNull String id,
                                                 @NonNull ExcellentEconomyAPI plugin,
                                                 @NonNull CurrencyManager currencyManager,
                                                 @NonNull DataHandler dataHandler,
                                                 @NonNull UserManager userManager) {
        return VaultCurrencyFactory.create(path, id, plugin, currencyManager, dataHandler, userManager);
    }

    @NonNull
    public static AbstractCurrency createNormal(@NonNull Path path, @NonNull String id) {
        return new NormalCurrency(path, id);
    }
}
