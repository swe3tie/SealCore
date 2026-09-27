package sealmc.swe3tie.sealcore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Guards the shipped resources against each other: a translator needs both language
 * files to carry the same keys, and a permission that plugin.yml does not declare is
 * a command nobody can use.
 */
class LanguageParityTest {

    private static final Pattern PERMISSION = Pattern.compile("permission:\\s*'?([\\w.*-]+)'?");

    private static String resource(String name) {
        InputStream stream = LanguageParityTest.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new AssertionError(name + " is not on the test classpath");
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static YamlConfiguration yaml(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
            new java.io.ByteArrayInputStream(resource(name).getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8));
    }

    @Test
    void everyLanguageCarriesTheSameKeys() {
        assertEquals(yaml("languages/en.yml").getKeys(true), yaml("languages/vi.yml").getKeys(true));
    }

    @Test
    void noKeyIsBlankInAShippedLanguage() {
        for (String name : List.of("languages/en.yml", "languages/vi.yml")) {
            for (String key : yaml(name).getKeys(true)) {
                String path = name + " :: " + key;
                String text = yaml(name).getString(key);
                if (text == null) {
                    continue;
                }
                assertTrue(!text.isBlank() || key.equals("gui.closed"), path + " is blank");
            }
        }
    }

    @Test
    void theDefaultLanguageIsOneThePluginShips() {
        String language = yaml("config.yml").getString("lang");
        assertNotNull(language);

        assertTrue(!resource("languages/" + language).isEmpty());
    }

    @Test
    void everyPermissionTheEconomyModuleUsesIsDeclared() {
        ConfigurationSection declared = yaml("plugin.yml").getConfigurationSection("permissions");
        assertNotNull(declared);

        Set<String> used = new LinkedHashSet<>();
        Matcher matcher = PERMISSION.matcher(resource("modules/economy.yml"));
        while (matcher.find()) {
            used.add(matcher.group(1));
        }

        assertTrue(!used.isEmpty(), "the module file declares no permissions at all");
        Set<String> missing = new LinkedHashSet<>();
        for (String permission : used) {
            if (!declared.contains(permission)) {
                missing.add(permission);
            }
        }
        assertEquals(Set.of(), missing, "a permission is used but never declared");
    }

    @Test
    void theCommandsTheModuleShipsAreDeclaredForTheServer() {
        ConfigurationSection commands = yaml("plugin.yml").getConfigurationSection("commands");
        assertNotNull(commands);

        assertTrue(commands.contains("balance"));
        assertTrue(commands.contains("pay"));
        assertTrue(commands.contains("sealcore"));
    }
}
