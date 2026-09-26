package sealmc.swe3tie.sealcore.economy

import sealmc.swe3tie.sealcore.config.SealCoreConfig
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyProvider
import sealmc.swe3tie.sealcore.economy.provider.NoopEconomyProvider
import java.util.UUID
import java.util.logging.Logger

/**
 * Entry point for every currency operation in SealCore.
 *
 * Holds the single active [EconomyProvider] and translates SealCore currency
 * keys into provider specific ids from `config.yml`. A provider is always
 * present, so feature modules call this without checking anything first.
 */
class EconomyService(
    private val config: SealCoreConfig.Economy,
    private val logger: Logger,
) {

    var provider: EconomyProvider = NoopEconomyProvider()
        private set

    val isReady: Boolean get() = provider.isAvailable()

    /**
     * Installs the ExcellentEconomy backed provider. Passing `null` leaves the
     * no-op provider in place, which is the correct state when the plugin is
     * missing.
     */
    fun install(bridge: sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge?): Boolean {
        if (bridge == null) {
            provider = NoopEconomyProvider()
            if (config.required) {
                logger.severe("config.yml sets economy.required=true but ExcellentEconomy is unavailable.")
            } else {
                logger.warning("ExcellentEconomy unavailable; currency features are disabled.")
            }
            return false
        }

        val mapped: Map<CurrencyKey, String> = CurrencyKey.BUILT_IN.associateWith { config.currencyId(it.id) }
        val excellent = ExcellentEconomyProvider(bridge, mapped, logger)

        val known: Set<String> = excellent.currencies()
        val missing: List<String> = mapped.values.filterNot { known.isEmpty() || known.contains(it) }
        if (missing.isNotEmpty()) {
            logger.warning("ExcellentEconomy does not know these configured currencies: ${missing.joinToString()}")
        }

        provider = excellent
        logger.info("Economy provider: ${excellent.displayName} (${known.size} currencies)")
        return true
    }

    fun knownCurrencies(): Set<String> = provider.currencies()

    fun hasCurrency(currency: CurrencyKey): Boolean =
        provider.currencies().let { it.isEmpty() || currency.id in it || providerId(currency) in it }

    fun providerId(currency: CurrencyKey): String = config.currencyId(currency.id)

    suspend fun balance(playerId: UUID, currency: CurrencyKey): Double = provider.balance(playerId, currency)

    suspend fun deposit(
        playerId: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String = "deposit",
    ): EconomyResult = provider.deposit(playerId, currency, amount, reason)

    suspend fun withdraw(
        playerId: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String = "withdraw",
    ): EconomyResult = provider.withdraw(playerId, currency, amount, reason)

    suspend fun transfer(
        from: UUID,
        to: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String = "transfer",
    ): EconomyResult = provider.transfer(from, to, currency, amount, reason)

    fun format(currency: CurrencyKey, amount: Double): String = provider.format(currency, amount)
}
