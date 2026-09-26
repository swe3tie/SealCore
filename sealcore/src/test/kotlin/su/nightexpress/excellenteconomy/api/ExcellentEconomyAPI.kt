package su.nightexpress.excellenteconomy.api

import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency
import su.nightexpress.excellenteconomy.api.currency.operation.OperationResult
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Test stand-in for ExcellentEconomy's API, declared at the upstream package
 * and type name.
 *
 * ExcellentEconomy registers this interface as a Bukkit service and its methods
 * are instance methods, so this fake does the same. The bridge under test
 * resolves the class by name and invokes the methods reflectively, which pins
 * the exact names and parameter types the plugin depends on without vendoring
 * the third party jar.
 */
interface ExcellentEconomyAPI {

    fun hasCurrency(id: String): Boolean

    fun getCurrencies(): Set<ExcellentCurrency>

    fun getCurrency(id: String): ExcellentCurrency?

    fun getBalanceAsync(playerId: UUID, currencyName: String): CompletableFuture<Double>

    fun depositAsync(playerId: UUID, currencyName: String, amount: Double): CompletableFuture<OperationResult>

    fun depositAsync(
        playerId: UUID,
        currencyName: String,
        amount: Double,
        context: Any,
    ): CompletableFuture<OperationResult>

    fun withdrawAsync(playerId: UUID, currencyId: String, amount: Double): CompletableFuture<OperationResult>

    fun withdrawAsync(
        playerId: UUID,
        currencyId: String,
        amount: Double,
        context: Any,
    ): CompletableFuture<OperationResult>

    /** In-memory backing store so the tests can assert real balances. */
    class Fake : ExcellentEconomyAPI {
        val balances = mutableMapOf<Pair<UUID, String>, Double>()
        val calls = mutableListOf<String>()
        var failEverything = false

        override fun hasCurrency(id: String): Boolean = CURRENCIES.any { it.getId() == id }

        override fun getCurrencies(): Set<ExcellentCurrency> = CURRENCIES

        override fun getCurrency(id: String): ExcellentCurrency? = CURRENCIES.firstOrNull { it.getId() == id }

        override fun getBalanceAsync(playerId: UUID, currencyName: String): CompletableFuture<Double> {
            calls += "getBalanceAsync $currencyName"
            return CompletableFuture.completedFuture(balances[playerId to currencyName] ?: 0.0)
        }

        override fun depositAsync(
            playerId: UUID,
            currencyName: String,
            amount: Double,
        ): CompletableFuture<OperationResult> = apply(playerId, currencyName, amount, "deposit", null)

        override fun depositAsync(
            playerId: UUID,
            currencyName: String,
            amount: Double,
            context: Any,
        ): CompletableFuture<OperationResult> = apply(playerId, currencyName, amount, "deposit", context)

        override fun withdrawAsync(
            playerId: UUID,
            currencyId: String,
            amount: Double,
        ): CompletableFuture<OperationResult> = apply(playerId, currencyId, amount, "withdraw", null)

        override fun withdrawAsync(
            playerId: UUID,
            currencyId: String,
            amount: Double,
            context: Any,
        ): CompletableFuture<OperationResult> = apply(playerId, currencyId, amount, "withdraw", context)

        private fun apply(
            playerId: UUID,
            currencyId: String,
            amount: Double,
            op: String,
            context: Any?,
        ): CompletableFuture<OperationResult> {
            calls += "$op $currencyId $amount context=${context != null}"
            if (failEverything) return CompletableFuture.completedFuture(OperationResult.FAILURE)
            val current = balances[playerId to currencyId] ?: 0.0
            val next = if (op == "withdraw") current - amount else current + amount
            if (next < 0) return CompletableFuture.completedFuture(OperationResult.FAILURE)
            balances[playerId to currencyId] = next
            return CompletableFuture.completedFuture(OperationResult.SUCCESS)
        }
    }

    companion object {
        val CURRENCIES: Set<ExcellentCurrency> = linkedSetOf(
            ExcellentCurrency.Simple("money"),
            ExcellentCurrency.Simple("shards"),
            ExcellentCurrency.Simple("coins"),
        )
    }
}
