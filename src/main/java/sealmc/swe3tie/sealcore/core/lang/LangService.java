package sealmc.swe3tie.sealcore.core.lang;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.core.config.ConfigException;
import sealmc.swe3tie.sealcore.core.config.ConfigService;
import sealmc.swe3tie.sealcore.core.config.CoreSettings;
import sealmc.swe3tie.sealcore.core.config.YamlFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * Loads {@code languages/<lang>.yml} with {@code en.yml} as the fallback for every missing key,
 * and renders messages with MiniMessage.
 * <p>
 * Built-in tags: {@code <accent>} (colour from config.yml) and {@code <prefix>}. Callers add their
 * own placeholders as {@link TagResolver}s. Rendering and sending are thread-safe.
 */
public final class LangService {

    public static final String FALLBACK = "en.yml";
    /** Language files shipped in the jar; each is extracted and kept up to date. */
    public static final List<String> BUNDLED = List.of("en.yml", "vi.yml");

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), TagResolver.empty(), false);
    private volatile BiFunction<Player, String, String> placeholderParser = (player, text) -> text;

    private record Snapshot(Map<String, List<String>> selected, Map<String, List<String>> fallback,
                            TagResolver builtins, boolean parsePlaceholders) {
    }

    public LangService(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Hook used to resolve PlaceholderAPI placeholders for player viewers. */
    public void setPlaceholderParser(@NotNull BiFunction<Player, String, String> parser) {
        this.placeholderParser = parser;
    }

    public void load(@NotNull CoreSettings settings) throws ConfigException {
        Path folder = this.plugin.getDataFolder().toPath().resolve("languages");

        // Extract and update every bundled language so admins see all available translations.
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        for (String name : BUNDLED) {
            YamlFile file = new YamlFile(folder.resolve(name), ConfigService.resource(this.plugin, "languages/" + name));
            loaded.put(name, file.load());
        }

        YamlConfiguration fallback = loaded.get(FALLBACK);
        YamlConfiguration selected = loaded.get(settings.lang());
        if (selected == null) {
            Path custom = folder.resolve(settings.lang());
            if (Files.exists(custom)) {
                selected = new YamlFile(custom, null).load();
            } else {
                this.plugin.getLogger().warning("Language file languages/" + settings.lang() + " not found, using " + FALLBACK);
                selected = fallback;
            }
        }

        // Last resort: the en.yml inside the jar, in case the one on disk was gutted.
        Map<String, List<String>> fallbackMessages = flatten(fallback);
        flatten(loadJarFallback()).forEach(fallbackMessages::putIfAbsent);

        String accentHex = settings.accentColor().asHexString();
        TagResolver accent = TagResolver.resolver("accent", Tag.styling(style -> style.color(settings.accentColor())));
        Map<String, List<String>> selectedMessages = flatten(selected);
        String prefixRaw = first(selectedMessages.get("prefix"), first(fallbackMessages.get("prefix"), ""));
        Component prefix = MINI.deserialize(prefixRaw, accent);
        TagResolver builtins = TagResolver.resolver(accent, Placeholder.component("prefix", prefix),
            Placeholder.parsed("accent_hex", accentHex));

        this.snapshot = new Snapshot(selectedMessages, fallbackMessages, builtins, settings.parsePlaceholdersInMessages());
    }

    /** Raw MiniMessage lines for a key; never null, a missing key yields a visible marker. */
    @NotNull
    public List<String> raw(@NotNull String key) {
        Snapshot s = this.snapshot;
        List<String> lines = s.selected.get(key);
        if (lines == null) lines = s.fallback.get(key);
        if (lines == null) return List.of("<red>Missing message: " + key);
        return lines;
    }

    public boolean has(@NotNull String key) {
        Snapshot s = this.snapshot;
        return s.selected.containsKey(key) || s.fallback.containsKey(key);
    }

    /** Renders a message into components, one per line. Empty messages yield an empty list. */
    @NotNull
    public List<Component> render(@NotNull String key, @Nullable Audience viewer, @NotNull TagResolver... resolvers) {
        List<String> lines = this.raw(key);
        if (lines.size() == 1 && lines.getFirst().isEmpty()) return List.of();

        Snapshot s = this.snapshot;
        TagResolver all = TagResolver.resolver(TagResolver.resolver(resolvers), s.builtins);
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String text = line;
            if (s.parsePlaceholders && viewer instanceof Player player && text.indexOf('%') >= 0) {
                text = this.placeholderParser.apply(player, text);
            }
            out.add(MINI.deserialize(text, all));
        }
        return out;
    }

    /** Renders a single-line message (multi-line messages are joined with newlines). */
    @NotNull
    public Component component(@NotNull String key, @Nullable Audience viewer, @NotNull TagResolver... resolvers) {
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), this.render(key, viewer, resolvers));
    }

    /** Parses arbitrary MiniMessage (e.g. from a module config) with the built-in tags. */
    @NotNull
    public Component parse(@NotNull String text, @Nullable Audience viewer, @NotNull TagResolver... resolvers) {
        Snapshot s = this.snapshot;
        if (s.parsePlaceholders && viewer instanceof Player player && text.indexOf('%') >= 0) {
            text = this.placeholderParser.apply(player, text);
        }
        return MINI.deserialize(text, TagResolver.resolver(TagResolver.resolver(resolvers), s.builtins));
    }

    public void send(@NotNull CommandSender sender, @NotNull String key, @NotNull TagResolver... resolvers) {
        for (Component line : this.render(key, sender, resolvers)) sender.sendMessage(line);
    }

    // ------------------------------------------------------------------------------------------

    @Nullable
    private YamlConfiguration loadJarFallback() {
        try (InputStream in = this.plugin.getResource("languages/" + FALLBACK)) {
            if (in == null) return null;
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not read bundled " + FALLBACK, e);
            return null;
        }
    }

    /** Flattens a YAML tree into dotted keys mapped to message lines. */
    @NotNull
    static Map<String, List<String>> flatten(@Nullable ConfigurationSection section) {
        Map<String, List<String>> out = new HashMap<>();
        if (section == null) return out;
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key) || YamlFile.VERSION_KEY.equals(key)) continue;
            if (section.isList(key)) {
                out.put(key, List.copyOf(section.getStringList(key)));
            } else {
                Object value = section.get(key);
                if (value != null) out.put(key, List.of(String.valueOf(value)));
            }
        }
        return out;
    }

    private static String first(@Nullable List<String> lines, String def) {
        return lines == null || lines.isEmpty() ? def : String.join("\n", lines);
    }
}
