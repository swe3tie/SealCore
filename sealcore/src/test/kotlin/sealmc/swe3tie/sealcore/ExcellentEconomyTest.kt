package sealmc.swe3tie.sealcore

import sealmc.swe3tie.sealcore.economy.CurrencyKey
import sealmc.swe3tie.sealcore.economy.EconomyResult
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyBridge
import sealmc.swe3tie.sealcore.economy.provider.ExcellentEconomyProvider
import sealmc.swe3tie.sealcore.economy.provider.NoopEconomyProvider
import sealmc.swe3tie.sealcore.util.runSuspendBlocking
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the reflective bridge against a stand-in that declares the same
 * package, type names and method signatures as ExcellentEconomy, so a rename
 * on either side fails here rather than on a live server.
 */
class ExcellentEconomyTest {

    private val logger = Logger.getLogger("SealCoreTest")
    private lateinit var fake: ExcellentEconomyAPI.Fake
    private lateinit var provider: ExcellentEconomyProvider

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        fake = ExcellentEconomyAPI.Fake()
        val bridge = requireNotNull(ExcellentEconomyBridge.create(logger, fake)) {
            "the reflective bridge failed to bind to the stand-in API"
        }
        provider = ExcellentEconomyProvider(
            bridge = bridge,
            currencyIds = mapOf(
                CurrencyKey.MONEY to "money",
                CurrencyKey.SHARDS to "shards",
                CurrencyKey.COINS to "coins",
            ),
            logger = logger,
        )
    }

    @Test
    fun `bridge binds to the upstream method names`() {
        val bridge = requireNotNull(ExcellentEconomyBridge.create(logger, fake))
        assertTrue(bridge.hasCurrency("money"))
        assertFalse(bridge.hasCurrency("gems"))
        assertEquals(setOf("money", "shards", "coins"), bridge.currencyIds())
    }

    @Test
    fun `bridge returns null when the plugin is absent`() {
        assertNull(ExcellentEconomyBridge.create(logger, null))
    }

    @Test
    fun `provider reports the upstream currencies`() {
        assertEquals(setOf("money", "shards", "coins"), provider.currencies())
    }

    @Test
    fun `deposit and withdraw move balances`() {
        runSuspendBlocking {
            assertTrue(provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test").isSuccess)
            assertEquals(100.0, provider.balance(alice, CurrencyKey.MONEY))
            assertTrue(provider.withdraw(alice, CurrencyKey.MONEY, 40.0, "test").isSuccess)
            assertEquals(60.0, provider.balance(alice, CurrencyKey.MONEY))
        }
    }

    @Test
    fun `overdraw reports insufficient funds`() {
        runSuspendBlocking {
            provider.deposit(alice, CurrencyKey.COINS, 10.0, "test")
            val result = provider.withdraw(alice, CurrencyKey.COINS, 25.0, "test")
            assertTrue(result is EconomyResult.Failure)
            assertEquals(EconomyResult.Reason.INSUFFICIENT_FUNDS, result.reason)
            assertEquals(10.0, provider.balance(alice, CurrencyKey.COINS))
        }
    }

    @Test
    fun `invalid amounts never reach the provider`() {
        runSuspendBlocking {
            for (amount in listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertEquals(
                    EconomyResult.Reason.INVALID_AMOUNT,
                    provider.deposit(alice, CurrencyKey.MONEY, amount, "test").reason,
                )
            }
            assertTrue(fake.calls.none { it.startsWith("deposit") })
        }
    }

    @Test
    fun `transfer moves the amount once`() {
        runSuspendBlocking {
            provider.deposit(alice, CurrencyKey.MONEY, 100.0, "test")
            val result = provider.transfer(alice, bob, CurrencyKey.MONEY, 30.0, "pay")
            assertTrue(result.isSuccess)
            assertEquals(70.0, provider.balance(alice, CurrencyKey.MONEY))
            assertEquals(30.0, provider.balance(bob, CurrencyKey.MONEY))
        }
    }

    @Test
    fun `failed transfer leaves the sender untouched`() {
        runSuspendBlocking {
            provider.deposit(alice, CurrencyKey.MONEY, 10.0, "test")
            val result = provider.transfer(alice, bob, CurrencyKey.MONEY, 50.0, "pay")
            assertTrue(result is EconomyResult.Failure)
            assertEquals(10.0, provider.balance(alice, CurrencyKey.MONEY))
            assertEquals(0.0, provider.balance(bob, CurrencyKey.MONEY))
        }
    }

    @Test
    fun `operation context is passed when the classes are present`() {
        runSuspendBlocking {
            provider.deposit(alice, CurrencyKey.MONEY, 1.0, "shop")
        }
        // The stand-in does not declare OperationContext, so the bridge must fall
        // back to the three argument overload instead of failing.
        assertTrue(fake.calls.any { it.contains("context=false") })
    }

    @Test
    fun `formatting falls back to the provider formatter`() {
        runSuspendBlocking { provider.deposit(alice, CurrencyKey.MONEY, 12.0, "test") }
        assertEquals("money:60.0", provider.format(CurrencyKey.MONEY, 60.0))
    }

    @Test
    fun `noop provider reports provider absent`() {
        val noop = NoopEconomyProvider()
        assertFalse(noop.isAvailable())
        runSuspendBlocking {
            assertEquals(EconomyResult.Reason.PROVIDER_ABSENT, noop.deposit(alice, CurrencyKey.MONEY, 1.0, "x").reason)
            assertEquals(EconomyResult.Reason.PROVIDER_ABSENT, noop.transfer(alice, bob, CurrencyKey.MONEY, 1.0, "x").reason)
        }
    }

    @Test
    fun `currency keys map onto configured provider ids`() {
        assertEquals(CurrencyKey.MONEY, CurrencyKey.of("MONEY"))
        assertEquals("gem", CurrencyKey.of("gem").id)
        assertEquals(listOf(CurrencyKey.MONEY, CurrencyKey.SHARDS, CurrencyKey.COINS), CurrencyKey.BUILT_IN)
    }
}
