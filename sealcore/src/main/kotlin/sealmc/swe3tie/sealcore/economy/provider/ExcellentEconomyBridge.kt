package sealmc.swe3tie.sealcore.economy.provider

import org.bukkit.Bukkit
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Reflective binding to ExcellentEconomy's API.
 *
 * Upstream ExcellentEconomy 2.8.0 is compiled to Java 25 bytecode. Depending on
 * it at compile time would force the whole plugin to Java 25 bytecode too, which
 * would lock out 1.21.11 servers pinned to Java 21, so the API is resolved by
 * name at enable time instead. The Phase 2 fork keeps the same class and method
 * names, so nothing here changes when the fork takes over.
 *
 * ExcellentEconomy publishes its API as a Bukkit service:
 * `getServer().getServicesManager().register(ExcellentEconomyAPI.class, this.api, ...)`.
 * The methods are therefore instance methods, invoked on the registered object
 * rather than statically, which is why the handles below are looked up on the
 * interface but called with [apiInstance].
 */
class ExcellentEconomyBridge private constructor(
    private val apiInstance: Any,
    private val operationContext: Class<*>?,
    private val operationExecutor: Class<*>?,
    private val successResult: Any,
    private val logger: Logger,
) {

    private val apiClass: Class<*> = Class.forName(API_CLASS)

    private val hasCurrencyMethod = method("hasCurrency", String::class.java)
    private val getCurrenciesMethod = method("getCurrencies")
    private val getCurrencyMethod = method("getCurrency", String::class.java)
    private val getBalanceMethod = method("getBalanceAsync", UUID::class.java, String::class.java)
    private val depositMethod = resolveOperation("depositAsync")
    private val withdrawMethod = resolveOperation("withdrawAsync")
    private val getCurrencyIdMethod = runCatching {
        Class.forName(CURRENCY_CLASS).getMethod("getId")
    }.getOrNull()
    private val formatValueMethod = runCatching {
        Class.forName(CURRENCY_CLASS).getMethod("formatValue", Double::class.java)
    }.getOrNull()

    private class Operation(val threeArg: Method, val fourArg: Method?)

    private fun method(name: String, vararg types: Class<*>): Method = apiClass.getMethod(name, *types)

    private fun resolveOperation(name: String): Operation {
        val three = method(name, UUID::class.java, String::class.java, Double::class.java)
        val four = operationContext?.let {
            runCatching { method(name, UUID::class.java, String::class.java, Double::class.java, it) }.getOrNull()
        }
        return Operation(three, four)
    }

    fun hasCurrency(currencyId: String): Boolean = runCatching {
        hasCurrencyMethod.invoke(apiInstance, currencyId) as? Boolean ?: false
    }.getOrElse {
        logFailure("hasCurrency", it)
        false
    }

    fun currencyIds(): Set<String> {
        val currencies = runCatching {
            @Suppress("UNCHECKED_CAST")
            getCurrenciesMethod.invoke(apiInstance) as? Set<Any>
        }.getOrElse {
            logFailure("getCurrencies", it)
            return emptySet()
        } ?: return emptySet()

        val getId = getCurrencyIdMethod ?: return currencies.map { it.toString() }.toSet()
        return currencies.mapNotNull { currency ->
            runCatching { getId.invoke(currency) as? String }.getOrNull()
        }.toSet()
    }

    fun getBalanceAsync(playerId: UUID, currencyId: String): CompletableFuture<Double> = runCatching {
        @Suppress("UNCHECKED_CAST")
        getBalanceMethod.invoke(apiInstance, playerId, currencyId) as CompletableFuture<Double>
    }.getOrElse {
        logFailure("getBalanceAsync", it)
        CompletableFuture.completedFuture(0.0)
    }

    fun depositAsync(
        playerId: UUID,
        currencyId: String,
        amount: Double,
        reason: String,
    ): CompletableFuture<Any> = invokeOperation(depositMethod, "depositAsync", playerId, currencyId, amount, reason)

    fun withdrawAsync(
        playerId: UUID,
        currencyId: String,
        amount: Double,
        reason: String,
    ): CompletableFuture<Any> = invokeOperation(withdrawMethod, "withdrawAsync", playerId, currencyId, amount, reason)

    fun isSuccess(result: Any?): Boolean = result != null && result === successResult

    fun format(currencyId: String, amount: Double): String {
        val currency = runCatching { getCurrencyMethod.invoke(apiInstance, currencyId) }.getOrNull()
            ?: return formatNumber(amount)
        val formatter = formatValueMethod ?: return formatNumber(amount)
        return runCatching { formatter.invoke(currency, amount) as? String ?: formatNumber(amount) }
            .getOrElse { formatNumber(amount) }
    }

    /**
     * Builds an [OperationContext] named after [reason] so transactions show up
     * in ExcellentEconomy's operation log attributed to the calling feature.
     * Falls back to the three argument overload when the context classes are
     * missing, which just logs them as "API".
     */
    private fun buildContext(reason: String): Any? {
        val contextClass = operationContext ?: return null
        val executorClass = operationExecutor ?: return null
        return runCatching {
            val executor = executorClass.getMethod("custom", String::class.java).invoke(null, reason)
            contextClass.getMethod("of", executorClass).invoke(null, executor)
        }.getOrNull()
    }

    private fun invokeOperation(
        operation: Operation,
        name: String,
        playerId: UUID,
        currencyId: String,
        amount: Double,
        reason: String,
    ): CompletableFuture<Any> = runCatching {
        val context = buildContext(reason)
        val fourArg = operation.fourArg
        @Suppress("UNCHECKED_CAST")
        if (fourArg != null && context != null) {
            fourArg.invoke(apiInstance, playerId, currencyId, amount, context) as CompletableFuture<Any>
        } else {
            operation.threeArg.invoke(apiInstance, playerId, currencyId, amount) as CompletableFuture<Any>
        }
    }.getOrElse {
        logFailure(name, it)
        CompletableFuture.completedFuture(FAILURE_SENTINEL)
    }

    private fun logFailure(name: String, error: Throwable) {
        logger.log(Level.WARNING, "ExcellentEconomy call '$name' failed", error)
    }

    private fun formatNumber(amount: Double): String = java.text.DecimalFormat("#,##0.##").format(amount)

    companion object {
        private const val API_CLASS = "su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI"
        private const val CURRENCY_CLASS = "su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency"
        private const val CONTEXT_CLASS = "su.nightexpress.excellenteconomy.api.currency.operation.OperationContext"
        private const val EXECUTOR_CLASS = "su.nightexpress.excellenteconomy.api.currency.operation.OperationExecutor"
        private const val RESULT_CLASS = "su.nightexpress.excellenteconomy.api.currency.operation.OperationResult"

        private val FAILURE_SENTINEL = Any()

        /** Resolves the API if the plugin is loaded, otherwise returns null with one warning. */
        fun createOrNull(logger: Logger): ExcellentEconomyBridge? = create(logger, resolveApiInstance(logger))

        private fun resolveApiInstance(logger: Logger): Any? {
            if (Bukkit.getPluginManager().getPlugin("ExcellentEconomy") == null) return null
            return runCatching {
                Bukkit.getServicesManager().load(Class.forName(API_CLASS))
            }.onFailure {
                logger.log(Level.WARNING, "ExcellentEconomy is installed but its API could not be bound", it)
            }.getOrNull()
        }

        /**
         * Testable seam: the resolved API object is injected so the reflective
         * contract can be exercised without a running server.
         */
        internal fun create(logger: Logger, apiInstance: Any?): ExcellentEconomyBridge? {
            if (apiInstance == null) {
                logger.info("ExcellentEconomy is not installed; economy features stay disabled.")
                return null
            }
            return runCatching {
                val resultClass = Class.forName(RESULT_CLASS)
                val success = resultClass.enumConstants.first { (it as Enum<*>).name == "SUCCESS" }
                ExcellentEconomyBridge(
                    apiInstance = apiInstance,
                    operationContext = runCatching { Class.forName(CONTEXT_CLASS) }.getOrNull(),
                    operationExecutor = runCatching { Class.forName(EXECUTOR_CLASS) }.getOrNull(),
                    successResult = success,
                    logger = logger,
                )
            }.onFailure {
                logger.log(Level.WARNING, "ExcellentEconomy is installed but its API could not be bound", it)
            }.getOrNull()
        }

        suspend fun <T> await(future: CompletableFuture<T>): T = suspendCoroutine { continuation ->
            future.whenComplete { value, error ->
                if (error != null) continuation.resumeWithException(error) else continuation.resume(value)
            }
        }
    }
}
