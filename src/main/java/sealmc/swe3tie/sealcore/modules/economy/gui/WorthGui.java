package sealmc.swe3tie.sealcore.modules.economy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.lang.LangService;
import sealmc.swe3tie.sealcore.modules.economy.EconomyModule;
import sealmc.swe3tie.sealcore.modules.economy.ProgressStore;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;
import sealmc.swe3tie.sealcore.modules.economy.SellProgress;
import sealmc.swe3tie.sealcore.modules.economy.WorthItem;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.FilterOption;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.PagedGuiToolbar;
import sealmc.swe3tie.sealcore.modules.economy.gui.toolbar.SortOption;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Manages rendering and interactions for the {@code /worth} lookup menu. */
public final class WorthGui {

    private WorthGui() {
    }

    public static void open(@NotNull Player player, @NotNull EconomyModule module, @NotNull WorthGuiConfig config) {
        List<SortOption<WorthItem>> sortOptions = buildSortOptions();
        List<FilterOption<WorthItem>> filterOptions = buildFilterOptions();

        PagedGuiToolbar<WorthItem> toolbar = new PagedGuiToolbar<>(
            config.toolbar(),
            module.priceService().allWorthItems(),
            sortOptions,
            filterOptions,
            p -> refresh(p, module, config),
            config.paginationEnabled()
        );

        WorthInventoryHolder holder = new WorthInventoryHolder(player, toolbar);
        Inventory inventory = Bukkit.createInventory(holder, config.size(),
            buildTitle(player, module.core().lang(), config.title(), 1, toolbar.maxPage()));
        holder.setInventory(inventory);

        toolbar.setOnPageOrOptionChange(() -> module.core().scheduler().entity(player, () -> {
            if (player.getOpenInventory().getTopInventory() != inventory) return;
            render(player, inventory, holder, module.core().lang(), config);
            updateTitle(player, module.core().lang(), config.title(), toolbar.page(), toolbar.maxPage());
        }));

        ProgressStore store = module.progressStore();
        store.load(player.getUniqueId()).whenComplete((progress, error) -> module.core().scheduler().entity(player, () -> {
            if (error == null && progress != null) {
                EnumMap<SellCategory, Double> mults = new EnumMap<>(SellCategory.class);
                for (SellCategory cat : SellCategory.values()) {
                    mults.put(cat, SellProgress.multiplier(progress.getOrDefault(cat, 0D)));
                }
                holder.setMultipliers(mults);
            }
            if (player.getOpenInventory().getTopInventory() == inventory) {
                render(player, inventory, holder, module.core().lang(), config);
            }
        }));

        render(player, inventory, holder, module.core().lang(), config);
        player.openInventory(inventory);
    }

    private static void refresh(@NotNull Player player, @NotNull EconomyModule module, @NotNull WorthGuiConfig config) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder() instanceof WorthInventoryHolder holder)) return;

        ProgressStore store = module.progressStore();
        store.load(player.getUniqueId()).whenComplete((progress, error) -> module.core().scheduler().entity(player, () -> {
            if (player.getOpenInventory().getTopInventory() != inventory) return;
            if (error == null && progress != null) {
                EnumMap<SellCategory, Double> mults = new EnumMap<>(SellCategory.class);
                for (SellCategory cat : SellCategory.values()) {
                    mults.put(cat, SellProgress.multiplier(progress.getOrDefault(cat, 0D)));
                }
                holder.setMultipliers(mults);
            }
            render(player, inventory, holder, module.core().lang(), config);
        }));
    }

    public static void render(@NotNull Player player, @NotNull Inventory inventory,
                              @NotNull WorthInventoryHolder holder, @NotNull LangService lang,
                              @NotNull WorthGuiConfig config) {
        // Clear item display slots (0..44)
        for (int i = 0; i < PagedGuiToolbar.ITEMS_PER_PAGE; i++) {
            inventory.setItem(i, null);
        }

        List<WorthItem> pageItems = holder.toolbar().pageItems();
        Map<SellCategory, Double> multipliers = holder.multipliers();

        for (int i = 0; i < pageItems.size(); i++) {
            WorthItem item = pageItems.get(i);
            double mult = item.category() != null ? multipliers.getOrDefault(item.category(), 1.0D) : 1.0D;
            double worth = item.basePrice() * mult;

            inventory.setItem(i, createDisplayItem(item, worth, mult, player, lang, config.itemLore()));
        }

        // Render bottom row toolbar
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

    private static ItemStack createDisplayItem(@NotNull WorthItem item, double worth, double multiplier,
                                               @NotNull Player player, @NotNull LangService lang,
                                               @NotNull List<String> loreTemplate) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.parse("<white>" + item.displayName(), player)
                .decoration(TextDecoration.ITALIC, false));

            if (meta instanceof PotionMeta potion && item.potionType() != null) {
                try {
                    PotionType type = PotionType.valueOf(item.potionType().toUpperCase(Locale.ROOT));
                    potion.setBasePotionType(type);
                } catch (Throwable ignored) {
                }
            } else if (meta instanceof EnchantmentStorageMeta book && !item.enchantments().isEmpty()) {
                for (Map.Entry<String, Integer> entry : item.enchantments().entrySet()) {
                    try {
                        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(entry.getKey().toLowerCase(Locale.ROOT));
                        @SuppressWarnings("deprecation")
                        Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(key);
                        if (ench != null) {
                            book.addStoredEnchant(ench, entry.getValue(), true);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            String sectionName = item.category() != null ? SellProgress.categoryLabel(item.category()) : "Chung";
            String worthFormatted = MoneyFormat.formatCompact(worth);
            String multFormatted = String.format(Locale.ROOT, "%.2f", multiplier);

            List<Component> lore = new ArrayList<>(loreTemplate.size());
            for (String line : loreTemplate) {
                String rendered = line
                    .replace("{CURRENCY_PREFIX}", "<green>$")
                    .replace("{CURRENCY_SUFFIX}", "")
                    .replace("{PRICE_FORMATTED}", worthFormatted)
                    .replace("{PRICE_COMPACT}", worthFormatted)
                    .replace("{PRICE}", MoneyFormat.formatStandard(worth))
                    .replace("{WORTH_FORMATTED}", worthFormatted)
                    .replace("{WORTH}", MoneyFormat.formatStandard(worth))
                    .replace("{SECTION_MULTIPLIER}", multFormatted)
                    .replace("{MULTIPLIER}", multFormatted)
                    .replace("{SECTION}", sectionName);
                lore.add(lang.parse(rendered, player).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @NotNull
    public static List<SortOption<WorthItem>> buildSortOptions() {
        return List.of(
            new SortOption<>("price_desc", "Giá cao nhất", (a, b) -> Double.compare(b.basePrice(), a.basePrice())),
            new SortOption<>("price_asc", "Giá thấp nhất", (a, b) -> Double.compare(a.basePrice(), b.basePrice())),
            new SortOption<>("name_asc", "Tên A-Z", Comparator.comparing(WorthItem::displayName, String.CASE_INSENSITIVE_ORDER)),
            new SortOption<>("name_desc", "Tên Z-A", (a, b) -> b.displayName().compareToIgnoreCase(a.displayName()))
        );
    }

    @NotNull
    public static List<FilterOption<WorthItem>> buildFilterOptions() {
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
