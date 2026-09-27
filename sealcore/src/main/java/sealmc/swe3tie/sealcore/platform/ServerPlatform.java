package sealmc.swe3tie.sealcore.platform;

import java.util.Locale;
import org.bukkit.Bukkit;

/**
 * The server implementation SealCore is running on.
 *
 * <p>SealCore only ever talks to the regionized schedulers, which Paper
 * implements as well, so the same code paths serve both platforms. The
 * distinction is kept for logging, for feature gating and for the one place
 * where behaviour really does differ (threaded access to shared state).
 */
public enum ServerPlatform {

    PAPER("Paper"),
    FOLIA("Folia"),
    UNKNOWN("Unknown");

    private final String displayName;

    ServerPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isFolia() {
        return this == FOLIA;
    }

    /** True when the regionized scheduler API can be used. */
    public boolean supportsRegions() {
        return this == PAPER || this == FOLIA;
    }

    public static ServerPlatform detect() {
        String brand;
        try {
            brand = Bukkit.getName();
        } catch (Throwable noServerYet) {
            return UNKNOWN;
        }
        if (brand == null) {
            return UNKNOWN;
        }
        String lower = brand.toLowerCase(Locale.ROOT);
        if (lower.contains("folia")) {
            return FOLIA;
        }
        if (lower.contains("paper") || lower.contains("purpur")) {
            return PAPER;
        }
        return UNKNOWN;
    }
}
