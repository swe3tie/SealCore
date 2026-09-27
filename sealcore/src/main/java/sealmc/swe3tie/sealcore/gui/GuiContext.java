package sealmc.swe3tie.sealcore.gui;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.economy.CurrencyKey;
import sealmc.swe3tie.sealcore.placeholder.PlaceholderEngine;
import sealmc.swe3tie.sealcore.text.Text;

/**
 * Everything a screen may need while it renders or handles a click.
 *
 * <p>Screens never reach for the plugin singleton directly, which keeps them
 * testable with plain fakes.
 */
public final class GuiContext {

    private final Player player;
    private final UUID playerId;
    private final GuiSession session;
    private final PlaceholderEngine placeholders;
    private final java.util.function.Consumer<Runnable> scheduler;

    public GuiContext(
        Player player,
        UUID playerId,
        GuiSession session,
        PlaceholderEngine placeholders,
        java.util.function.Consumer<Runnable> scheduler
    ) {
        this.player = player;
        this.playerId = playerId;
        this.session = session;
        this.placeholders = placeholders;
        this.scheduler = scheduler;
    }

    public Player player() {
        return player;
    }

    public UUID playerId() {
        return playerId;
    }

    public GuiSession session() {
        return session;
    }

    public PlaceholderEngine placeholders() {
        return placeholders;
    }

    public String placeholder(String key) {
        return placeholders == null ? key : placeholders.resolve(player, key);
    }

    public Component component(String text, String... keyAndValue) {
        return Text.render(text, keyAndValue);
    }

    public double balance(CurrencyKey currency) {
        return placeholders == null ? 0.0 : placeholders.cachedBalance(playerId, currency);
    }

    /** Runs the task on the thread owning the player. */
    public void onPlayerThread(Runnable task) {
        scheduler.accept(task);
    }
}
