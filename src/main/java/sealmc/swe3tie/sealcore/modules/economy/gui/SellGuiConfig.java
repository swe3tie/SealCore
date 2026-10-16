package sealmc.swe3tie.sealcore.modules.economy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.core.config.ConfigException;
import sealmc.swe3tie.sealcore.core.lang.LangService;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;
import sealmc.swe3tie.sealcore.modules.economy.SellProgress;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, validated configuration for sell and progression inventories. */
public final class SellGuiConfig {

    private final String title;
    private final int rows;
    private final String multiplierTitle;
    private final int multiplierRows;
    private final Set<Integer> sellSlots;
    private final Map<SellCategory, CategoryButton> categoryButtons;
    private final ProgressConfig progress;
    private final SellProgress.BarConfig progressBar;

    private SellGuiConfig(String title, int rows, String multiplierTitle, int multiplierRows,
                          Set<Integer> sellSlots,
                          Map<SellCategory, CategoryButton> categoryButtons,
                          ProgressConfig progress, SellProgress.BarConfig progressBar) {
        this.title = title;
        this.rows = rows;
        this.multiplierTitle = multiplierTitle;
        this.multiplierRows = multiplierRows;
        this.sellSlots = sellSlots;
        this.categoryButtons = categoryButtons;
        this.progress = progress;
        this.progressBar = progressBar;
    }

    @NotNull
    public static SellGuiConfig load(@NotNull YamlConfiguration config) throws ConfigException {
        return load(config, config);
    }

    @NotNull
    public static SellGuiConfig load(@NotNull YamlConfiguration sellConfig,
                                     @NotNull YamlConfiguration multiplierConfig) throws ConfigException {
        return load(sellConfig, multiplierConfig, null);
    }

    @NotNull
    public static SellGuiConfig load(@NotNull YamlConfiguration sellConfig,
                                     @NotNull YamlConfiguration multiplierConfig,
                                     @Nullable PaginationConfig paginationConfig) throws ConfigException {
        String title = sellConfig.getString("title", "<dark_gray>Sell items: <green>{TOTAL_FORMATTED}");
        if (title == null || title.isBlank()) throw new ConfigException("GUI sell title cannot be empty");
        int rows = sellConfig.getInt("rows", 5);
        if (rows < 1 || rows > 6) throw new ConfigException("GUI sell rows must be between 1 and 6");

        int size = rows * 9;

        ConfigurationSection buttonSection = sellConfig.getConfigurationSection("category-buttons");
        if (buttonSection == null) buttonSection = sellConfig.getConfigurationSection("categories");
        if (buttonSection == null) buttonSection = sellConfig;

        EnumMap<SellCategory, CategoryButton> buttons = new EnumMap<>(SellCategory.class);
        Set<Integer> used = new LinkedHashSet<>();
        for (SellCategory category : SellCategory.values()) {
            ConfigurationSection section = buttonSection.getConfigurationSection(category.id());
            if (section == null && buttonSection != sellConfig) {
                section = sellConfig.getConfigurationSection(category.id());
            }
            if (section == null) throw new ConfigException("GUI category button is missing: " + category.id());
            int slot = readSingleSlot(section.getInt("slot", category.ordinal() + 37), size,
                category.id() + ".slot");
            if (!used.add(slot)) throw new ConfigException("Invalid or duplicate category button slot: " + (slot + 1));
            buttons.put(category, new CategoryButton(category, slot, readItem(section, category.id())));
        }

        Set<Integer> sellSlots;
        if (sellConfig.contains("sell-slots")) {
            sellSlots = readSlots(sellConfig.getIntegerList("sell-slots"), size, "sell-slots");
        } else {
            sellSlots = new LinkedHashSet<>();
            for (int slot = 0; slot < size; slot++) {
                if (!used.contains(slot)) {
                    sellSlots.add(slot);
                }
            }
        }

        SellProgress.BarConfig progressBar = readProgressBar(multiplierConfig, sellConfig);
        String multiplierTitle = multiplierConfig.getString("menu.title", "<dark_gray>ʜệ ѕố ʙáɴ");
        int multiplierRows = multiplierConfig.getInt("menu.rows", 3);
        if (multiplierRows < 1 || multiplierRows > 6) multiplierRows = 3;

        return new SellGuiConfig(title, rows, multiplierTitle, multiplierRows, Set.copyOf(sellSlots), Map.copyOf(buttons),
            readProgress(multiplierConfig, paginationConfig), progressBar);
    }

    @NotNull public String title() { return this.title; }
    public int rows() { return this.rows; }
    public int size() { return this.rows * 9; }
    @NotNull public String multiplierTitle() { return this.multiplierTitle; }
    public int multiplierRows() { return this.multiplierRows; }
    public int multiplierSize() { return this.multiplierRows * 9; }
    @NotNull public Set<Integer> sellSlots() { return this.sellSlots; }
    @NotNull public Map<SellCategory, CategoryButton> categoryButtons() { return this.categoryButtons; }
    @NotNull public CategoryButton categoryButton(@NotNull SellCategory category) { return this.categoryButtons.get(category); }
    @NotNull public ProgressConfig progress() { return this.progress; }
    @NotNull public SellProgress.BarConfig progressBar() { return this.progressBar; }

    private static ProgressConfig readProgress(ConfigurationSection config, @Nullable PaginationConfig paginationConfig) throws ConfigException {
        if (config == null) throw new ConfigException("GUI sell progress section is missing");
        ConfigurationSection section = config.getConfigurationSection("progress");
        if (section == null) section = config;
        int rows = section.getInt("rows", 6);
        if (rows < 1 || rows > 6) throw new ConfigException("GUI progress rows must be between 1 and 6");
        int size = rows * 9;

        Map<Integer, List<Integer>> pageSlots = readPageSlots(section, size);
        List<Integer> slots = new ArrayList<>();
        if (!pageSlots.isEmpty() && pageSlots.containsKey(1)) {
            slots.addAll(pageSlots.get(1));
        }

        ConfigurationSection prevSec = section.getConfigurationSection("previous");
        int previous = prevSec != null && prevSec.contains("slot")
            ? readSingleSlot(prevSec.getInt("slot"), size, "previous.slot")
            : readSingleSlot(section.getInt("previous-slot", 46), size, "progress.previous-slot");

        ConfigurationSection nextSec = section.getConfigurationSection("next");
        int next = nextSec != null && nextSec.contains("slot")
            ? readSingleSlot(nextSec.getInt("slot"), size, "next.slot")
            : readSingleSlot(section.getInt("next-slot", 54), size, "progress.next-slot");

        ConfigurationSection sumSec = section.getConfigurationSection("summary");
        int summary = sumSec != null && sumSec.contains("slot")
            ? readSingleSlot(sumSec.getInt("slot"), size, "summary.slot")
            : readSingleSlot(section.getInt("summary-slot", 50), size, "progress.summary-slot");

        ConfigurationSection panes = section.getConfigurationSection("panes");
        if (panes == null) throw new ConfigException("GUI progress panes section is missing");
        Pane inProgress = new Pane("in-progress", readItem(required(panes, "in-progress"), "in-progress"));
        Pane completed = new Pane("completed", readItem(required(panes, "completed"), "completed"));
        Pane incomplete = new Pane("incomplete", readItem(required(panes, "incomplete"), "incomplete"));

        ItemDefinition defaultPrev = paginationConfig != null && paginationConfig.previousItem() != null
            ? new ItemDefinition("previous", paginationConfig.previousItem().material(), paginationConfig.previousItem().name(), paginationConfig.previousItem().lore())
            : new ItemDefinition("previous", Material.ARROW, "<#9CC0D9>ᴛʀᴀɴɢ ᴛʀướᴄ", List.of("<white>Nhấp để quay về trang trước"));
        ItemDefinition defaultNext = paginationConfig != null && paginationConfig.nextItem() != null
            ? new ItemDefinition("next", paginationConfig.nextItem().material(), paginationConfig.nextItem().name(), paginationConfig.nextItem().lore())
            : new ItemDefinition("next", Material.ARROW, "<#9CC0D9>ᴛʀᴀɴɢ ѕᴀᴜ", List.of("<white>Nhấp để sang trang sau"));

        ItemDefinition previousItem = readItemWithFallback(prevSec, "previous", defaultPrev);
        ItemDefinition nextItem = readItemWithFallback(nextSec, "next", defaultNext);

        ItemDefinition defaultSum = new ItemDefinition("summary", Material.AIR, "<#9CC0D9>{SECTION}", List.of(
            "<white>ʜệ ѕố ᴛɪếᴘ ᴛʜᴇᴏ: <#9CC0D9>{NEXT_MULTIPLIER}x",
            "<white>{PROGRESS_BAR} <#9CC0D9>{PERCENTAGE}%",
            "<dark_gray>{CURRENT_FORMATTED}/{NEXT_TARGET_FORMATTED}",
            "<gray>Bán ᴠậᴛ ᴘʜẩᴍ để nâng cấp hệ số bán của bạn"
        ));
        ItemDefinition summaryItem = readItemWithFallback(sumSec, "summary", defaultSum);

        boolean filePagination = true;
        if (section.isBoolean("pagination")) {
            filePagination = section.getBoolean("pagination");
        } else if (section.isConfigurationSection("pagination")) {
            filePagination = section.getBoolean("pagination.enabled", true);
        }
        boolean paginationEnabled = filePagination && (paginationConfig == null || paginationConfig.sellMultiplierEnabled());

        return new ProgressConfig(section.getString("title", "<dark_gray>Progress {SECTION} — {CURRENT_PAGE}/{MAX_PAGE}"),
            rows, List.copyOf(slots), pageSlots, summary, previous, next, inProgress, completed, incomplete, previousItem, nextItem, summaryItem, paginationEnabled);
    }

    /**
     * Reads the {@code progress-slots} (or legacy {@code page-slots}) map (page number to 1-based slots).
     * When absent, distributes the flat {@code slots} list evenly across as many pages as the thresholds need.
     */
    private static Map<Integer, List<Integer>> readPageSlots(ConfigurationSection section, int size) throws ConfigException {
        Map<Integer, List<Integer>> pages = new java.util.TreeMap<>();
        ConfigurationSection configured = section.getConfigurationSection("progress-slots");
        if (configured == null) configured = section.getConfigurationSection("page-slots");

        if (configured != null) {
            for (String key : configured.getKeys(false)) {
                int page;
                try {
                    String cleanKey = key;
                    if (cleanKey.toLowerCase(java.util.Locale.ROOT).startsWith("page-")
                        || cleanKey.toLowerCase(java.util.Locale.ROOT).startsWith("page_")
                        || cleanKey.toLowerCase(java.util.Locale.ROOT).startsWith("page")) {
                        cleanKey = cleanKey.replaceAll("[^0-9]", "");
                    }
                    page = Integer.parseInt(cleanKey);
                } catch (NumberFormatException exception) {
                    throw new ConfigException("GUI progress page slots has non-numeric page: " + key);
                }
                if (page < 1) throw new ConfigException("GUI progress page slots has invalid page: " + key);
                List<Integer> pageList = new ArrayList<>();
                for (Integer value : configured.getIntegerList(key)) {
                    pageList.add(readSingleSlot(value, size, "progress-slots." + key));
                }
                if (pageList.isEmpty()) throw new ConfigException("GUI progress page slots " + key + " cannot be empty");
                pages.put(page, List.copyOf(pageList));
            }
        }
        if (!pages.isEmpty()) return Map.copyOf(pages);

        List<Integer> fallback = new ArrayList<>();
        for (Integer value : section.getIntegerList("slots")) {
            fallback.add(readSingleSlot(value, size, "progress.slots"));
        }
        if (fallback.isEmpty()) {
            int[] defaults = {11, 20, 29, 38, 39, 40, 31, 22, 13, 14, 15, 24, 33, 42, 43, 44, 35, 26, 17, 10};
            for (int value : defaults) fallback.add(value - 1);
        }

        int perPage = Math.max(1, fallback.size());
        int total = SellProgress.thresholds().size();
        int pageCount = Math.max(1, (total + perPage - 1) / perPage);
        for (int page = 1; page <= pageCount; page++) pages.put(page, List.copyOf(fallback));
        return Map.copyOf(pages);
    }

    private static ItemDefinition readItemWithFallback(@Nullable ConfigurationSection section, @NotNull String id,
                                                        @NotNull ItemDefinition fallback) {
        if (section == null) return fallback;
        String materialName = section.getString("material");
        Material material = parseMaterial(materialName);
        if (material == null || isAir(material)) {
            material = fallback.material();
        }
        String name = section.getString("name", fallback.name());
        List<String> lore = section.contains("lore") ? List.copyOf(section.getStringList("lore")) : fallback.lore();
        return new ItemDefinition(id, material, name, lore);
    }

    private static ConfigurationSection required(ConfigurationSection parent, String key) throws ConfigException {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) throw new ConfigException("GUI sell section is missing: " + key);
        return section;
    }

    private static ItemDefinition readItem(ConfigurationSection section, String id) throws ConfigException {
        String materialName = section.getString("material");
        Material material = parseMaterial(materialName);
        if (material == null || isAir(material)) throw new ConfigException("GUI item '" + id + "' has unknown material: " + materialName);
        return new ItemDefinition(id, material, section.getString("name", ""), List.copyOf(section.getStringList("lore")));
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static Material parseMaterial(String name) {
        if (name == null || name.isBlank()) return null;
        String clean = name.trim();
        if (clean.regionMatches(true, 0, "minecraft:", 0, 10)) {
            clean = clean.substring(10);
        }
        try {
            return Material.valueOf(clean.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            try {
                return Material.matchMaterial(name);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private static SellProgress.BarConfig readProgressBar(ConfigurationSection multiplierConfig,
                                                          ConfigurationSection sellConfig) throws ConfigException {
        ConfigurationSection section = null;
        if (multiplierConfig != null) {
            section = multiplierConfig.getConfigurationSection("progress-bar");
            if (section == null) section = multiplierConfig.getConfigurationSection("progress.progress-bar");
        }
        if (section == null && sellConfig != null) {
            section = sellConfig.getConfigurationSection("progress-bar");
        }
        if (section == null) return SellProgress.BarConfig.DEFAULT;

        int length = section.getInt("length", SellProgress.BAR_LENGTH);
        if (length < 1 || length > 64) {
            throw new ConfigException("GUI progress-bar length must be between 1 and 64: " + length);
        }
        String complete = section.getString("complete", "<#9CC0D9>■");
        if (complete == null || complete.isBlank()) {
            throw new ConfigException("GUI progress-bar complete cannot be empty");
        }
        String incomplete = section.getString("incomplete", "<dark_gray>■");
        if (incomplete == null || incomplete.isBlank()) {
            throw new ConfigException("GUI progress-bar incomplete cannot be empty");
        }
        return new SellProgress.BarConfig(length, complete, incomplete);
    }

    private static Set<Integer> readSlots(List<Integer> values, int size, String path) throws ConfigException {
        Set<Integer> slots = new LinkedHashSet<>();
        for (Integer value : values) slots.add(readSingleSlot(value, size, path));
        return slots;
    }

    private static int readSingleSlot(int value, int size, String path) throws ConfigException {
        int slot = value - 1;
        if (slot < 0 || slot >= size) throw new ConfigException("GUI slot out of bounds in " + path + ": " + value);
        return slot;
    }

    public record CategoryButton(@NotNull SellCategory category, int slot, @NotNull ItemDefinition item) {
    }

    public record ProgressConfig(
        @NotNull String title, int rows, @NotNull List<Integer> slots,
        @NotNull Map<Integer, List<Integer>> pageSlots, int summarySlot, int previousSlot, int nextSlot,
        @NotNull Pane inProgress, @NotNull Pane completed, @NotNull Pane incomplete,
        @NotNull ItemDefinition previous, @NotNull ItemDefinition next,
        @NotNull ItemDefinition summary,
        boolean paginationEnabled
    ) {
        public ProgressConfig(
            @NotNull String title, int rows, @NotNull List<Integer> slots,
            @NotNull Map<Integer, List<Integer>> pageSlots, int summarySlot, int previousSlot, int nextSlot,
            @NotNull Pane inProgress, @NotNull Pane completed, @NotNull Pane incomplete,
            @NotNull ItemDefinition previous, @NotNull ItemDefinition next
        ) {
            this(title, rows, slots, pageSlots, summarySlot, previousSlot, nextSlot, inProgress, completed, incomplete, previous, next,
                new ItemDefinition("summary", Material.AIR, "<#9CC0D9>{SECTION}", List.of()), true);
        }

        public ProgressConfig(
            @NotNull String title, int rows, @NotNull List<Integer> slots,
            @NotNull Map<Integer, List<Integer>> pageSlots, int summarySlot, int previousSlot, int nextSlot,
            @NotNull Pane inProgress, @NotNull Pane completed, @NotNull Pane incomplete,
            @NotNull ItemDefinition previous, @NotNull ItemDefinition next,
            boolean paginationEnabled
        ) {
            this(title, rows, slots, pageSlots, summarySlot, previousSlot, nextSlot, inProgress, completed, incomplete, previous, next,
                new ItemDefinition("summary", Material.AIR, "<#9CC0D9>{SECTION}", List.of()), paginationEnabled);
        }

        public int size() { return this.rows * 9; }

        public int pageCount() {
            if (!this.paginationEnabled) return 1;
            return this.pageSlots.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        }

        /** Slots for a 1-based page, falling back to the flat {@code slots} list. */
        @NotNull
        public List<Integer> slotsForPage(int page) {
            return this.pageSlots.getOrDefault(page, this.slots);
        }

        /** Threshold index the given 1-based page starts at, based on the preceding pages' slot counts. */
        public int startIndex(int page) {
            int start = 0;
            for (int previous = 1; previous < page; previous++) start += this.slotsForPage(previous).size();
            return start;
        }

        /** Every slot any page can occupy, so stale panes can be cleared before repopulating. */
        @NotNull
        public List<Integer> allProgressSlots() {
            Set<Integer> all = new LinkedHashSet<>();
            for (List<Integer> page : this.pageSlots.values()) all.addAll(page);
            return List.copyOf(all);
        }
    }

    public record Pane(@NotNull String id, @NotNull ItemDefinition item) {
    }

    public record ItemDefinition(@NotNull String id, @NotNull Material material, @NotNull String name, @NotNull List<String> lore) {
        public ItemDefinition {
            lore = List.copyOf(lore);
        }

        @NotNull
        public ItemStack createItem(@NotNull Player player, @NotNull LangService lang) {
            return this.createItem(player, lang, Map.of());
        }

        @NotNull
        public ItemStack createItem(@NotNull Player player, @NotNull LangService lang, @NotNull Map<String, String> placeholders) {
            String renderedName = replace(this.name, placeholders);
            ItemStack item = new ItemStack(this.material);
            ItemMeta meta = item.getItemMeta();
            if (!renderedName.isEmpty()) {
                meta.displayName(lang.parse(renderedName, player).decoration(TextDecoration.ITALIC, false));
            }
            if (!this.lore.isEmpty()) {
                List<Component> rendered = new ArrayList<>(this.lore.size());
                for (String line : this.lore) {
                    rendered.add(lang.parse(replace(line, placeholders), player).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(rendered);
            }
            item.setItemMeta(meta);
            return item;
        }

        private static String replace(String text, Map<String, String> placeholders) {
            String result = text;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            return result;
        }
    }
}
