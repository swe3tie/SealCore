package sealmc.swe3tie.sealcore.placeholder;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.economy.EconomyService;
import sealmc.swe3tie.sealcore.util.Async;

/**
 * Resolves {@code %prefix_key%} tokens inside GUI titles, HUD lines and
 * messages.
 *
 * <p>Registrations are prefix based, so a feature module adds its own namespace
 * without touching the engine. Lookups are synchronous, so a resolver must not
 * call a blocking API: currency values come from a short lived cache that the
 * framework refreshes off the server thread.
 */
public final class PlaceholderEngine {

    private final EconomyService economy;
    private final Map<String, Resolver> resolvers = new ConcurrentHashMap<>();

    /** Cached balances so a HUD tick never blocks on a storage round trip. */
    private final Map<CacheKey, Double> balanceCache = new ConcurrentHashMap<>();

    private record CacheKey(UUID playerId, CurrencyKey currency) {
    }

    /** Returns null when the key is unknown, so the engine can try another prefix. */
    @FunctionalInterface
    public interface Resolver {
        String resolve(OfflinePlayer viewer, String key);
    }

    public PlaceholderEngine(EconomyService economy) {
        this.economy = economy;
    }

    public void register(String prefix, Resolver resolver) {
        resolvers.put(prefix.toLowerCase(Locale.ROOT), resolver);
    }

    public void unregister(String prefix) {
        resolvers.remove(prefix.toLowerCase(Locale.ROOT));
    }

    public Set<String> prefixes() {
        return Set.copyOf(resolvers.keySet());
    }

    public String resolve(OfflinePlayer viewer, String text) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf('%', index);
            if (start < 0) {
                result.append(text, index, text.length());
                break;
            }
            int end = text.indexOf('%', start + 1);
            if (end < 0) {
                result.append(text, index, text.length());
                break;
            }
            result.append(text, index, start);
            String token = text.substring(start + 1, end);
            result.append(resolveToken(viewer, token));
            index = end + 1;
        }
        return result.toString();
    }

    private String resolveToken(OfflinePlayer viewer, String token) {
        if (token.isEmpty()) {
            return "%";
        }
        int separator = token.indexOf('_');
        if (separator <= 0) {
            return "%" + token + "%";
        }
        String prefix = token.substring(0, separator).toLowerCase(Locale.ROOT);
        String key = token.substring(separator + 1);
        Resolver resolver = resolvers.get(prefix);
        if (resolver == null) {
            return "%" + token + "%";
        }
        try {
            String value = resolver.resolve(viewer, key);
            return value == null ? "%" + token + "%" : value;
        } catch (RuntimeException ignored) {
            return "%" + token + "%";
        }
    }

    // --- built in placeholders -------------------------------------------------

    public void installBuiltins() {
        register("player", (viewer, key) -> {
            if (viewer == null) {
                return null;
            }
            return switch (key.toLowerCase(Locale.ROOT)) {
                case "name" -> viewer.getName() == null ? "unknown" : viewer.getName();
                case "uuid" -> viewer.getUniqueId().toString();
                case "world" -> viewer.getPlayer() == null || viewer.getPlayer().getWorld() == null
                    ? "unknown"
                    : viewer.getPlayer().getWorld().getName();
                case "x" -> viewer.getLocation() == null ? "0" : String.valueOf(viewer.getLocation().getBlockX());
                case "y" -> viewer.getLocation() == null ? "0" : String.valueOf(viewer.getLocation().getBlockY());
                case "z" -> viewer.getLocation() == null ? "0" : String.valueOf(viewer.getLocation().getBlockZ());
                case "health" -> viewer.getPlayer() == null ? "0.0" : String.valueOf(viewer.getPlayer().getHealth());
                case "food" -> viewer.getPlayer() == null ? "0" : String.valueOf(viewer.getPlayer().getFoodLevel());
                case "ping" -> viewer.getPlayer() == null ? "0" : String.valueOf(viewer.getPlayer().getPing());
                case "online" -> String.valueOf(viewer.isOnline());
                default -> null;
            };
        });
        register("server", (viewer, key) -> switch (key.toLowerCase(Locale.ROOT)) {
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max" -> String.valueOf(Bukkit.getMaxPlayers());
            case "version" -> Bukkit.getMinecraftVersion();
            case "brand" -> Bukkit.getName();
            default -> null;
        });
        register("sealcore", (viewer, key) -> {
            if (viewer == null) {
                return "0";
            }
            return switch (key.toLowerCase(Locale.ROOT)) {
                case "money", "shards", "coins" -> format(viewer, CurrencyKey.of(key));
                default -> null;
            };
        });
    }

    public String format(OfflinePlayer viewer, CurrencyKey currency) {
        if (economy == null) {
            return "0";
        }
        double balance = cachedBalance(viewer.getUniqueId(), currency);
        try {
            return economy.format(currency, balance);
        } catch (RuntimeException fallback) {
            return Double.toString(balance);
        }
    }

    /**
     * Balance as of the last refresh. Returns the stale value rather than
     * blocking, which is the right trade-off for anything rendered per tick.
     */
    public double cachedBalance(UUID playerId, CurrencyKey currency) {
        Double balance = balanceCache.get(new CacheKey(playerId, currency));
        return balance == null ? 0.0 : balance;
    }

    /**
     * Refreshes one balance off the server thread. Never call from a render
     * path: it awaits the provider.
     */
    public void refreshBalance(UUID playerId, CurrencyKey currency) {
        if (economy == null) {
            return;
        }
        try {
            double balance = Async.await(economy.balance(playerId, currency));
            balanceCache.put(new CacheKey(playerId, currency), balance);
        } catch (RuntimeException failure) {
            // A provider that is down leaves the previous value in place.
        }
    }

    public void refreshAllBalances(java.util.List<CurrencyKey> currencies) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (CurrencyKey currency : currencies) {
                refreshBalance(player.getUniqueId(), currency);
            }
        }
    }

    public void invalidate(UUID playerId) {
        balanceCache.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void clearCache() {
        balanceCache.clear();
    }
}
