package su.nightexpress.excellenteconomy.user.data;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;

public class CurrencySettings {

    private boolean paymentsEnabled;

    public CurrencySettings(boolean paymentsEnabled) {
        this.setPaymentsEnabled(paymentsEnabled);
    }

    @NotNull
    public static CurrencySettings create(@NotNull ExcellentCurrency currency) {
        return new CurrencySettings(true);
    }

    public boolean isPaymentsEnabled() {
        return paymentsEnabled;
    }

    public void setPaymentsEnabled(boolean paymentsEnabled) {
        this.paymentsEnabled = paymentsEnabled;
    }
}
