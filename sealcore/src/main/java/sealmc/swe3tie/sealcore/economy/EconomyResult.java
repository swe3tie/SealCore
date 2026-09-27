package sealmc.swe3tie.sealcore.economy;

/** Outcome of an economy operation. */
public sealed interface EconomyResult {

    record Success(double newBalance) implements EconomyResult {
    }

    record Failure(Reason reason, String detail) implements EconomyResult {
        public Failure(Reason reason) {
            this(reason, null);
        }
    }

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default double newBalance() {
        return this instanceof Success success ? success.newBalance() : 0.0;
    }

    default Reason reason() {
        return this instanceof Failure failure ? failure.reason() : null;
    }

    enum Reason {
        /** The player does not have enough of that currency. */
        INSUFFICIENT_FUNDS,

        /** The provider does not know the requested currency. */
        UNKNOWN_CURRENCY,

        /** No economy provider is loaded, e.g. ExcellentEconomy is missing. */
        PROVIDER_ABSENT,

        /** A plugin cancelled the change through the provider's event. */
        CANCELED,

        /** Amount was zero, negative or not a number. */
        INVALID_AMOUNT,

        /** The provider threw, or the account could not be loaded. */
        ERROR
    }

    static EconomyResult success(double balance) {
        return new Success(balance);
    }

    static EconomyResult failure(Reason reason) {
        return new Failure(reason, null);
    }

    static EconomyResult failure(Reason reason, String detail) {
        return new Failure(reason, detail);
    }
}
