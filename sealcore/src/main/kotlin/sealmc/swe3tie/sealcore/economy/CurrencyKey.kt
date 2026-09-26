package sealmc.swe3tie.sealcore.economy

/**
 * A currency as SealCore sees it, independent of the backing provider.
 *
 * `config.yml` maps each key onto a provider specific currency id, so feature
 * modules only ever pass [MONEY], [SHARDS] or [COINS] around.
 */
data class CurrencyKey(val id: String) {

    val lowerId: String get() = id.lowercase()

    override fun toString(): String = id

    companion object {
        val MONEY = CurrencyKey("money")
        val SHARDS = CurrencyKey("shards")
        val COINS = CurrencyKey("coins")

        val BUILT_IN = listOf(MONEY, SHARDS, COINS)

        fun of(id: String): CurrencyKey = when (id.lowercase()) {
            "money" -> MONEY
            "shards" -> SHARDS
            "coins" -> COINS
            else -> CurrencyKey(id)
        }
    }
}
