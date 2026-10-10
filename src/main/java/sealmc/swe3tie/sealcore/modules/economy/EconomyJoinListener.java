package sealmc.swe3tie.sealcore.modules.economy;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Delivers pending offline payment notifications when a player joins the server. */
public final class EconomyJoinListener implements Listener {

    private record Notification(int id, String senderName, double amount) {
    }

    private final EconomyModule module;

    public EconomyJoinListener(@NotNull EconomyModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String table = this.module.core().storage().table("economy_notifications");

        this.module.core().storage().query(connection -> {
            List<Notification> list = new ArrayList<>();
            String sql = "SELECT id, sender_name, amount FROM " + table + " WHERE receiver_uuid = ? ORDER BY id ASC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Notification(rs.getInt(1), rs.getString(2), rs.getDouble(3)));
                    }
                }
            }
            return list;
        }).thenAccept(notifications -> {
            if (notifications.isEmpty()) return;

            // Delete pending records from database
            this.module.core().storage().execute(connection -> {
                String delSql = "DELETE FROM " + table + " WHERE receiver_uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(delSql)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            });

            // Deliver notifications on player entity scheduler after 20 ticks delay
            this.module.core().scheduler().entityLater(player, () -> {
                if (!player.isOnline()) return;
                var lang = this.module.core().lang();
                String chatKey = lang.has("economy.pay.receiver.offline")
                    ? "economy.pay.receiver.offline"
                    : "economy.pay.receiver.chat";

                for (Notification n : notifications) {
                    String unformatted = MoneyFormat.formatStandard(n.amount());
                    String formatted = MoneyFormat.formatCompact(n.amount());
                    lang.send(player, chatKey,
                        Placeholder.unparsed("player", n.senderName()),
                        Placeholder.unparsed("amount", unformatted),
                        Placeholder.unparsed("amount_formatted", formatted),
                        Placeholder.unparsed("money", unformatted),
                        Placeholder.unparsed("money_formatted", formatted));

                    player.sendActionBar(lang.component("economy.pay.receiver.actionbar", player,
                        Placeholder.unparsed("player", n.senderName()),
                        Placeholder.unparsed("amount", unformatted),
                        Placeholder.unparsed("amount_formatted", formatted),
                        Placeholder.unparsed("money", unformatted),
                        Placeholder.unparsed("money_formatted", formatted)));
                }
            }, null, 20L);
        });
    }
}
