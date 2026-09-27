package sealmc.swe3tie.sealcore.economy.provider;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

/**
 * Reflective binding to ExcellentEconomy's API.
 *
 * <p>Upstream ExcellentEconomy 2.8.0 is compiled to Java 25 bytecode.
 * Depending on it at compile time would force the whole plugin to Java 25
 * bytecode too, which would lock out 1.21.11 servers pinned to Java 21, so the
 * API is resolved by name at enable time instead. The Phase 2 fork keeps the
 * same class and method names, so nothing here changes when the fork takes
 * over.
 *
 * <p>ExcellentEconomy publishes its API as a Bukkit service. The methods are
 * therefore instance methods, invoked on the registered object rather than
 * statically, which is why the handles below are looked up on the interface but
 * called with {@code apiInstance}.
 */
public final class ExcellentEconomyBridge {

    private static final String API_CLASS = "su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI";
    private static final String CURRENCY_CLASS = "su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency";
    private static final String CONTEXT_CLASS = "su.nightexpress.excellenteconomy.api.currency.operation.OperationContext";
    private static final String EXECUTOR_CLASS = "su.nightexpress.excellenteconomy.api.currency.operation.OperationExecutor";
    private static final String RESULT_CLASS = "su.nightexpress.excellenteconomy.api.currency.operation.OperationResult";

    private static final Object FAILURE_SENTINEL = new Object();

    private final Object apiInstance;
    private final Class<?> operationContext;
    private final Class<?> operationExecutor;
    private final Object successResult;
    private final Logger logger;

    private final Class<?> apiClass;
    private final Method hasCurrencyMethod;
    private final Method getCurrenciesMethod;
    private final Method getCurrencyMethod;
    private final Method getBalanceMethod;
    private final Operation depositOperation;
    private final Operation withdrawOperation;
    private final Method getCurrencyIdMethod;
    private final Method formatValueMethod;

    private ExcellentEconomyBridge(
        Object apiInstance,
        Class<?> operationContext,
        Class<?> operationExecutor,
        Object successResult,
        Logger logger
    ) throws ClassNotFoundException, NoSuchMethodException {
        this.apiInstance = apiInstance;
        this.operationContext = operationContext;
        this.operationExecutor = operationExecutor;
        this.successResult = successResult;
        this.logger = logger;
        this.apiClass = Class.forName(API_CLASS);
        this.hasCurrencyMethod = method("hasCurrency", String.class);
        this.getCurrenciesMethod = method("getCurrencies");
        this.getCurrencyMethod = method("getCurrency", String.class);
        this.getBalanceMethod = method("getBalanceAsync", UUID.class, String.class);
        this.depositOperation = resolveOperation("depositAsync");
        this.withdrawOperation = resolveOperation("withdrawAsync");
        Class<?> currencyClass = loadOrNull(CURRENCY_CLASS);
        this.getCurrencyIdMethod = findMethod(currencyClass, "getId");
        this.formatValueMethod = findMethod(currencyClass, "formatValue", double.class);
    }

    private static final class Operation {
        private final Method threeArg;
        private final Method fourArg;

        private Operation(Method threeArg, Method fourArg) {
            this.threeArg = threeArg;
            this.fourArg = fourArg;
        }
    }

    public boolean hasCurrency(String currencyId) {
        try {
            Object result = hasCurrencyMethod.invoke(apiInstance, currencyId);
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logFailure("hasCurrency", failure);
            return false;
        }
    }

    public Set<String> currencyIds() {
        Object currencies;
        try {
            currencies = getCurrenciesMethod.invoke(apiInstance);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logFailure("getCurrencies", failure);
            return Set.of();
        }
        if (!(currencies instanceof Set<?> set)) {
            return Set.of();
        }

        Set<String> ids = new LinkedHashSet<>();
        if (getCurrencyIdMethod == null) {
            for (Object currency : set) {
                ids.add(String.valueOf(currency));
            }
            return ids;
        }
        for (Object currency : set) {
            try {
                Object id = getCurrencyIdMethod.invoke(currency);
                if (id != null) {
                    ids.add(id.toString());
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // One currency that cannot be read is skipped, not fatal.
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Double> getBalanceAsync(UUID playerId, String currencyId) {
        try {
            return (CompletableFuture<Double>) getBalanceMethod.invoke(apiInstance, playerId, currencyId);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logFailure("getBalanceAsync", failure);
            return CompletableFuture.completedFuture(0.0);
        }
    }

    public CompletableFuture<Object> depositAsync(UUID playerId, String currencyId, double amount, String reason) {
        return invokeOperation(depositOperation, "depositAsync", playerId, currencyId, amount, reason);
    }

    public CompletableFuture<Object> withdrawAsync(UUID playerId, String currencyId, double amount, String reason) {
        return invokeOperation(withdrawOperation, "withdrawAsync", playerId, currencyId, amount, reason);
    }

    public boolean isSuccess(Object result) {
        return result != null && result == successResult;
    }

    public String format(String currencyId, double amount) {
        Object currency;
        try {
            currency = getCurrencyMethod.invoke(apiInstance, currencyId);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return formatNumber(amount);
        }
        if (currency == null || formatValueMethod == null) {
            return formatNumber(amount);
        }
        try {
            Object formatted = formatValueMethod.invoke(currency, amount);
            return formatted instanceof String text ? text : formatNumber(amount);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return formatNumber(amount);
        }
    }

    /**
     * Builds an operation context named after {@code reason} so transactions show
     * up in ExcellentEconomy's operation log attributed to the calling feature.
     * Falls back to the three argument overload when the context classes are
     * missing, which just logs them as "API".
     */
    private Object buildContext(String reason) {
        if (operationContext == null || operationExecutor == null) {
            return null;
        }
        try {
            Object executor = operationExecutor.getMethod("custom", String.class).invoke(null, reason);
            return operationContext.getMethod("of", operationExecutor).invoke(null, executor);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Object> invokeOperation(
        Operation operation,
        String name,
        UUID playerId,
        String currencyId,
        double amount,
        String reason
    ) {
        try {
            Object context = buildContext(reason);
            if (operation.fourArg != null && context != null) {
                return (CompletableFuture<Object>) operation.fourArg.invoke(
                    apiInstance, playerId, currencyId, amount, context);
            }
            return (CompletableFuture<Object>) operation.threeArg.invoke(apiInstance, playerId, currencyId, amount);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logFailure(name, failure);
            return CompletableFuture.completedFuture(FAILURE_SENTINEL);
        }
    }

    private Method method(String name, Class<?>... types) throws NoSuchMethodException {
        return apiClass.getMethod(name, types);
    }

    // Upstream's amount parameter is a primitive double, not java.lang.Double, so
    // every lookup in this class has to ask for double.class. Asking for the wrapper makes
    // getMethod throw and takes the whole bridge down with it, which is why the
    // test stand-in declares a primitive and not a boxed amount either.

    private Operation resolveOperation(String name) throws NoSuchMethodException {
        Method three = method(name, UUID.class, String.class, double.class);
        Method four = operationContext == null ? null : findMethod(apiClass, name, UUID.class, String.class, double.class,
            operationContext);
        return new Operation(three, four);
    }

    private void logFailure(String name, Throwable error) {
        logger.log(Level.WARNING, "ExcellentEconomy call '" + name + "' failed", error);
    }

    private String formatNumber(double amount) {
        return new DecimalFormat("#,##0.##").format(amount);
    }

    private static Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... types) {
        if (type == null) {
            return null;
        }
        try {
            return type.getMethod(name, types);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    /** Resolves the API if the plugin is loaded, otherwise returns null with one warning. */
    public static ExcellentEconomyBridge createOrNull(Logger logger) {
        return create(logger, resolveApiInstance(logger));
    }

    private static Object resolveApiInstance(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("ExcellentEconomy") == null) {
            return null;
        }
        try {
            return Bukkit.getServicesManager().load(Class.forName(API_CLASS));
        } catch (ClassNotFoundException | RuntimeException failure) {
            logger.log(Level.WARNING, "ExcellentEconomy is installed but its API could not be bound", failure);
            return null;
        }
    }

    /**
     * Testable seam: the resolved API object is injected so the reflective
     * contract can be exercised without a running server.
     */
    public static ExcellentEconomyBridge create(Logger logger, Object apiInstance) {
        if (apiInstance == null) {
            logger.info("ExcellentEconomy is not installed; economy features stay disabled.");
            return null;
        }
        try {
            Object success = null;
            for (Object constant : Class.forName(RESULT_CLASS).getEnumConstants()) {
                if (constant instanceof Enum<?> value && value.name().equals("SUCCESS")) {
                    success = constant;
                }
            }
            if (success == null) {
                logger.warning("ExcellentEconomy is installed but its API could not be bound");
                return null;
            }
            return new ExcellentEconomyBridge(
                apiInstance,
                loadOrNull(CONTEXT_CLASS),
                loadOrNull(EXECUTOR_CLASS),
                success,
                logger);
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException failure) {
            logger.log(Level.WARNING, "ExcellentEconomy is installed but its API could not be bound", failure);
            return null;
        }
    }
}
