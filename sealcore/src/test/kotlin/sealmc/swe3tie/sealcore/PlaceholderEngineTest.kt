package sealmc.swe3tie.sealcore

import sealmc.swe3tie.sealcore.economy.CurrencyKey
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine
import sealmc.swe3tie.sealcore.util.runSuspendBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholderEngineTest {

    private val engine = PlaceholderEngine(null)

    @Test
    fun `resolves a registered prefix`() {
        engine.register("shop") { _, key -> "item:$key" }
        assertEquals("item:apple", engine.resolve(null, "%shop_apple%"))
    }

    @Test
    fun `prefix lookup is case insensitive`() {
        engine.register("shop") { _, key -> key.uppercase() }
        assertEquals("APPLE", engine.resolve(null, "%SHOP_apple%"))
    }

    @Test
    fun `resolves several tokens in one string`() {
        engine.register("shop") { _, key -> key }
        assertEquals("[apple] then [pear]", engine.resolve(null, "[%shop_apple%] then [%shop_pear%]"))
    }

    @Test
    fun `leaves unknown text untouched`() {
        assertEquals("no tokens", engine.resolve(null, "no tokens"))
        assertEquals("%shop_apple%", engine.resolve(null, "%shop_apple%"))
        assertEquals("%nope_key%", engine.resolve(null, "%nope_key%"))
        assertEquals("%no_separator%", engine.resolve(null, "%no_separator%"))
        assertEquals("%dangling", engine.resolve(null, "%dangling"))
        assertEquals("%", engine.resolve(null, "%%"))
    }

    @Test
    fun `a null result falls through to the raw token`() {
        engine.register("shop") { _, _ -> null }
        assertEquals("%shop_apple%", engine.resolve(null, "%shop_apple%"))
    }

    @Test
    fun `a throwing resolver never breaks the caller`() {
        engine.register("boom") { _, _ -> throw IllegalStateException("boom") }
        assertEquals("%boom_key%", engine.resolve(null, "%boom_key%"))
    }

    @Test
    fun `unregister removes the namespace`() {
        engine.register("shop") { _, key -> key }
        engine.unregister("SHOP")
        assertEquals("%shop_apple%", engine.resolve(null, "%shop_apple%"))
        assertTrue(engine.prefixes().isEmpty())
    }

    @Test
    fun `formats from the cache without touching the economy`() {
        val playerId = UUID.randomUUID()
        assertEquals("0", engine.format(fakePlayer(id = playerId), CurrencyKey.MONEY))
        assertEquals(0.0, engine.cachedBalance(playerId, CurrencyKey.MONEY))
    }

    @Test
    fun `balance cache is per player and per currency`() {
        val service = EconomyFixture.service()
        val cached = PlaceholderEngine(service)
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        runSuspendBlocking {
            service.deposit(alice, CurrencyKey.SHARDS, 25.0)
            service.deposit(bob, CurrencyKey.SHARDS, 7.0)
            cached.refreshBalance(alice, CurrencyKey.SHARDS)
            cached.refreshBalance(bob, CurrencyKey.SHARDS)
        }

        assertEquals(25.0, cached.cachedBalance(alice, CurrencyKey.SHARDS))
        assertEquals(7.0, cached.cachedBalance(bob, CurrencyKey.SHARDS))
        assertEquals(0.0, cached.cachedBalance(alice, CurrencyKey.MONEY))

        cached.invalidate(alice)
        assertEquals(0.0, cached.cachedBalance(alice, CurrencyKey.SHARDS))
    }

    @Test
    fun `formats a cached balance through the provider`() {
        val service = EconomyFixture.service()
        val cached = PlaceholderEngine(service)
        val player = fakePlayer()
        runSuspendBlocking {
            service.deposit(player.uniqueId, CurrencyKey.MONEY, 12.5)
            cached.refreshBalance(player.uniqueId, CurrencyKey.MONEY)
        }
        assertEquals("money:12.5", cached.format(player, CurrencyKey.MONEY))
    }
}
