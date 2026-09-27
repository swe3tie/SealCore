package su.nightexpress.excellenteconomy.tops.menu;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.tops.TopEntry;
import su.nightexpress.excellenteconomy.tops.TopLang;
import su.nightexpress.excellenteconomy.tops.TopManager;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.ConfigTypes;
import su.nightexpress.nightcore.ui.inventory.item.ItemPopulator;
import su.nightexpress.nightcore.ui.inventory.item.MenuItem;
import su.nightexpress.nightcore.ui.inventory.menu.AbstractObjectMenu;
import su.nightexpress.nightcore.ui.inventory.viewer.ViewerContext;
import su.nightexpress.nightcore.util.bukkit.NightItem;
import su.nightexpress.nightcore.util.placeholder.CommonPlaceholders;
import su.nightexpress.nightcore.util.placeholder.PlaceholderContext;

import static su.nightexpress.excellenteconomy.EconomyPlaceholders.*;

import java.util.List;
import java.util.stream.IntStream;

public class TopMenu extends AbstractObjectMenu<ExcellentCurrency> {

    private final TopManager topManager;

    private ItemPopulator<TopEntry> entryItemPopulator;

    public TopMenu(@NonNull EconomyPlugin plugin, @NonNull TopManager topManager) {
        super(plugin, MenuType.GENERIC_9X6, "Balance Top - " + CURRENCY_NAME, ExcellentCurrency.class);
        this.topManager = topManager;
    }

    @Override
    @NonNull
    protected String getRawTitle(@NonNull ViewerContext context) {
        ExcellentCurrency currency = this.getObject(context);

        return PlaceholderContext.builder().with(currency.placeholders()).build().apply(super.getRawTitle(context));
    }

    @Override
    public void registerActions() {

    }

    @Override
    public void registerConditions() {

    }

    @Override
    public void defineDefaultLayout() {
        this.addBackgroundItem(Material.BLACK_STAINED_GLASS_PANE, IntStream.range(0, 9).toArray());
        this.addBackgroundItem(Material.BLACK_STAINED_GLASS_PANE, IntStream.range(45, 54).toArray());
        this.addBackgroundItem(Material.GRAY_STAINED_GLASS_PANE, IntStream.range(0, 45).toArray());

        this.addNextPageButton(53);
        this.addPreviousPageButton(45);
    }

    @Override
    protected void onLoad(@NonNull FileConfig config) {
        NightItem entryIcon = config.get(ConfigTypes.NIGHT_ITEM, "Entry.Icon",
            NightItem.fromType(Material.PLAYER_HEAD)
        );

        int[] defSlots = {13, 21, 22, 23, 29, 30, 31, 32, 33, 37, 38, 39, 40, 41, 42, 43};
        int[] entrySlots = config.get(ConfigTypes.INT_ARRAY, "Entry.Slots", defSlots);

        this.entryItemPopulator = ItemPopulator.builder(TopEntry.class)
            .actionProvider(entry -> context -> {
            })
            .itemProvider((context, entry) -> {
                ExcellentCurrency currency = this.getObject(context);

                return entryIcon.copy()
                    .hideAllComponents()
                    .localized(TopLang.UI_LEADERBOARD_ENTRY)
                    .setPlayerProfile(entry.getProfile().query())
                    .replace(builder -> builder
                        .with(GENERIC_POS, () -> String.valueOf(entry.getPosition()))
                        .with(CommonPlaceholders.PLAYER_NAME, entry::getName)
                        .with(GENERIC_BALANCE, () -> currency.format(entry.getBalance()))
                    );
            })
            .slots(entrySlots)
            .build();
    }

    @Override
    protected void onClick(@NonNull ViewerContext context, @NonNull InventoryClickEvent event) {

    }

    @Override
    protected void onDrag(@NonNull ViewerContext context, @NonNull InventoryDragEvent event) {

    }

    @Override
    protected void onClose(@NonNull ViewerContext context, @NonNull InventoryCloseEvent event) {

    }

    @Override
    public void onPrepare(@NonNull ViewerContext context, @NonNull InventoryView view, @NonNull Inventory inventory,
                          @NonNull List<MenuItem> items) {
        ExcellentCurrency currency = this.getObject(context);
        List<TopEntry> entries = this.topManager.getTopEntries(currency);

        this.entryItemPopulator.populateTo(context, entries, items);
    }

    @Override
    public void onReady(@NonNull ViewerContext context, @NonNull InventoryView view, @NonNull Inventory inventory) {

    }

    @Override
    public void onRender(@NonNull ViewerContext context, @NonNull InventoryView view, @NonNull Inventory inventory) {

    }
}
