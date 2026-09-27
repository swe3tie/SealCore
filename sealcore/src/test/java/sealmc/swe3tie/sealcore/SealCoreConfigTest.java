package sealmc.swe3tie.sealcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.config.SealCoreConfig;

class SealCoreConfigTest {

    private static SealCoreConfig parse(String yaml) {
        return SealCoreConfig.from(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    @Test
    void emptyConfigYieldsUsableDefaults() {
        var config = parse("");
        assertEquals(20L, config.general().refreshIntervalTicks());
        assertEquals(6, config.gui().rows());
        assertEquals(SealCoreConfig.Storage.Type.SQLITE, config.storage().type());
        assertFalse(config.economy().required());
    }

    @Test
    void readsCurrencyMapping() {
        var config = parse("""
            economy:
              currencies:
                money: vault_money
                shards: premium_shards
                coins: play_coins
              default-currency: shards
            """);
        assertEquals("vault_money", config.economy().currencyId("money"));
        assertEquals("premium_shards", config.economy().currencyId("shards"));
        assertEquals("play_coins", config.economy().currencyId("coins"));
        assertEquals("shards", config.economy().defaultCurrency());
    }

    @Test
    void unknownCurrencyKeysPassThrough() {
        var config = parse("economy:\n  currencies:\n    money: coins\n");
        assertEquals("gem", config.economy().currencyId("gem"));
    }

    @Test
    void parsesMysqlSettings() {
        var config = parse("""
            storage:
              type: MySQL
              host: db.example.com
              port: 3307
              database: seal
              username: mc
              password: secret
              use-ssl: true
              pool-size: 24
            """);
        assertEquals(SealCoreConfig.Storage.Type.MYSQL, config.storage().type());
        assertEquals("db.example.com", config.storage().host());
        assertEquals(3307, config.storage().port());
        assertEquals(24, config.storage().poolSize());
        assertTrue(config.storage().useSsl());
    }

    @Test
    void unknownStorageTypeFallsBackToSqlite() {
        assertEquals(SealCoreConfig.Storage.Type.SQLITE, parse("storage:\n  type: postgres\n").storage().type());
    }

    @Test
    void guiRowsAreClampedToARealContainer() {
        assertEquals(6, parse("gui:\n  rows: 9\n").gui().rows());
        assertEquals(1, parse("gui:\n  rows: 0\n").gui().rows());
    }
}
