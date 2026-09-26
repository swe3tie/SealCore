package sealmc.swe3tie.sealcore.economy.provider

import sealmc.swe3tie.sealcore.economy.CurrencyKey
import sealmc.swe3tie.sealcore.economy.EconomyProvider
import sealmc.swe3tie.sealcore.economy.EconomyResult
import java.util.UUID
import java.util.logging.Logger
import kotlin.math.abs

/**
 * [EconomyProvider] backed by ExcellentEconomy.
 *
 * Amounts are validated here so an obviously bad request never reaches the
 * provider, and a failed withdrawal during a transfer refunds the sender
 * instead of minting money.
 */
class ExcellentEconomyProvider(
    private val bridge: ExcellentEconomyBridge,
    private val currencyIds: Map<CurrencyKey, String>,
    private val logger: Logger,
    override val displayName: String = "ExcellentEconomy",
) : EconomyProvider {

    override val id: String = "excellenteconomy"

    override fun isAvailable(): Boolean = true

    override fun currencies(): Set<String> = bridge.currencyIds()

    override suspend fun balance(playerId: UUID, currency: CurrencyKey): Double = runCatching {
        ExcellentEconomyBridge.await(bridge.getBalanceAsync(playerId, providerId(currency)))
    }.getOrElse {
        logger.warning("Balance lookup failed for $playerId: ${it.message}")
        0.0
    }

    override suspend fun deposit(
        playerId: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String,
    ): EconomyResult {
        if (!isValidAmount(amount)) return invalidAmount()
        val result = ExcellentEconomyBridge.await(bridge.depositAsync(playerId, providerId(currency), amount, reason))
        return if (bridge.isSuccess(result)) {
            EconomyResult.success(balance(playerId, currency))
        } else {
            EconomyResult.failure(EconomyResult.Reason.ERROR, "deposit returned $result")
        }
    }

    override suspend fun withdraw(
        playerId: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String,
    ): EconomyResult {
        if (!isValidAmount(amount)) return invalidAmount()
        val result = ExcellentEconomyBridge.await(bridge.withdrawAsync(playerId, providerId(currency), amount, reason))
        return if (bridge.isSuccess(result)) {
            EconomyResult.success(balance(playerId, currency))
        } else {
            // The provider does not tell us why it refused, so an overdraw is
            // reported as insufficient funds; anything else is a generic error.
            val current = balance(playerId, currency)
            if (current < abs(amount)) {
                EconomyResult.failure(EconomyResult.Reason.INSUFFICIENT_FUNDS)
            } else {
                EconomyResult.failure(EconomyResult.Reason.ERROR, "withdraw returned $result")
            }
        }
    }

    override suspend fun transfer(
        from: UUID,
        to: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String,
    ): EconomyResult {
        if (!isValidAmount(amount)) return invalidAmount()
        val withdrawn = withdraw(from, currency, amount, "$reason:transfer-out")
        if (withdrawn is EconomyResult.Failure) return withdrawn

        val deposited = deposit(to, currency, amount, "$reason:transfer-in")
        if (deposited is EconomyResult.Failure) {
            // Roll back, otherwise the amount disappears from the economy.
            withdraw(from, currency, amount, "$reason:transfer-refund")
            return EconomyResult.failure(deposited.reason, "refunded sender: ${deposited.detail}")
        }
        return EconomyResult.success(balance(to, currency))
    }

    override fun format(currency: CurrencyKey, amount: Double): String = bridge.format(providerId(currency), amount)

    private fun providerId(currency: CurrencyKey): String = currencyIds[currency] ?: currency.id

    private fun isValidAmount(amount: Double): Boolean =
        !amount.isNaN() && !amount.isInfinite() && amount > 0.0

    private fun invalidAmount(): EconomyResult =
        EconomyResult.failure(EconomyResult.Reason.INVALID_AMOUNT)
}
