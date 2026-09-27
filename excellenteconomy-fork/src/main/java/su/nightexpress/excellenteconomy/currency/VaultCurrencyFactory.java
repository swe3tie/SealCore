package su.nightexpress.excellenteconomy.currency;

import org.jspecify.annotations.NonNull;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.currency.impl.AbstractCurrency;
import su.nightexpress.excellenteconomy.currency.impl.EconomyCurrency;
import su.nightexpress.excellenteconomy.data.DataHandler;
import su.nightexpress.excellenteconomy.user.UserManager;

import java.nio.file.Path;

/**
 * Builds the Vault backed currency.
 *
 * Lives apart from {@link CurrencyFactory} on purpose. {@code EconomyCurrency}
 * implements {@code net.milkbowl.vault.economy.Economy}, so the verifier has to
 * resolve Vault to link this class. The verifier checks every method of a class it
 * links, and {@code CurrencyManager} links {@code CurrencyFactory} on the very
 * first currency, so upstream 2.8.0 threw {@code NoClassDefFoundError} while
 * enabling on any server without Vault installed, even though Vault is declared
 * optional in both plugin descriptors. Nothing on a Vault-less server runs this
 * class, and the one call site is behind {@code Plugins.isInstalled(Vault)}, so
 * it is only ever linked once Vault is really there.
 */
final class VaultCurrencyFactory {

    private VaultCurrencyFactory() {
    }

    @NonNull
    static AbstractCurrency create(@NonNull Path path,
                                   @NonNull String id,
                                   @NonNull ExcellentEconomyAPI plugin,
                                   @NonNull CurrencyManager currencyManager,
                                   @NonNull DataHandler dataHandler,
                                   @NonNull UserManager userManager) {
        return new EconomyCurrency(path, id, plugin, currencyManager, dataHandler, userManager);
    }
}
