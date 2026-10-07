package sealmc.swe3tie.sealcore.core.hook.placeholder;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Holds every module's placeholder resolvers under the single {@code %sealcore_<module>_<params>%}
 * expansion, and parses third-party placeholders when PlaceholderAPI is installed.
 * Works without PlaceholderAPI: resolvers just are not exposed and {@link #parse} is a no-op.
 */
public final class PlaceholderRegistry {

    @FunctionalInterface
    public interface Resolver {
        /** @return the replacement, or {@code null} if {@code params} is not recognised */
        @Nullable
        String resolve(@Nullable OfflinePlayer player, @NotNull String params);
    }

    private final Map<String, Resolver> resolvers = new ConcurrentHashMap<>();
    private volatile BiFunction<Player, String, String> parser = (player, text) -> text;

    public void register(@NotNull String moduleId, @NotNull Resolver resolver) {
        this.resolvers.put(moduleId, resolver);
    }

    public void unregister(@NotNull String moduleId) {
        this.resolvers.remove(moduleId);
    }

    /** Resolves the part after {@code sealcore_}, e.g. {@code economy_balance_money}. */
    @Nullable
    public String resolve(@Nullable OfflinePlayer player, @NotNull String identifier) {
        int split = identifier.indexOf('_');
        String module = split < 0 ? identifier : identifier.substring(0, split);
        String params = split < 0 ? "" : identifier.substring(split + 1);
        Resolver resolver = this.resolvers.get(module.toLowerCase(java.util.Locale.ROOT));
        return resolver == null ? null : resolver.resolve(player, params);
    }

    /** Replaces PlaceholderAPI placeholders in {@code text} (identity when not hooked). */
    @NotNull
    public String parse(@NotNull Player player, @NotNull String text) {
        return this.parser.apply(player, text);
    }

    public void setParser(@NotNull BiFunction<Player, String, String> parser) {
        this.parser = parser;
    }

    public boolean isHooked() {
        return this.hooked;
    }

    private volatile boolean hooked;

    public void setHooked(boolean hooked) {
        this.hooked = hooked;
    }
}
