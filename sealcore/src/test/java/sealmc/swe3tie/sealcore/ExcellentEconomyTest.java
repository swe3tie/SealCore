package sealmc.swe3tie.sealcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.economy.EconomyResult;
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge;
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyProvider;
import sealmc.swe3tie.sealcore.economy.provider.NoopEconomyProvider;
import sealmc.swe3tie.sealcore.util.Async;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.api.currency.operation.NotificationTarget;

/**
 * Exercises the reflective bridge against a stand-in that declares the same package,
 * type names and method signatures as ExcellentEconomy, so a rename on either side
 * fails here rather than on a live server.
 */
class ExcellentEconomyTest {

    private static final Logger LOGGER = Logger.getLogger("SealCoreTest");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private ExcellentEconomyAPI.Fake fake;
    private ExcellentEconomyProvider provider;

    @BeforeEach
    void setUp() {
        fake = new ExcellentEconomyAPI.Fake();
        ExcellentEconomyBridge bridge = ExcellentEconomyBridge.create(LOGGER, fake);
        assertNotNull(bridge, "the reflective bridge failed to bind to the stand-in API");
        provider = new ExcellentEconomyProvider(bridge, Map.of(
            CurrencyKey.MONEY, "money",
            CurrencyKey.SHARDS, "shards",
            CurrencyKey.COINS, "coins"), LOGGER);
    }

    @Test
    void bridgeBindsToTheUpstreamMethodNames() {
        var bridge = ExcellentEconomyBridge.create(LOGGER, fake);
        assertNotNull(bridge);
        assertTrue(bridge.hasCurrency("money"));
        assertFalse(bridge.hasCurrency("gems"));
        assertEquals(Set.of("money", "shards", "coins"), bridge.currencyIds());
    }

    @Test
    void bridgeReturnsNullWhenThePluginIsAbsent() {
        assertNull(ExcellentEconomyBridge.create(LOGGER, null));
    }

    @Test
    void providerReportsTheUpstreamCurrencies() {
        assertEquals(Set.of("money", "shards", "coins"), provider.currencies());
    }

    @Test
    void depositAndWithdrawMoveBalances() {
        assertTrue(Async.await(provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test")).isSuccess());
        assertEquals(100.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
        assertTrue(Async.await(provider.withdraw(alice, CurrencyKey.MONEY, 40.0, "test")).isSuccess());
        assertEquals(60.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
    }

    @Test
    void overdrawReportsInsufficientFunds() {
        Async.await(provider.deposit(alice, CurrencyKey.COINS, 10.0, "test"));
        var result = Async.await(provider.withdraw(alice, CurrencyKey.COINS, 25.0, "test"));
        assertTrue(result instanceof EconomyResult.Failure);
        assertEquals(EconomyResult.Reason.INSUFFICIENT_FUNDS, result.reason());
        assertEquals(10.0, Async.await(provider.balance(alice, CurrencyKey.COINS)));
    }

    @Test
    void invalidAmountsNeverReachTheProvider() {
        for (double amount : List.of(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(
                EconomyResult.Reason.INVALID_AMOUNT,
                Async.await(provider.deposit(alice, CurrencyKey.MONEY, amount, "test")).reason());
        }
        assertTrue(fake.calls.stream().noneMatch(call -> call.startsWith("deposit")));
    }

    @Test
    void transferMovesTheAmountOnce() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test"));
        assertTrue(Async.await(provider.transfer(alice, bob, CurrencyKey.MONEY, 30.0, "pay")).isSuccess());
        assertEquals(70.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
        assertEquals(30.0, Async.await(provider.balance(bob, CurrencyKey.MONEY)));
    }

    @Test
    void failedTransferLeavesTheSenderUntouched() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 10.0, "test"));
        var result = Async.await(provider.transfer(alice, bob, CurrencyKey.MONEY, 50.0, "pay"));
        assertTrue(result instanceof EconomyResult.Failure);
        assertEquals(10.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
        assertEquals(0.0, Async.await(provider.balance(bob, CurrencyKey.MONEY)));
    }

    /**
     * The stand-in now behaves like the real API and lets a balance go negative, so
     * the provider has to be the one that refuses. This is the regression that let
     * /pay hand out money nobody had.
     */
    @Test
    void transferRefusesToOverdrawAndMintsNothing() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 25.0, "test"));
        var result = Async.await(provider.transfer(alice, bob, CurrencyKey.MONEY, 60.0, "pay"));
        assertTrue(result instanceof EconomyResult.Failure);
        assertEquals(EconomyResult.Reason.INSUFFICIENT_FUNDS, result.reason());
        assertEquals(25.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
        assertEquals(0.0, Async.await(provider.balance(bob, CurrencyKey.MONEY)));
    }

    @Test
    void withdrawingMoreThanTheBalanceIsRefusedAndLeavesTheBalance() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 12.0, "test"));
        var result = Async.await(provider.withdraw(alice, CurrencyKey.MONEY, 50.0, "test"));
        assertTrue(result instanceof EconomyResult.Failure);
        assertEquals(EconomyResult.Reason.INSUFFICIENT_FUNDS, result.reason());
        assertEquals(12.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
    }

    @Test
    void payingExactlyTheBalanceIsAllowed() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 25.0, "test"));
        assertTrue(Async.await(provider.transfer(alice, bob, CurrencyKey.MONEY, 25.0, "pay")).isSuccess());
        assertEquals(0.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
        assertEquals(25.0, Async.await(provider.balance(bob, CurrencyKey.MONEY)));
    }

    /**
     * Two payments that each fit on their own must not both pass the balance check.
     * Without the per player queue both read 100, both withdraw 80, and the server
     * creates 60 out of nothing.
     */
    @Test
    void concurrentPaymentsCannotBothSpendTheSameBalance() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test"));

        var first = provider.transfer(alice, bob, CurrencyKey.MONEY, 80.0, "pay");
        var second = provider.transfer(alice, bob, CurrencyKey.MONEY, 80.0, "pay");
        var results = List.of(Async.await(first), Async.await(second));

        assertEquals(1, results.stream().filter(EconomyResult::isSuccess).count(),
            "exactly one of the two payments should have gone through: " + results);
        assertEquals(20.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)));
        assertEquals(80.0, Async.await(provider.balance(bob, CurrencyKey.MONEY)));
    }

    /**
     * A transfer whose deposit half fails has to hand the money back by depositing it.
     * Withdrawing again, which is what this used to do, charged the sender twice.
     */
    @Test
    void failedDepositGivesTheSenderTheirMoneyBackOnce() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test"));
        fake.failDepositsFor = bob;
        var result = Async.await(provider.transfer(alice, bob, CurrencyKey.MONEY, 40.0, "pay"));
        fake.failDepositsFor = null;

        assertTrue(result instanceof EconomyResult.Failure);
        assertEquals(100.0, Async.await(provider.balance(alice, CurrencyKey.MONEY)),
            "the sender is paid once and refunded once, so they end where they started");
        assertEquals(0.0, Async.await(provider.balance(bob, CurrencyKey.MONEY)));
    }

    @Test
    void aTransactionCarriesAnOperationContext() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 1.0, "shop"));
        // Falls back to the three argument overload only if the context classes
        // are missing, which would cost the transaction its name in the log.
        assertTrue(fake.calls.stream().anyMatch(call -> call.contains("context=true")));
        assertNotNull(fake.lastContext);
        assertEquals("shop", fake.lastContext.getExecutor().getName());
    }

    @Test
    void excellentEconomyStaysQuietInChatButKeepsTheOperationLog() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test"));
        Async.await(provider.transfer(alice, bob, CurrencyKey.MONEY, 1.0, "pay"));

        // The default context notifies the player, so every /pay used to print
        // ExcellentEconomy's "1 has been taken from your account!" right above
        // SealCore's own message.
        assertNotNull(fake.lastContext);
        assertFalse(fake.lastContext.shouldNotify(NotificationTarget.USER));
        assertFalse(fake.lastContext.shouldNotify(NotificationTarget.EXECUTOR));

        // The loggers are a different target and must survive the silencing.
        assertTrue(fake.lastContext.shouldNotify(NotificationTarget.FILE_LOGGER));
        assertTrue(fake.lastContext.shouldNotify(NotificationTarget.CONSOLE_LOGGER));
    }

    @Test
    void aPaymentCarriesAContextOnBothHalves() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test"));
        fake.calls.clear();
        assertTrue(Async.await(provider.transfer(alice, bob, CurrencyKey.MONEY, 30.0, "pay")).isSuccess());

        // The withdrawal and the deposit both went through, so both carried a
        // context rather than one traced half and one anonymous one.
        assertEquals(2, fake.calls.stream().filter(call -> call.contains("context=true")).count());
        assertFalse(fake.calls.stream().anyMatch(call -> call.contains("context=false")));
    }

    @Test
    void formattingFallsBackToTheProviderFormatter() {
        Async.await(provider.deposit(alice, CurrencyKey.MONEY, 12.0, "test"));
        assertEquals("money:60.0", provider.format(CurrencyKey.MONEY, 60.0));
    }

    @Test
    void noopProviderReportsProviderAbsent() {
        var noop = new NoopEconomyProvider();
        assertFalse(noop.isAvailable());
        assertEquals(
            EconomyResult.Reason.PROVIDER_ABSENT,
            Async.await(noop.deposit(alice, CurrencyKey.MONEY, 1.0, "x")).reason());
        assertEquals(
            EconomyResult.Reason.PROVIDER_ABSENT,
            Async.await(noop.transfer(alice, bob, CurrencyKey.MONEY, 1.0, "x")).reason());
    }

    @Test
    void currencyKeysMapOntoConfiguredProviderIds() {
        assertEquals(CurrencyKey.MONEY, CurrencyKey.of("MONEY"));
        assertEquals("gem", CurrencyKey.of("gem").id());
        assertEquals(List.of(CurrencyKey.MONEY, CurrencyKey.SHARDS, CurrencyKey.COINS), CurrencyKey.BUILT_IN);
    }
}
