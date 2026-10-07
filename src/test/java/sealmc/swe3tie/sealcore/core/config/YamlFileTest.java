package sealmc.swe3tie.sealcore.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFileTest {

    private static final String DEFAULTS = """
        # header comment
        config-version: 1
        # the language
        lang: en.yml
        economy:
          # a number
          min: 1
          max: 100
          currencies:
            money: money
            shards: shards
        """;

    private static Supplier<InputStream> defaults() {
        return () -> new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void createsFileFromDefaults(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config.yml");
        YamlConfiguration config = new YamlFile(path, defaults()).load();

        assertTrue(Files.exists(path));
        assertEquals("en.yml", config.getString("lang"));
        assertEquals(100, config.getInt("economy.max"));
        assertTrue(Files.readString(path).contains("# the language"), "comments are kept");
    }

    @Test
    void addsMissingKeysWithoutTouchingUserValues(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config.yml");
        Files.writeString(path, "lang: vi.yml\neconomy:\n  min: 50\n");

        YamlConfiguration config = new YamlFile(path, defaults()).load();

        assertEquals("vi.yml", config.getString("lang"), "user value kept");
        assertEquals(50, config.getInt("economy.min"), "user value kept");
        assertEquals(100, config.getInt("economy.max"), "missing key restored");
        assertEquals(1, config.getInt("config-version"));

        String written = Files.readString(path);
        assertTrue(written.contains("lang: vi.yml"));
        // config-version was missing on disk, so it is restored along with the defaults' header.
        assertTrue(written.contains("config-version: 1"), "restored key written to disk");
    }

    @Test
    void restoredKeysKeepTheirComments(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config.yml");
        Files.writeString(path, "lang: en.yml\n");

        new YamlFile(path, defaults()).load();

        String written = Files.readString(path);
        assertTrue(written.contains("# a number"), "comment of restored economy.min copied");
    }

    @Test
    void freeSectionEntriesStayDeleted(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config.yml");
        Files.writeString(path, "economy:\n  currencies:\n    money: money\n");

        YamlConfiguration config = new YamlFile(path, defaults(), List.of("economy.currencies")).load();

        assertTrue(config.contains("economy.currencies.money"));
        assertFalse(config.contains("economy.currencies.shards"), "deleted entry must not come back");
    }

    @Test
    void freeSectionIsSeededWhenAbsent(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config.yml");
        Files.writeString(path, "lang: en.yml\n");

        YamlConfiguration config = new YamlFile(path, defaults(), List.of("economy.currencies")).load();

        assertTrue(config.contains("economy.currencies.money"));
        assertTrue(config.contains("economy.currencies.shards"));
    }

    @Test
    void secondLoadIsIdempotent(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config.yml");
        YamlFile file = new YamlFile(path, defaults());
        file.load();
        String first = Files.readString(path);
        file.load();
        assertEquals(first, Files.readString(path));
    }

    @Test
    void invalidYamlThrowsAndLeavesFileUntouched(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config.yml");
        String broken = "lang: [unclosed\n";
        Files.writeString(path, broken);

        assertThrows(ConfigException.class, () -> new YamlFile(path, defaults()).load());
        assertEquals(broken, Files.readString(path));
    }

    @Test
    void loadsWithoutDefaults(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("custom.yml");
        Files.writeString(path, "a: 1\n");
        assertEquals(1, new YamlFile(path, null).load().getInt("a"));
    }
}
