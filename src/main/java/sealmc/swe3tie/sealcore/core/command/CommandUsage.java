package sealmc.swe3tie.sealcore.core.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.lang.LangService;

/**
 * Standard command syntax error feedback across all SealCore commands:
 * plays {@code entity.villager.no} and displays {@code <red>Cách dùng: <usage>} on the action bar.
 */
public final class CommandUsage {

    private CommandUsage() {
    }

    /**
     * Sends syntax error feedback with the given usage text.
     *
     * @param lang  language service
     * @param sender command sender
     * @param usage  usage example string, e.g. {@code "/pay <player> <amount>"}
     */
    public static void send(@NotNull LangService lang, @NotNull CommandSender sender, @NotNull String usage) {
        var component = lang.component("core.command-usage", sender, Placeholder.unparsed("usage", usage));
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendActionBar(component);
        } else {
            sender.sendMessage(component);
        }
    }
}
