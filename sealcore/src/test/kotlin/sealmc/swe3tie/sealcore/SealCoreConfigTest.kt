package sealmc.swe3tie.sealcore

import org.bukkit.configuration.file.YamlConfiguration
import sealmc.swe3tie.sealcore.config.SealCoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SealCoreConfigTest {

    private fun parse(yaml: String): SealCoreConfig =
        SealCoreConfig.from(YamlConfiguration.loadConfiguration(java.io.StringReader(yaml)))

    @Test
    fun `empty config yields usable defaults`() {
        val config = SealCoreConfig.from(YamlConfiguration.loadConfiguration(java.io.StringReader("")))
        assertEquals(20L, config.general.refreshIntervalTicks)
        assertEquals(6, config.gui.rows)
        assertEquals(SealCoreConfig.Storage.Type.SQLITE, config.storage.type)
        assertFalse(config.economy.required)
    }

    @Test
    fun `reads currency mapping`() {
        val config = parse(
            """
            economy:
              currencies:
                money: vault_money
                shards: premium_shards
                coins: play_coins
              default-currency: shards
            """.trimIndent(),
        )
        assertEquals("vault_money", config.economy.currencyId("money"))
        assertEquals("premium_shards", config.economy.currencyId("shards"))
        assertEquals("play_coins", config.economy.currencyId("coins"))
        assertEquals("shards", config.economy.defaultCurrency)
    }

    @Test
    fun `unknown currency keys pass through`() {
        val config = parse("economy:\n  currencies:\n    money: coins\n")
        assertEquals("gem", config.economy.currencyId("gem"))
    }

    @Test
    fun `parses mysql settings`() {
        val config = parse(
            """
            storage:
              type: MySQL
              host: db.example.com
              port: 3307
              database: seal
              username: mc
              password: secret
              use-ssl: true
              pool-size: 24
            """.trimIndent(),
        )
        assertEquals(SealCoreConfig.Storage.Type.MYSQL, config.storage.type)
        assertEquals("db.example.com", config.storage.host)
        assertEquals(3307, config.storage.port)
        assertEquals(24, config.storage.poolSize)
        assertTrue(config.storage.useSsl)
    }

    @Test
    fun `unknown storage type falls back to sqlite`() {
        assertEquals(SealCoreConfig.Storage.Type.SQLITE, parse("storage:\n  type: postgres\n").storage.type)
    }

    @Test
    fun `gui rows are clamped to a real container`() {
        assertEquals(6, parse("gui:\n  rows: 9\n").gui.rows)
        assertEquals(1, parse("gui:\n  rows: 0\n").gui.rows)
    }
}
