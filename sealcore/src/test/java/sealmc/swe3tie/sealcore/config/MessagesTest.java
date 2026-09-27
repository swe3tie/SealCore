package sealmc.swe3tie.sealcore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static final Logger LOGGER = Logger.getLogger("SealCoreTest");

    static {
        LOGGER.setLevel(Level.OFF);
    }

    private static Plugin plugin(Path root) {
        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getDataFolder" -> root.toFile();
                case "getLogger" -> LOGGER;
                case "toString" -> "FakePlugin";
                case "hashCode" -> 1;
                case "equals" -> false;
                default -> null;
            });
    }

    private static Path tempRoot() {
        try {
            return Files.createTempDirectory("sealcore-lang");
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static void write(Path root, String name, String body) {
        try {
            Path directory = root.resolve("languages");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name), body);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    @Test
    void languageNamesAreNormalisedToAFileName() {
        assertEquals("en.yml", Messages.normalise(null));
        assertEquals("en.yml", Messages.normalise("  "));
        assertEquals("en.yml", Messages.normalise("en"));
        assertEquals("EN.yml", Messages.normalise("EN.YML"));
        // Case is preserved, because jar resources are matched case sensitively.
        assertEquals("en-US.yml", Messages.normalise("en-US"));
    }

    @Test
    void aLanguageTypedInTheWrongCaseStillResolves() {
        Path root = tempRoot();
        write(root, "en.yml", "core:\n  hi: 'hello'\n");

        assertEquals("hello", Messages.load(plugin(root), "EN.YML").text("core.hi"));
        assertEquals("hello", Messages.load(plugin(root), "en").text("core.hi"));
    }

    @Test
    void aKeyMissingFromTheLanguageFallsBackToEnglish() {
        Path root = tempRoot();
        write(root, "en.yml", "core:\n  hi: 'hello'\n  bye: 'bye'\n");
        write(root, "vi.yml", "core:\n  hi: 'chao'\n");

        var messages = Messages.load(plugin(root), "vi.yml");

        assertEquals("vi.yml", messages.language());
        assertEquals("en.yml", messages.fallbackLanguage());
        assertEquals("chao", messages.text("core.hi"));
        assertEquals("bye", messages.text("core.bye"));
    }

    @Test
    void aKeyMissingEverywhereRendersTheKeyItself() {
        Path root = tempRoot();
        write(root, "en.yml", "core:\n  hi: 'hello'\n");
        write(root, "vi.yml", "core: {}\n");

        var messages = Messages.load(plugin(root), "vi.yml");

        assertEquals("core.gone", messages.text("core.gone"));
        assertFalse(messages.has("core.gone"));
        assertTrue(messages.has("core.hi"));
    }

    @Test
    void englishAloneNeedsNoFallback() {
        Path root = tempRoot();
        write(root, "en.yml", "core:\n  hi: 'hello'\n");

        var messages = Messages.load(plugin(root), "en.yml");

        assertEquals("en.yml", messages.language());
        assertNull(messages.fallbackLanguage());
        assertEquals("hello", messages.text("core.hi"));
    }

    @Test
    void placeholdersAreSubstituted() {
        Path root = tempRoot();
        write(root, "en.yml", "core:\n  paid: 'got <white>{amount}</white>'\n");

        var messages = Messages.load(plugin(root), "en.yml");

        assertEquals("got 5", messages.text("core.paid", "amount", "5"));
    }
}
