package su.nightexpress.excellenteconomy.api.event;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.user.CoinsUser;

public final class ChangeBalanceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final CoinsUser         user;
    private final ExcellentCurrency currency;
    private final double            oldAmount;
    private final double            newAmount;

    private boolean cancelled;

    public ChangeBalanceEvent(@NonNull CoinsUser user, @NonNull ExcellentCurrency currency, double oldAmount,
                              double newAmount) {
        super(!Bukkit.isPrimaryThread());
        this.user = user;
        this.currency = currency;
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @NonNull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NonNull
    public CoinsUser getUser() {
        return this.user;
    }

    @Nullable
    public Player getPlayer() {
        return this.user.player().orElse(null);
    }

    @NonNull
    public ExcellentCurrency getCurrency() {
        return this.currency;
    }

    public double getOldAmount() {
        return this.oldAmount;
    }

    public double getNewAmount() {
        return this.newAmount;
    }
}
