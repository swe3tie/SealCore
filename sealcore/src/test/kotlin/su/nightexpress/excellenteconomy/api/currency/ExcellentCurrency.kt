package su.nightexpress.excellenteconomy.api.currency

/** Test stand-in for ExcellentEconomy's `ExcellentCurrency`. */
interface ExcellentCurrency {
    fun getId(): String
    fun formatValue(amount: Double): String

    data class Simple(private val id: String) : ExcellentCurrency {
        override fun getId(): String = id
        override fun formatValue(amount: Double): String = "$id:$amount"
    }
}
