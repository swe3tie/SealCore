package sealmc.swe3tie.sealcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine;
import sealmc.swe3tie.sealcore.util.Async;

class PlaceholderEngineTest {

    private final PlaceholderEngine engine = new PlaceholderEngine(null);

    @Test
    void resolvesARegisteredPrefix() {
        engine.register("shop", (viewer, key) -> "item:" + key);
        assertEquals("item:apple", engine.resolve(null, "%shop_apple%"));
    }

    @Test
    void prefixLookupIsCaseInsensitive() {
        engine.register("shop", (viewer, key) -> key.toUpperCase(java.util.Locale.ROOT));
        assertEquals("APPLE", engine.resolve(null, "%SHOP_apple%"));
    }

    @Test
    void resolvesSeveralTokensInOneString() {
        engine.register("shop", (viewer, key) -> key);
        assertEquals("[apple] then [pear]", engine.resolve(null, "[%shop_apple%] then [%shop_pear%]"));
    }

    @Test
    void leavesUnknownTextUntouched() {
        assertEquals("no tokens", engine.resolve(null, "no tokens"));
        assertEquals("%shop_apple%", engine.resolve(null, "%shop_apple%"));
        assertEquals("%nope_key%", engine.resolve(null, "%nope_key%"));
        assertEquals("%no_separator%", engine.resolve(null, "%no_separator%"));
        assertEquals("%dangling", engine.resolve(null, "%dangling"));
        assertEquals("%", engine.resolve(null, "%%"));
    }

    @Test
    void aNullResultFallsThroughToTheRawToken() {
        engine.register("shop", (viewer, key) -> null);
        assertEquals("%shop_apple%", engine.resolve(null, "%shop_apple%"));
    }

    @Test
    void aThrowingResolverNeverBreaksTheCaller() {
        engine.register("boom", (viewer, key) -> {
            throw new IllegalStateException("boom");
        });
        assertEquals("%boom_key%", engine.resolve(null, "%boom_key%"));
    }

    @Test
    void unregisterRemovesTheNamespace() {
        engine.register("shop", (viewer, key) -> key);
        engine.unregister("SHOP");
        assertEquals("%shop_apple%", engine.resolve(null, "%shop_apple%"));
        assertTrue(engine.prefixes().isEmpty());
    }

    @Test
    void formatsFromTheCacheWithoutTouchingTheEconomy() {
        UUID playerId = UUID.randomUUID();
        assertEquals("0", engine.format(TestFixtures.fakePlayer("Tester", playerId), CurrencyKey.MONEY));
        assertEquals(0.0, engine.cachedBalance(playerId, CurrencyKey.MONEY));
    }

    @Test
    void balanceCacheIsPerPlayerAndPerCurrency() {
        var service = EconomyFixture.service();
        var cached = new PlaceholderEngine(service);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        Async.await(service.deposit(alice, CurrencyKey.SHARDS, 25.0, "test"));
        Async.await(service.deposit(bob, CurrencyKey.SHARDS, 7.0, "test"));
        cached.refreshBalance(alice, CurrencyKey.SHARDS);
        cached.refreshBalance(bob, CurrencyKey.SHARDS);

        assertEquals(25.0, cached.cachedBalance(alice, CurrencyKey.SHARDS));
        assertEquals(7.0, cached.cachedBalance(bob, CurrencyKey.SHARDS));
        assertEquals(0.0, cached.cachedBalance(alice, CurrencyKey.MONEY));

        cached.invalidate(alice);
        assertEquals(0.0, cached.cachedBalance(alice, CurrencyKey.SHARDS));
    }

    @Test
    void formatsACachedBalanceThroughTheProvider() {
        var service = EconomyFixture.service();
        var cached = new PlaceholderEngine(service);
        var player = TestFixtures.fakePlayer();
        Async.await(service.deposit(player.getUniqueId(), CurrencyKey.MONEY, 12.5, "test"));
        cached.refreshBalance(player.getUniqueId(), CurrencyKey.MONEY);
        assertEquals("money:12.5", cached.format(player, CurrencyKey.MONEY));
    }
}
