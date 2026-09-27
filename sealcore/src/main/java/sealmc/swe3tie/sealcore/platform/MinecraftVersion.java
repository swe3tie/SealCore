package sealmc.swe3tie.sealcore.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;

/**
 * A Minecraft version such as {@code 1.21.11}, {@code 26.1.2} or {@code 26.2}.
 *
 * <p>Version parts are compared numerically and element-wise, which keeps the
 * ordering correct across the 1.21 to 26 scheme change: {@code 1.21.11} sorts
 * before {@code 26.1.2} because {@code 1 < 26}, and {@code 26.1.2} sorts before
 * {@code 26.2} because {@code 1 < 2}.
 */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {

    public static final MinecraftVersion UNKNOWN = new MinecraftVersion("unknown", List.of());
    public static final MinecraftVersion FIRST_SUPPORTED = new MinecraftVersion("1.21.11", List.of(1, 21, 11));

    private final String raw;
    private final List<Integer> parts;

    public MinecraftVersion(String raw, List<Integer> parts) {
        this.raw = raw;
        this.parts = List.copyOf(parts);
    }

    public String raw() {
        return raw;
    }

    public List<Integer> parts() {
        return parts;
    }

    public int major() {
        return parts.isEmpty() ? 0 : parts.get(0);
    }

    /** True for the calendar-versioned releases (26.x and newer). */
    public boolean isModern() {
        return major() >= 26;
    }

    public boolean isKnown() {
        return !parts.isEmpty();
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int size = Math.max(parts.size(), other.parts.size());
        for (int index = 0; index < size; index++) {
            int mine = index < parts.size() ? parts.get(index) : 0;
            int theirs = index < other.parts.size() ? other.parts.get(index) : 0;
            int result = Integer.compare(mine, theirs);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return raw;
    }

    public static MinecraftVersion parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return UNKNOWN;
        }
        List<Integer> parts = new ArrayList<>();
        for (String segment : value.split("[.\\-]")) {
            int end = 0;
            while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
                end++;
            }
            if (end > 0) {
                try {
                    parts.add(Integer.valueOf(segment.substring(0, end)));
                } catch (NumberFormatException tooLarge) {
                    // A part that is not a number simply does not count.
                }
            }
        }
        return parts.isEmpty() ? new MinecraftVersion(value, Collections.emptyList()) : new MinecraftVersion(value, parts);
    }

    public static MinecraftVersion detect() {
        try {
            return parse(Bukkit.getMinecraftVersion());
        } catch (Throwable noServerYet) {
            return UNKNOWN;
        }
    }
}
