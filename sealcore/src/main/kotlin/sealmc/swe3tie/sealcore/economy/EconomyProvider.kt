package sealmc.swe3tie.sealcore.economy

import java.util.UUID

/**
 * Everything SealCore needs from a currency backend.
 *
 * Implementations must be safe to call from any thread: the suspend functions
 * are awaited on the calling thread and must not block it. A provider that is
 * absent still has to be registered, so callers never deal with `null`; it
 * reports [EconomyResult.Reason.PROVIDER_ABSENT] instead.
 */
interface EconomyProvider {

    val id: String

    val displayName: String

    fun isAvailable(): Boolean

    /** Currency ids the provider knows about. */
    fun currencies(): Set<String>

    suspend fun balance(playerId: UUID, currency: CurrencyKey): Double

    suspend fun deposit(playerId: UUID, currency: CurrencyKey, amount: Double, reason: String): EconomyResult

    suspend fun withdraw(playerId: UUID, currency: CurrencyKey, amount: Double, reason: String): EconomyResult

    suspend fun transfer(
        from: UUID,
        to: UUID,
        currency: CurrencyKey,
        amount: Double,
        reason: String,
    ): EconomyResult

    fun format(currency: CurrencyKey, amount: Double): String
}
