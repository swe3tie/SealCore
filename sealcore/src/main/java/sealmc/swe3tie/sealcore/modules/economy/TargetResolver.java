package sealmc.swe3tie.sealcore.modules.economy;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.module.ModuleContext;

/**
 * Turns a typed name into a player id.
 *
 * <p>An online exact match first, then a row from {@code sealcore_players}, which
 * is why a name the server has never seen this session still resolves as long as
 * the player has joined before. Nothing here asks Mojang: a web lookup blocks a
 * server thread, which is not a cost a command may pay.
 *
 * <p>Every method reads storage, so callers run it on the async scheduler.
 */
public final class TargetResolver {

    private final ModuleContext context;
    private final Function<String, Player> onlineLookup;
    private final Supplier<List<String>> onlineList;

    public TargetResolver(ModuleContext context) {
        this(context, Bukkit::getPlayerExact, () -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    /** Testable seam: the online lookups are injected. */
    public TargetResolver(
        ModuleContext context,
        Function<String, Player> onlineLookup,
        Supplier<List<String>> onlineList
    ) {
        this.context = context;
        this.onlineLookup = onlineLookup;
        this.onlineList = onlineList;
    }

    public PaymentTarget resolve(String name, boolean allowOffline) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Player online = onlineLookup.apply(trimmed);
        if (online != null) {
            return new PaymentTarget(online.getName(), online.getUniqueId(), online);
        }
        if (!allowOffline) {
            return null;
        }

        var repository = context.profiles();
        if (repository == null) {
            return null;
        }
        try {
            var record = repository.findByName(trimmed);
            return record == null ? null : new PaymentTarget(record.name(), record.uuid(), null);
        } catch (RuntimeException failure) {
            context.logger().warning("Name lookup for '" + trimmed + "' failed: " + failure.getMessage());
            return null;
        }
    }

    /** Online names for tab completion, which never needs storage. */
    public List<String> onlineNames(String partial) {
        String needle = partial == null ? "" : partial;
        return onlineList.get().stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(needle.toLowerCase(Locale.ROOT)))
            .sorted()
            .toList();
    }
}
