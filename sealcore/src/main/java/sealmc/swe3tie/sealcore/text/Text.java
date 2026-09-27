package sealmc.swe3tie.sealcore.text;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage helpers. Adventure is provided by the server, so nothing here is
 * shaded into the plugin jar.
 *
 * <p>Every component SealCore shows goes through {@link #mini()}, which carries
 * the standard tag set plus <code>&lt;accent&gt;</code>. Values are not tags:
 * they are written <code>{name}</code> and filled in by {@link #render}.
 */
public final class Text {

    /** The brand colour SealCore's own strings lean on. */
    public static final String DEFAULT_ACCENT = "#9CC0D9";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Names for the tags a rewritten placeholder is parked under while MiniMessage parses. */
    private static final String TAG = "sealcore-placeholder-";

    private static volatile TextColor accent = initialAccent();
    private static volatile MiniMessage mini = build(accent);

    private Text() {
    }

    /**
     * The one MiniMessage instance every render goes through.
     *
     * <p>Built with the global tags, so {@code <accent>} works in language
     * files, in config titles and anywhere else a component is parsed. It is
     * rebuilt only when the accent changes, because parse runs on GUI and HUD
     * tick paths.
     */
    public static MiniMessage mini() {
        return mini;
    }

    /** The accent colour currently in effect, for code that needs it directly. */
    public static TextColor accent() {
        return accent;
    }

    /**
     * Applies {@code general.accent-color} from {@code config.yml}.
     *
     * @return false, keeping the previous colour, for text that is not a hex
     *     colour, so one bad value in the file cannot blank out every string.
     */
    public static boolean setAccent(String hex) {
        TextColor colour = colorOf(hex);
        if (colour == null) {
            return false;
        }
        accent = colour;
        mini = build(colour);
        return true;
    }

    /**
     * The standard tag set plus {@code <accent>}. {@code tags} replaces the
     * default set rather than adding to it, so the standard tags are named
     * explicitly.
     */
    private static MiniMessage build(TextColor accent) {
        return MiniMessage.builder()
            .tags(TagResolver.resolver(
                TagResolver.standard(),
                TagResolver.resolver("accent", Tag.styling(accent))))
            .build();
    }

    private static TextColor initialAccent() {
        TextColor colour = colorOf(DEFAULT_ACCENT);
        return colour != null ? colour : NamedTextColor.WHITE;
    }

    private static TextColor colorOf(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            return TextColor.fromHexString(hex.trim());
        } catch (IllegalArgumentException notAColour) {
            return null;
        }
    }

    public static Component parse(String input) {
        return mini.deserialize(input);
    }

    public static Component parse(String input, TagResolver resolver) {
        return mini.deserialize(input, resolver);
    }

    /**
     * Renders {@code template} with MiniMessage after substituting the arguments,
     * which are alternating key and value.
     *
     * <p>Placeholders are written <code>{key}</code>. MiniMessage gives angle
     * brackets to colour and decoration, so a value called "white" or "red" used
     * to be swallowed as a tag and leave a hole in the message; braces keep values
     * and formatting apart. Each one is rewritten to a tag name no real tag can
     * collide with before MiniMessage sees the string, and the value goes in
     * unparsed, so a player name can never smuggle markup of its own in.
     *
     * <p>A file that still writes <code>&lt;key&gt;</code> keeps working, so a
     * language file translated before this change does not blank out on upgrade.
     */
    public static Component render(String template, String... keyAndValue) {
        if (keyAndValue.length == 0) {
            return parse(template);
        }
        if (keyAndValue.length % 2 != 0) {
            throw new IllegalArgumentException("render() needs an even number of arguments, got " + keyAndValue.length);
        }
        TagResolver.Builder builder = TagResolver.builder();
        String rewritten = template;
        for (int i = 0; i < keyAndValue.length; i += 2) {
            String key = keyAndValue[i];
            String token = "{" + key + "}";
            String legacy = "<" + key + ">";
            if (rewritten.contains(token)) {
                String tag = TAG + (i / 2);
                builder.resolver(Placeholder.unparsed(tag, keyAndValue[i + 1]));
                rewritten = rewritten.replace(token, "<" + tag + ">");
            } else if (rewritten.contains(legacy)) {
                builder.resolver(Placeholder.unparsed(key, keyAndValue[i + 1]));
            }
        }
        return parse(rewritten, builder.build());
    }

    public static Component empty() {
        return Component.empty();
    }

    /** Formats a duration as {@code 1d 2h 3m}, dropping leading zero units. */
    public static String duration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        if (totalSeconds <= 0L) {
            return "0m";
        }
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        List<String> parts = new ArrayList<>(4);
        if (days > 0L) {
            parts.add(days + "d");
        }
        if (hours > 0L) {
            parts.add(hours + "h");
        }
        if (minutes > 0L) {
            parts.add(minutes + "m");
        }
        if (seconds > 0L && days == 0L && hours == 0L) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }

    /**
     * Strips every MiniMessage tag and colour, for logging and console output.
     *
     * <p>This is a plain text render, not another MiniMessage render:
     * serialising back to MiniMessage would leave the markup in the log, and
     * console commands have no renderer to interpret it.
     */
    public static String plain(String input) {
        return plain(parse(input));
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }
}
