package sealmc.swe3tie.sealcore.modules.economy;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Who a command was pointed at, and whether they are on the server right now. */
public record PaymentTarget(String name, UUID uuid, Player player) {

    public boolean isOnline() {
        return player != null;
    }
}
