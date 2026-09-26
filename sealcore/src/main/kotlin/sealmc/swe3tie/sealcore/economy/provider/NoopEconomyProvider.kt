package sealmc.swe3tie.sealcore.economy.provider

import sealmc.swe3tie.sealcore.economy.CurrencyKey
import sealmc.swe3tie.sealcore.economy.EconomyProvider
import sealmc.swe3tie.sealcore.economy.EconomyResult
import java.text.DecimalFormat
import java.util.UUID

/**
 * Stand-in used when no economy plugin is installed.
 *
 * Registered unconditionally so feature modules never branch on a null
 * provider; every call comes back as [EconomyResult.Reason.PROVIDER_ABSENT].
 */
class NoopEconomyProvider(
    override val id: String = "none",
    override val displayName: String = "No economy provider",
) : EconomyProvider {

    private val formatter = DecimalFormat("#,##0.##")

    override fun isAvailable(): Boolean = false

    override fun currencies(): Set<String> = emptySet()

    override suspend fun balance(playerId: UUID, currency: CurrencyKey): Double = 0.0

    override suspend fun deposit(
        playerId: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String,
    ): EconomyResult = absent()

    override suspend fun withdraw(
        playerId: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String,
    ): EconomyResult = absent()

    override suspend fun transfer(
        from: UUID,
        to: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String,
    ): EconomyResult = absent()

    override fun format(currency: CurrencyKey, amount: Double): String = formatter.format(amount)

    private fun absent(): EconomyResult =
        EconomyResult.failure(EconomyResult.Reason.PROVIDER_ABSENT, "No economy provider loaded")
}
