package sealmc.swe3tie.sealcore.modules.economy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.lang.LangService;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;
import sealmc.swe3tie.sealcore.modules.economy.SellHistoryEntry;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.FilterOption;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.PagedGuiToolbar;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.SortOption;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Manages rendering and interactions for the {@code /sell history} menu. */
public final class SellHistoryGui {

    private SellHistoryGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Player player;
        private final PagedGuiToolbar<SellHistoryEntry> toolbar;
        private Inventory inventory;

        public Holder(@NotNull Player player, @NotNull PagedGuiToolbar<SellHistoryEntry> toolbar) {
            this.player = player;
            this.toolbar = toolbar;
        }

        public @NotNull Player player() {
            return this.player;
        }

        public @NotNull PagedGuiToolbar<SellHistoryEntry> toolbar() {
            return this.toolbar;
        }

        public void setInventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (this.inventory == null) throw new IllegalStateException("inventory not initialized");
            return this.inventory;
        }
    }

    public static void open(@NotNull Player player, @NotNull EconomyModule module) {
        SellHistoryGuiConfig config = module.sellHistoryGuiConfig();
        List<SortOption<SellHistoryEntry>> sortOptions = buildSortOptions();
        List<FilterOption<SellHistoryEntry>> filterOptions = buildFilterOptions();

        PagedGuiToolbar<SellHistoryEntry> toolbar = new PagedGuiToolbar<>(
            config.toolbar(),
            List.of(),
            sortOptions,
            filterOptions,
            p -> refresh(p, module, config),
            config.paginationEnabled()
        );

        Holder holder = new Holder(player, toolbar);
        Inventory inventory = Bukkit.createInventory(holder, config.size(),
            buildTitle(player, module.core().lang(), config.title(), 1, 1));
        holder.setInventory(inventory);

        toolbar.setOnPageOrOptionChange(() -> module.core().scheduler().entity(player, () -> {
            if (player.getOpenInventory().getTopInventory() != inventory) return;
            render(player, inventory, holder, module.core().lang(), config);
            updateTitle(player, module.core().lang(), config.title(), toolbar.page(), toolbar.maxPage());
        }));

        player.openInventory(inventory);

        module.sellHistoryStore().load(player.getUniqueId()).whenComplete((entries, error) -> module.core().scheduler().entity(player, () -> {
            if (player.getOpenInventory().getTopInventory() != inventory) return;
            if (error == null && entries != null) {
                toolbar.setItems(entries);
            }
            render(player, inventory, holder, module.core().lang(), config);
            updateTitle(player, module.core().lang(), config.title(), toolbar.page(), toolbar.maxPage());
        }));
    }

    private static void refresh(@NotNull Player player, @NotNull EconomyModule module, @NotNull SellHistoryGuiConfig config) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder() instanceof Holder holder)) return;

        module.sellHistoryStore().invalidate(player.getUniqueId());
        module.sellHistoryStore().load(player.getUniqueId()).whenComplete((entries, error) -> module.core().scheduler().entity(player, () -> {
            if (player.getOpenInventory().getTopInventory() != inventory) return;
            if (error == null && entries != null) {
                holder.toolbar().setItems(entries);
            }
            render(player, inventory, holder, module.core().lang(), config);
            updateTitle(player, module.core().lang(), config.title(), holder.toolbar().page(), holder.toolbar().maxPage());
        }));
    }

    public static void render(@NotNull Player player, @NotNull Inventory inventory,
                              @NotNull Holder holder, @NotNull LangService lang,
                              @NotNull SellHistoryGuiConfig config) {
        for (int i = 0; i < PagedGuiToolbar.ITEMS_PER_PAGE; i++) {
            inventory.setItem(i, null);
        }

        List<SellHistoryEntry> pageItems = holder.toolbar().pageItems();
        for (int i = 0; i < pageItems.size(); i++) {
            SellHistoryEntry entry = pageItems.get(i);
            inventory.setItem(i, createDisplayItem(entry, player, lang, config.itemLore()));
        }

        holder.toolbar().renderToolbar(inventory, player, lang);
    }

    @SuppressWarnings("deprecation")
    public static void updateTitle(@NotNull Player player, @NotNull LangService lang,
                                   @NotNull String titleTemplate, int page, int maxPage) {
        String title = LegacyComponentSerializer.legacySection().serialize(
            buildTitle(player, lang, titleTemplate, page, maxPage));
        player.getOpenInventory().setTitle(title);
    }

    public static Component buildTitle(@NotNull Player player, @NotNull LangService lang,
                                       @NotNull String titleTemplate, int page, int maxPage) {
        return lang.parse(titleTemplate, player,
            Placeholder.unparsed("page", Integer.toString(page)),
            Placeholder.unparsed("current_page", Integer.toString(page)),
            Placeholder.unparsed("max_page", Integer.toString(maxPage)));
    }

    private static ItemStack createDisplayItem(@NotNull SellHistoryEntry entry,
                                               @NotNull Player player,
                                               @NotNull LangService lang,
                                               @NotNull List<String> loreTemplate) {
        ItemStack stack = new ItemStack(entry.material(), Math.clamp(entry.amount(), 1, 64));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta instanceof PotionMeta potion && entry.potionType() != null) {
                try {
                    PotionType type = PotionType.valueOf(entry.potionType().toUpperCase(Locale.ROOT));
                    potion.setBasePotionType(type);
                } catch (IllegalArgumentException ignored) {
                    try {
                        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(entry.potionType().toLowerCase(Locale.ROOT));
                        @SuppressWarnings("deprecation")
                        org.bukkit.potion.PotionEffectType pet = org.bukkit.Registry.POTION_EFFECT_TYPE.get(key);
                        if (pet != null) {
                            potion.addCustomEffect(new org.bukkit.potion.PotionEffect(pet, 3600, 0), true);
                        }
                    } catch (Throwable ignored2) {
                    }
                } catch (Throwable ignored) {
                }
            }

            meta.displayName(lang.parse("<white>" + entry.displayName(), player)
                .decoration(TextDecoration.ITALIC, false));

            String formattedDate = entry.formattedDate();
            String amountFormatted = String.valueOf(entry.amount());
            String totalCompact = MoneyFormat.formatCompact(entry.totalPrice());
            String totalStandard = MoneyFormat.formatStandard(entry.totalPrice());

            List<Component> lore = new ArrayList<>(loreTemplate.size());
            for (String line : loreTemplate) {
                String rendered = line
                    .replace("{SELL_DATE}", formattedDate)
                    .replace("{AMOUNT}", amountFormatted)
                    .replace("{TOTAL_PRICE_FORMATTED}", totalCompact)
                    .replace("{TOTAL_PRICE}", totalStandard)
                    .replace("{PRICE_FORMATTED}", totalCompact)
                    .replace("{CURRENCY_PREFIX}", "<green>$")
                    .replace("{CURRENCY_SUFFIX}", "");
                lore.add(lang.parse(rendered, player).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static void handleClick(@NotNull InventoryClickEvent event, @NotNull Player player) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            holder.toolbar().handleClick(rawSlot, player);
        }
    }

    @NotNull
    public static List<SortOption<SellHistoryEntry>> buildSortOptions() {
        return List.of(
            new SortOption<>("date_desc", "Gần nhất", (a, b) -> Long.compare(b.soldAt(), a.soldAt())),
            new SortOption<>("date_asc", "Lâu nhất", (a, b) -> Long.compare(a.soldAt(), b.soldAt())),
            new SortOption<>("price_desc", "Giá cao nhất", (a, b) -> Double.compare(b.totalPrice(), a.totalPrice())),
            new SortOption<>("price_asc", "Giá thấp nhất", (a, b) -> Double.compare(a.totalPrice(), b.totalPrice()))
        );
    }

    @NotNull
    public static List<FilterOption<SellHistoryEntry>> buildFilterOptions() {
        return List.of(
            new FilterOption<>("all", "Tất cả", item -> true),
            new FilterOption<>("crops", "Cây trồng", item -> item.category() == SellCategory.CROPS),
            new FilterOption<>("ores", "Khoáng sản", item -> item.category() == SellCategory.ORES),
            new FilterOption<>("mobs", "Sinh vật", item -> item.category() == SellCategory.MOBS),
            new FilterOption<>("natural", "Tự nhiên", item -> item.category() == SellCategory.NATURAL),
            new FilterOption<>("tools", "Giáp và công cụ", item -> item.category() == SellCategory.TOOLS),
            new FilterOption<>("fish", "Cá", item -> item.category() == SellCategory.FISH),
            new FilterOption<>("books", "Sách và sách phù phép", item -> item.category() == SellCategory.BOOKS),
            new FilterOption<>("potions", "Thuốc", item -> item.category() == SellCategory.POTIONS),
            new FilterOption<>("blocks", "Khối", item -> item.category() == SellCategory.BLOCKS)
        );
    }
}
