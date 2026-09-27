package sealmc.swe3tie.sealcore.config;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import sealmc.swe3tie.sealcore.text.Text;

/**
 * Every player facing string, in one file per language.
 *
 * <p>{@code config.yml} names the active file with {@code lang}, for example
 * {@code lang: en.yml}. Keys are namespaced by module, so
 * {@code jobs.miner.payout-received} is the namespace and the key together and
 * two modules can never collide.
 *
 * <p>A key missing from the selected language falls back to {@code en.yml}
 * before it falls back to rendering the key itself, so a partially translated
 * language still shows English rather than blanks in game. Both the miss and a
 * missing key are logged once, not once per player per frame.
 */
public final class Messages {

    public static final String DIRECTORY = "languages";

    /** The language every other file falls back to. */
    public static final String DEFAULT_LANGUAGE = "en.yml";

    private static final String SUFFIX = ".yml";

    private final String language;
    private final String fallbackLanguage;
    private final YamlConfiguration primary;
    private final YamlConfiguration fallback;

    private final Set<String> missing = new HashSet<>();

    public Messages(String language, String fallbackLanguage, YamlConfiguration primary, YamlConfiguration fallback) {
        this.language = language;
        this.fallbackLanguage = fallbackLanguage;
        this.primary = primary;
        this.fallback = fallback;
    }

    public String language() {
        return language;
    }

    public String fallbackLanguage() {
        return fallbackLanguage;
    }

    public boolean has(String path) {
        return primary.isSet(path) || (fallback != null && fallback.isSet(path));
    }

    public Component component(String path, String... keyAndValue) {
        String template = primary.getString(path);
        if (template == null && fallback != null) {
            template = fallback.getString(path);
        }
        if (template == null) {
            if (missing.add(path)) {
                System.err.println("[SealCore] Missing message key '" + path + "' in " + language + ".");
            }
            return Component.text(path);
        }
        return Text.render(template, keyAndValue);
    }

    public String text(String path, String... keyAndValue) {
        return Text.plain(component(path, keyAndValue));
    }

    public void send(CommandSender sender, String path, String... keyAndValue) {
        sender.sendMessage(component(path, keyAndValue));
    }

    /** Keys present in the language file, useful for spotting stale translations. */
    public Set<String> keys() {
        return primary.getKeys(true);
    }

    /**
     * Turns {@code en}, {@code EN}, {@code en.yml} and {@code en-US.yml} into a
     * file name.
     *
     * <p>Case is preserved on purpose: language files are matched against jar
     * resources, which is case sensitive, so {@code en-US.yml} must not quietly
     * become {@code en-us.yml}.
     */
    public static String normalise(String language) {
        String trimmed = language == null ? "" : language.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_LANGUAGE;
        }
        String withSuffix = trimmed.toLowerCase(java.util.Locale.ROOT).endsWith(SUFFIX)
            ? trimmed.substring(0, trimmed.length() - SUFFIX.length()) + SUFFIX
            : trimmed + SUFFIX;
        return new File(withSuffix).getName();
    }

    public static Messages load(Plugin plugin, String language) {
        File directory = new File(plugin.getDataFolder(), DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // The lookup also copies the shipped file out on first run, so a
        // language that only exists in the jar still works immediately.
        String chosen = normalise(language);
        fileFor(plugin, chosen);
        YamlConfiguration primary = YamlConfiguration.loadConfiguration(new File(directory, chosen));

        String fallbackFile;
        if (chosen.equals(DEFAULT_LANGUAGE)) {
            fallbackFile = null;
        } else {
            fallbackFile = fileFor(plugin, DEFAULT_LANGUAGE);
        }
        YamlConfiguration fallback = fallbackFile == null
            ? null
            : YamlConfiguration.loadConfiguration(new File(directory, fallbackFile));
        if (fallbackFile == null && !chosen.equals(DEFAULT_LANGUAGE)) {
            plugin.getLogger().warning("No languages/" + DEFAULT_LANGUAGE + " to fall back on; missing keys will render raw.");
        }
        return new Messages(chosen, fallbackFile, primary, fallback);
    }

    /**
     * Finds a language file, first by the exact name, then case insensitively,
     * then in the jar. A language is a human typed value, so {@code EN},
     * {@code en} and {@code en-US} all have to land on the file the operator
     * meant rather than silently falling back to English.
     */
    private static String fileFor(Plugin plugin, String name) {
        File directory = new File(plugin.getDataFolder(), DIRECTORY);
        if (new File(directory, name).exists()) {
            return name;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().equalsIgnoreCase(name)) {
                    return file.getName();
                }
            }
        }
        String resource = DIRECTORY + "/" + name;
        if (plugin.getResource(resource) != null) {
            plugin.saveResource(resource, false);
            return name;
        }
        return null;
    }
}
