package sealmc.swe3tie.sealcore.modules.economy.packet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration for client-side item worth lore displayed via PacketEvents.
 */
public record WorthLoreConfig(
    boolean enabled,
    @NotNull List<String> lines,
    @NotNull Set<String> prefixes,
    @NotNull Set<String> staticLines
) {

    public static final List<String> DEFAULT_LINES = List.of("<gray>Giá trị: <green>${WORTH_FORMATTED}");
    public static final Set<String> DEFAULT_PREFIXES = Set.of(
        "Giá trị: ", "Giá trị:", "Giá bán: ", "Giá bán:", "Worth: ", "Worth:", "Price: ", "Price:"
    );

    public WorthLoreConfig {
        lines = List.copyOf(lines);
        prefixes = Collections.unmodifiableSet(prefixes);
        staticLines = Collections.unmodifiableSet(staticLines);
    }

    @NotNull
    public static WorthLoreConfig load(@Nullable ConfigurationSection section) {
        if (section == null) {
            return new WorthLoreConfig(true, DEFAULT_LINES, DEFAULT_PREFIXES, Set.of());
        }

        boolean enabled = section.getBoolean("enabled", true);
        List<String> lines;
        if (section.isList("lines")) {
            lines = section.getStringList("lines");
        } else if (section.isString("format")) {
            lines = List.of(section.getString("format"));
        } else {
            lines = DEFAULT_LINES;
        }
        if (lines.isEmpty()) {
            lines = DEFAULT_LINES;
        }

        Set<String> prefixes = new LinkedHashSet<>(DEFAULT_PREFIXES);
        Set<String> staticLines = new LinkedHashSet<>();

        for (String line : lines) {
            int idx = -1;
            String lower = line.toLowerCase(Locale.ROOT);
            for (String tag : List.of("{worth", "{price", "{gia", "{value")) {
                int found = lower.indexOf(tag);
                if (found != -1 && (idx == -1 || found < idx)) {
                    idx = found;
                }
            }

            if (idx != -1) {
                String prefixRaw = line.substring(0, idx);
                try {
                    Component comp = MiniMessage.miniMessage().deserialize(prefixRaw);
                    String plain = PlainTextComponentSerializer.plainText().serialize(comp);
                    if (!plain.isBlank()) {
                        prefixes.add(plain);
                        prefixes.add(plain.trim());
                        if (plain.endsWith(":")) {
                            prefixes.add(plain + " ");
                        }
                    }
                } catch (Throwable ignored) {
                }
            } else {
                try {
                    Component comp = MiniMessage.miniMessage().deserialize(line);
                    String plain = PlainTextComponentSerializer.plainText().serialize(comp);
                    if (!plain.isBlank()) {
                        staticLines.add(plain);
                        staticLines.add(plain.trim());
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return new WorthLoreConfig(enabled, lines, prefixes, staticLines);
    }

    /**
     * Checks if a lore component line represents a primary worth value line.
     */
    public boolean isWorthValueLine(@NotNull Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        for (String prefix : this.prefixes) {
            if (plain.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a component matches either a worth value line or an associated static line.
     */
    public boolean matchesWorthLore(@NotNull Component component) {
        if (this.isWorthValueLine(component)) {
            return true;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        for (String staticLine : this.staticLines) {
            if (plain.equals(staticLine) || plain.trim().equals(staticLine)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips worth lore lines from the bottom of a lore list upwards until a non-worth line is reached.
     */
    @NotNull
    public List<Component> stripWorthLines(@NotNull List<Component> original) {
        List<Component> result = new java.util.ArrayList<>(original);
        while (!result.isEmpty()) {
            Component last = result.getLast();
            if (this.matchesWorthLore(last)) {
                result.removeLast();
            } else {
                break;
            }
        }
        return result;
    }
}
