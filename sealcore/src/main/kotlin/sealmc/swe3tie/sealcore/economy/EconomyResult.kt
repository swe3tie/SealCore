package sealmc.swe3tie.sealcore.economy

/** Outcome of an economy operation. */
sealed interface EconomyResult {

    data class Success(override val newBalance: Double) : EconomyResult

    data class Failure(override val reason: Reason, val detail: String? = null) : EconomyResult

    val isSuccess: Boolean get() = this is Success

    val newBalance: Double
        get() = (this as? Success)?.newBalance ?: 0.0

    val reason: Reason?
        get() = (this as? Failure)?.reason

    enum class Reason {
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
        ERROR,
    }

    companion object {
        fun success(balance: Double): Success = Success(balance)

        fun failure(reason: Reason, detail: String? = null): Failure = Failure(reason, detail)
    }
}
