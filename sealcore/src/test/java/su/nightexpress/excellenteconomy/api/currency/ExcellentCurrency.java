package su.nightexpress.excellenteconomy.api.currency;

/** Test stand-in for ExcellentEconomy's {@code ExcellentCurrency}. */
public interface ExcellentCurrency {

    String getId();

    String formatValue(Double amount);

    record Simple(String id) implements ExcellentCurrency {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String formatValue(Double amount) {
            return id + ":" + amount;
        }
    }
}
