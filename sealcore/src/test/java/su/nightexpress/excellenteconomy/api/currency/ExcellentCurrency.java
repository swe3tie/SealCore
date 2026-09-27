package su.nightexpress.excellenteconomy.api.currency;

/** Test stand-in for ExcellentEconomy's {@code ExcellentCurrency}. */
public interface ExcellentCurrency {

    String getId();

    String formatValue(double amount);

    record Simple(String id) implements ExcellentCurrency {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String formatValue(double amount) {
            return id + ":" + amount;
        }
    }
}
