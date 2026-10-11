package sealmc.swe3tie.sealcore.modules.economy.gui.toolbar;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.core.lang.LangService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable bottom-row navigation toolbar (slots 45, 48, 49, 50, 53) handling
 * pagination, sorting cycles, filtering cycles, and customizable refresh.
 */
public final class PagedGuiToolbar<T> {

    public static final int SLOT_PREVIOUS = 45;
    public static final int SLOT_SORT = 48;
    public static final int SLOT_REFRESH = 49;
    public static final int SLOT_FILTER = 50;
    public static final int SLOT_NEXT = 53;
    public static final int ITEMS_PER_PAGE = 45;

    private final PagedToolbarConfig config;
    private final List<SortOption<T>> sortOptions;
    private final List<FilterOption<T>> filterOptions;
    private final Consumer<Player> onRefresh;
    private final boolean paginationEnabled;
    private Runnable onPageOrOptionChange;

    private List<T> allItems = List.of();
    private List<T> filteredAndSorted = List.of();
    private int page = 1;
    private int currentSortIndex = 0;
    private int currentFilterIndex = 0;

    public PagedGuiToolbar(@NotNull PagedToolbarConfig config,
                           @NotNull List<T> items,
                           @NotNull List<SortOption<T>> sortOptions,
                           @NotNull List<FilterOption<T>> filterOptions,
                           @Nullable Consumer<Player> onRefresh,
                           boolean paginationEnabled) {
        this.config = config;
        this.sortOptions = sortOptions.isEmpty() ? List.of(new SortOption<>("default", "Mặc định", (a, b) -> 0)) : List.copyOf(sortOptions);
        this.filterOptions = filterOptions.isEmpty() ? List.of(new FilterOption<>("all", "Tất cả", x -> true)) : List.copyOf(filterOptions);
        this.onRefresh = onRefresh;
        this.paginationEnabled = paginationEnabled;
        this.setItems(items);
    }

    public PagedGuiToolbar(@NotNull PagedToolbarConfig config,
                           @NotNull List<T> items,
                           @NotNull List<SortOption<T>> sortOptions,
                           @NotNull List<FilterOption<T>> filterOptions,
                           @Nullable Consumer<Player> onRefresh) {
        this(config, items, sortOptions, filterOptions, onRefresh, true);
    }

    public PagedGuiToolbar(@NotNull PagedToolbarConfig config,
                           @NotNull List<T> items,
                           @NotNull List<SortOption<T>> sortOptions,
                           @NotNull List<FilterOption<T>> filterOptions,
                           @Nullable Runnable onRefreshRunnable) {
        this(config, items, sortOptions, filterOptions, onRefreshRunnable != null ? p -> onRefreshRunnable.run() : null, true);
    }

    public PagedGuiToolbar(@NotNull PagedToolbarConfig config,
                           @NotNull List<T> items,
                           @NotNull List<SortOption<T>> sortOptions,
                           @NotNull List<FilterOption<T>> filterOptions,
                           @Nullable Runnable onRefreshRunnable,
                           boolean paginationEnabled) {
        this(config, items, sortOptions, filterOptions, onRefreshRunnable != null ? p -> onRefreshRunnable.run() : null, paginationEnabled);
    }

    public void setOnPageOrOptionChange(@Nullable Runnable onPageOrOptionChange) {
        this.onPageOrOptionChange = onPageOrOptionChange;
    }

    public void setItems(@NotNull List<T> items) {
        this.allItems = List.copyOf(items);
        this.recompute();
    }

    public void recompute() {
        FilterOption<T> filter = this.currentFilter();
        List<T> filtered = new ArrayList<>();
        for (T item : this.allItems) {
            if (filter.predicate().test(item)) {
                filtered.add(item);
            }
        }

        SortOption<T> sort = this.currentSort();
        filtered.sort(sort.comparator());
        this.filteredAndSorted = List.copyOf(filtered);

        int max = this.maxPage();
        if (this.page > max) this.page = max;
        if (this.page < 1) this.page = 1;
    }

    public int page() {
        return this.page;
    }

    public void setPage(int page) {
        int max = this.maxPage();
        this.page = Math.max(1, Math.min(page, max));
        if (this.onPageOrOptionChange != null) this.onPageOrOptionChange.run();
    }

    public boolean paginationEnabled() {
        return this.paginationEnabled;
    }

    public int maxPage() {
        if (!this.paginationEnabled || this.filteredAndSorted.isEmpty()) return 1;
        return (this.filteredAndSorted.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
    }

    @NotNull
    public List<T> pageItems() {
        if (this.filteredAndSorted.isEmpty()) return List.of();
        int start = (this.page - 1) * ITEMS_PER_PAGE;
        if (start >= this.filteredAndSorted.size()) return List.of();
        int end = Math.min(start + ITEMS_PER_PAGE, this.filteredAndSorted.size());
        return this.filteredAndSorted.subList(start, end);
    }

    @NotNull
    public SortOption<T> currentSort() {
        return this.sortOptions.get(this.currentSortIndex);
    }

    public int currentSortIndex() {
        return this.currentSortIndex;
    }

    public void cycleSort() {
        this.currentSortIndex = (this.currentSortIndex + 1) % this.sortOptions.size();
        this.page = 1;
        this.recompute();
        if (this.onPageOrOptionChange != null) this.onPageOrOptionChange.run();
    }

    @NotNull
    public FilterOption<T> currentFilter() {
        return this.filterOptions.get(this.currentFilterIndex);
    }

    public int currentFilterIndex() {
        return this.currentFilterIndex;
    }

    public void cycleFilter() {
        this.currentFilterIndex = (this.currentFilterIndex + 1) % this.filterOptions.size();
        this.page = 1;
        this.recompute();
        if (this.onPageOrOptionChange != null) this.onPageOrOptionChange.run();
    }

    public void triggerRefresh(@NotNull Player player) {
        if (this.onRefresh != null) {
            this.onRefresh.accept(player);
        }
    }

    /** Renders toolbar buttons into the provided inventory. */
    public void renderToolbar(@NotNull Inventory inventory, @NotNull Player player, @NotNull LangService lang) {
        int max = this.maxPage();

        // Slot 45: Previous
        if (this.paginationEnabled && this.page > 1) {
            inventory.setItem(SLOT_PREVIOUS, this.config.previousButton().createItem(player, lang));
        } else {
            inventory.setItem(SLOT_PREVIOUS, null);
        }

        // Slot 48: Sort
        List<String> sortLore = new ArrayList<>(this.sortOptions.size());
        for (int i = 0; i < this.sortOptions.size(); i++) {
            SortOption<T> option = this.sortOptions.get(i);
            String prefix = (i == this.currentSortIndex) ? "<#9CC0D9>• " : "<white>• ";
            sortLore.add(prefix + option.displayName());
        }
        inventory.setItem(SLOT_SORT, this.config.sortButton().createItem(player, lang, sortLore));

        // Slot 49: Refresh
        inventory.setItem(SLOT_REFRESH, this.config.refreshButton().createItem(player, lang));

        // Slot 50: Filter
        List<String> filterLore = new ArrayList<>(this.filterOptions.size());
        for (int i = 0; i < this.filterOptions.size(); i++) {
            FilterOption<T> option = this.filterOptions.get(i);
            String prefix = (i == this.currentFilterIndex) ? "<#9CC0D9>• " : "<white>• ";
            filterLore.add(prefix + option.displayName());
        }
        inventory.setItem(SLOT_FILTER, this.config.filterButton().createItem(player, lang, filterLore));

        // Slot 53: Next
        if (this.paginationEnabled && this.page < max) {
            inventory.setItem(SLOT_NEXT, this.config.nextButton().createItem(player, lang));
        } else {
            inventory.setItem(SLOT_NEXT, null);
        }
    }

    /**
     * Handles inventory click on toolbar slots. Returns true if the click was handled
     * by the toolbar.
     */
    public boolean handleClick(int rawSlot, @NotNull Player player) {
        if (rawSlot == SLOT_PREVIOUS) {
            if (this.paginationEnabled && this.page > 1) {
                this.setPage(this.page - 1);
            }
            return true;
        } else if (rawSlot == SLOT_NEXT) {
            if (this.paginationEnabled && this.page < this.maxPage()) {
                this.setPage(this.page + 1);
            }
            return true;
        } else if (rawSlot == SLOT_SORT) {
            this.cycleSort();
            return true;
        } else if (rawSlot == SLOT_FILTER) {
            this.cycleFilter();
            return true;
        } else if (rawSlot == SLOT_REFRESH) {
            this.triggerRefresh(player);
            return true;
        }
        return false;
    }

    @NotNull
    public PagedToolbarConfig config() {
        return this.config;
    }

    @NotNull
    public List<SortOption<T>> sortOptions() {
        return Collections.unmodifiableList(this.sortOptions);
    }

    @NotNull
    public List<FilterOption<T>> filterOptions() {
        return Collections.unmodifiableList(this.filterOptions);
    }
}
