package excellenteconomy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationResult;

/**
 * Pins the API surface {@code SealCore} binds to by reflection.
 *
 * <p>SealCore declares no dependency on ExcellentEconomy. It resolves
 * {@code ExcellentEconomyAPI} with {@code Class.forName} and then looks each
 * method up by name and parameter type, so a rename or a change from {@code double}
 * to {@link Double} upstream would not fail a compile, only a live server, with the
 * whole bridge silently falling back to the no-op provider.
 *
 * <p>SealCore's own unit tests exercise that reflection against a hand written
 * stand-in declared at the upstream package. This test closes the gap: it checks the
 * stand-in's assumptions against the real interface, which is only on this module's
 * classpath.
 */
class ApiSurfaceTest {

    private static final Class<?> CURRENCY = ExcellentCurrency.class;
    private static final Class<?> CONTEXT =
        su.nightexpress.excellenteconomy.api.currency.operation.OperationContext.class;

    @Test
    void theCurrencyLookupMethodsSealCoreCallsExist() {
        assertDoesNotThrow(() -> ExcellentEconomyAPI.class.getMethod("hasCurrency", String.class));
        assertDoesNotThrow(() -> ExcellentEconomyAPI.class.getMethod("getCurrencies"));
        assertDoesNotThrow(() -> ExcellentEconomyAPI.class.getMethod("getCurrency", String.class));
        assertDoesNotThrow(() -> ExcellentEconomyAPI.class.getMethod("getBalanceAsync", UUID.class, String.class));
        assertDoesNotThrow(() -> ExcellentCurrency.class.getMethod("getId"));
    }

    @Test
    void balanceLookupReturnsAFutureOfABoxedDouble() {
        Method method = assertDoesNotThrow(
            () -> ExcellentEconomyAPI.class.getMethod("getBalanceAsync", UUID.class, String.class));
        assertEquals(CompletableFuture.class, method.getReturnType());
        // The return is boxed even though the amount parameters are not.
        var arguments = ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments();
        assertEquals(Double.class, arguments[0]);
    }

    /**
     * The one that bit us. {@code getMethod} takes a primitive and its wrapper as
     * different parameters, and upstream declares the amount as a primitive, so a
     * lookup by {@link Double} throws and the bridge never binds.
     */
    @Test
    void theAmountParameterIsAPrimitiveDoubleNotTheWrapper() throws NoSuchMethodException {
        assertDoesNotThrow(() -> ExcellentEconomyAPI.class.getMethod("depositAsync", UUID.class, String.class, double.class));
        assertDoesNotThrow(() -> ExcellentEconomyAPI.class.getMethod("withdrawAsync", UUID.class, String.class, double.class));
        assertDoesNotThrow(() -> CURRENCY.getMethod("formatValue", double.class));

        assertThrows(NoSuchMethodException.class,
            () -> ExcellentEconomyAPI.class.getMethod("depositAsync", UUID.class, String.class, Double.class));
        assertThrows(NoSuchMethodException.class,
            () -> ExcellentEconomyAPI.class.getMethod("withdrawAsync", UUID.class, String.class, Double.class));
        assertThrows(NoSuchMethodException.class, () -> CURRENCY.getMethod("formatValue", Double.class));
    }

    @Test
    void theFourArgumentOverloadsTakeAnOperationContext() {
        assertDoesNotThrow(
            () -> ExcellentEconomyAPI.class.getMethod("depositAsync", UUID.class, String.class, double.class, CONTEXT));
        assertDoesNotThrow(
            () -> ExcellentEconomyAPI.class.getMethod("withdrawAsync", UUID.class, String.class, double.class, CONTEXT));
    }

    @Test
    void theResultEnumStillHasSuccessAndFailure() {
        assertEquals(2, OperationResult.values().length);
        assertEquals("SUCCESS", OperationResult.SUCCESS.name());
        assertEquals("FAILURE", OperationResult.FAILURE.name());
    }

    @Test
    void theApiIsRegisteredAsABukkitService() {
        // SealCore resolves the interface through the services manager, so the name
        // it looks up has to be the interface itself, not an implementation.
        assertTrue(ExcellentEconomyAPI.class.isInterface());
    }
}
