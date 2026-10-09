package sealmc.swe3tie.sealcore.modules.economy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.hook.Hook;
import sealmc.swe3tie.sealcore.core.hook.economy.CurrencyService;
import sealmc.swe3tie.sealcore.modules.economy.gui.SellGuiConfig;
import sealmc.swe3tie.sealcore.modules.economy.gui.SellInventoryHolder;
import sealmc.swe3tie.sealcore.modules.economy.util.MoneyFormat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** {@code /sell} and its category progression screens. */
public final class SellCommand implements Listener {

    private final EconomyModule module;
    private volatile ProgressStore progressStore;

    public SellCommand(@NotNull EconomyModule module) {
        this.module = module;
    }

    public void setProgressStore(@NotNull ProgressStore progressStore) {
        this.progressStore = progressStore;
    }

    public void register() {
        var section = this.module.config().getConfigurationSection("commands.sell");
        String name = section != null ? section.getString("name", "sell") : "sell";
        List<String> aliases = section != null ? section.getStringList("aliases") : List.of();
        String permission = section != null ? section.getString("permission", "sealcore.economy.sell") : "sealcore.economy.sell";
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .executes(ctx -> this.openGui(ctx.getSource().getSender()));
        this.module.core().commands().register(this.module::isActive, root, "Open sell menu", aliases, permission);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view.getTopInventory().getHolder() instanceof SellInventoryHolder holder) {
                if (holder.isProgressGui()) holder.suppressClose();
                player.closeInventory();
            }
        }
    }

    private int openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.module.core().lang().send(sender, "core.players-only");
            return 0;
        }
        if (this.module.currency() == null) {
            this.module.core().lang().send(player, "core.hook-missing", Placeholder.unparsed("hook", Hook.ECONOMY.pluginName()));
            return 0;
        }
        this.openSellGui(player);
        return 1;
    }

    private void openSellGui(@NotNull Player player) {
        SellGuiConfig gui = this.module.guiConfig();
        SellInventoryHolder holder = new SellInventoryHolder(player, gui.sellSlots());
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), this.buildTitle(player, 0D));
        holder.setInventory(inventory);
        this.placeCategoryButtons(player, inventory, gui, Map.of());
        player.openInventory(inventory);
        ProgressStore store = this.progressStore;
        if (store != null) {
            store.load(player.getUniqueId()).whenComplete((progress, error) -> this.module.core().scheduler().entity(player, () -> {
                if (error != null || player.getOpenInventory().getTopInventory() != inventory) return;
                this.placeCategoryButtons(player, inventory, gui, progress);
                this.updateSellGui(player, inventory, holder);
            }));
        }
        this.updateSellGui(player, inventory, holder);
    }

    private void placeCategoryButtons(@NotNull Player player, @NotNull Inventory inventory, @NotNull SellGuiConfig gui,
                                      @NotNull Map<SellCategory, Double> progress) {
        for (SellCategory category : SellCategory.values()) {
            double paid = progress.getOrDefault(category, 0D);
            SellGuiConfig.CategoryButton button = gui.categoryButton(category);
            inventory.setItem(button.slot(), button.item().createItem(player, this.module.core().lang(),
                SellProgress.placeholders(category, paid, button.item().name())));
        }
    }

    private void placeCategoryButtons(@NotNull Player player, @NotNull Inventory inventory, @NotNull SellGuiConfig gui) {
        this.placeCategoryButtons(player, inventory, gui, Map.of());
    }

    @SuppressWarnings("deprecation")
    private void setProgressTitle(@NotNull Player player, int page, int maxPage, @NotNull SellCategory category) {
        String title = LegacyComponentSerializer.legacySection().serialize(this.buildProgressTitle(player, category, page, maxPage));
        player.getOpenInventory().setTitle(title);
    }

    @SuppressWarnings("deprecation")
    private void updateSellGui(@NotNull Player player, @NotNull Inventory inventory, @NotNull SellInventoryHolder holder) {
        this.module.core().scheduler().entityLater(player, () -> {
            if (player.getOpenInventory().getTopInventory() != inventory) return;
            double total = this.module.priceService().calculateTotal(inventory, holder.sellSlots());
            String title = LegacyComponentSerializer.legacySection().serialize(this.buildTitle(player, total));
            player.getOpenInventory().setTitle(title);
        }, null, 1L);
    }

    private boolean isTop(@NotNull InventoryView view, int rawSlot) {
        return rawSlot >= 0 && rawSlot < view.getTopInventory().getSize();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (holder.isProgressGui()) {
            event.setCancelled(true);
            if (this.isTop(event.getView(), rawSlot)) this.handleProgressClick(player, holder, rawSlot);
            return;
        }

        if (!this.isTop(event.getView(), rawSlot)) {
            if (event.getClick().isKeyboardClick()) event.setCancelled(true);
            this.updateSellGui(player, event.getView().getTopInventory(), holder);
            return;
        }

        SellGuiConfig gui = this.module.guiConfig();
        SellCategory clicked = null;
        for (SellCategory category : SellCategory.values()) {
            if (gui.categoryButton(category).slot() == rawSlot) {
                clicked = category;
                break;
            }
        }
        if (clicked != null) {
            event.setCancelled(true);
            this.openProgressGui(player, holder, clicked, 1);
        } else if (!holder.isSellSlot(rawSlot)) {
            event.setCancelled(true);
        } else {
            this.updateSellGui(player, event.getView().getTopInventory(), holder);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (holder.isProgressGui()) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !holder.isSellSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        this.updateSellGui(player, event.getView().getTopInventory(), holder);
    }

    private void openProgressGui(@NotNull Player player, @NotNull SellInventoryHolder previous,
                                 @NotNull SellCategory category, int page) {
        ProgressStore store = this.progressStore;
        if (store == null) return;
        Inventory previousInventory = previous.getInventory();
        store.load(player.getUniqueId()).whenComplete((values, error) -> this.module.core().scheduler().entity(player, () -> {
            if (player.getOpenInventory().getTopInventory() != previousInventory) return;
            if (error != null) {
                this.module.core().getLogger().warning("Could not load sell progress for " + player.getUniqueId() + ": " + error.getMessage());
                return;
            }
            SellGuiConfig.ProgressConfig config = this.module.guiConfig().progress();
            int maxPage = config.pageCount();
            int safePage = Math.max(1, Math.min(page, maxPage));
            SellInventoryHolder holder = new SellInventoryHolder(player, category, safePage);
            Inventory inventory = Bukkit.createInventory(holder, config.size(), this.buildProgressTitle(player, category, safePage, maxPage));
            holder.setInventory(inventory);
            this.populateProgress(player, inventory, category, safePage, maxPage, values.getOrDefault(category, 0D));
            previous.suppressClose();
            player.openInventory(inventory);
        }));
    }

    private void changeProgressPage(@NotNull Player player, @NotNull SellInventoryHolder holder, int page) {
        ProgressStore store = this.progressStore;
        if (store == null || !holder.isProgressGui()) return;
        store.load(player.getUniqueId()).whenComplete((values, error) -> this.module.core().scheduler().entity(player, () -> {
            Inventory inventory = holder.getInventory();
            if (error != null || player.getOpenInventory().getTopInventory() != inventory) return;
            SellGuiConfig.ProgressConfig config = this.module.guiConfig().progress();
            int safePage = Math.max(1, Math.min(page, config.pageCount()));
            holder.setPage(safePage);
            this.populateProgress(player, inventory, holder.category(), safePage, config.pageCount(),
                values.getOrDefault(holder.category(), 0D));
            this.setProgressTitle(player, safePage, config.pageCount(), holder.category());
        }));
    }

    private void populateProgress(@NotNull Player player, @NotNull Inventory inventory, @NotNull SellCategory category,
                                  int page, int maxPage, double paid) {
        SellGuiConfig.ProgressConfig config = this.module.guiConfig().progress();
        SellGuiConfig.CategoryButton summary = this.module.guiConfig().categoryButton(category);
        inventory.setItem(config.summarySlot(), summary.item().createItem(player, this.module.core().lang(),
            SellProgress.placeholders(category, paid, summary.item().name())));

        List<SellProgress.Threshold> thresholds = SellProgress.thresholds();
        int start = (page - 1) * config.slots().size();
        for (int i = 0; i < config.slots().size(); i++) {
            int index = start + i;
            if (index >= thresholds.size()) break;
            SellProgress.Threshold threshold = thresholds.get(index);
            SellGuiConfig.ItemDefinition definition = paid >= threshold.required()
                ? config.completed().item() : (SellProgress.next(paid) == threshold ? config.inProgress().item() : config.incomplete().item());
            Map<String, String> placeholders = SellProgress.thresholdPlaceholders(category, paid, threshold,
                "<green>■", "<dark_gray>■", summary.item().name());
            inventory.setItem(config.slots().get(i), definition.createItem(player, this.module.core().lang(), placeholders));
        }
        inventory.setItem(config.previousSlot(), page > 1 ? config.previous().createItem(player, this.module.core().lang()) : null);
        inventory.setItem(config.nextSlot(), page < maxPage ? config.next().createItem(player, this.module.core().lang()) : null);
    }

    private void handleProgressClick(@NotNull Player player, @NotNull SellInventoryHolder holder, int rawSlot) {
        SellGuiConfig.ProgressConfig config = this.module.guiConfig().progress();
        if (rawSlot == config.previousSlot() && holder.page() > 1) {
            this.changeProgressPage(player, holder, holder.page() - 1);
        } else if (rawSlot == config.nextSlot() && holder.page() < config.pageCount()) {
            this.changeProgressPage(player, holder, holder.page() + 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        Player player = holder.player();
        if (holder.consumeSuppressedClose()) return;
        if (holder.isProgressGui()) {
            this.module.core().scheduler().entityLater(player, () -> this.openSellGui(player), null, 1L);
            return;
        }
        this.sellItems(player, holder);
    }

    private void sellItems(@NotNull Player player, @NotNull SellInventoryHolder holder) {
        Inventory inventory = holder.getInventory();
        EnumMap<SellCategory, Double> baseTotals = new EnumMap<>(SellCategory.class);
        List<ItemStack> unsellable = new ArrayList<>();
        for (int slot : holder.sellSlots()) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || stack.getType().isAir()) continue;
            inventory.setItem(slot, null);
            double itemTotal = this.module.priceService().getItemPrice(stack);
            SellCategory category = this.module.priceService().category(stack);
            if (itemTotal > 0D && category != null) baseTotals.merge(category, itemTotal, Double::sum);
            else unsellable.add(stack);
        }
        this.returnItems(player, unsellable);
        if (baseTotals.isEmpty() || this.module.currency() == null) return;
        UUID playerId = player.getUniqueId();
        ProgressStore store = this.progressStore;
        if (store == null) return;
        CompletableFuture<Map<SellCategory, Double>> loaded = store.load(playerId);
        loaded.thenCompose(progress -> {
            EnumMap<SellCategory, Double> paidByCategory = new EnumMap<>(SellCategory.class);
            for (Map.Entry<SellCategory, Double> entry : baseTotals.entrySet()) {
                double multiplier = SellProgress.multiplier(progress.getOrDefault(entry.getKey(), 0D));
                paidByCategory.put(entry.getKey(), entry.getValue() * multiplier);
            }
            double total = paidByCategory.values().stream().mapToDouble(Double::doubleValue).sum();
            CurrencyService currency = this.module.currency();
            if (currency == null) return CompletableFuture.completedFuture(0D);
            return currency.deposit(playerId, "money", total, "sell").thenCompose(success -> {
                if (!Boolean.TRUE.equals(success)) return CompletableFuture.completedFuture(0D);
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                for (Map.Entry<SellCategory, Double> entry : paidByCategory.entrySet()) {
                    chain = chain.thenCompose(ignored -> store.add(playerId, entry.getKey(), entry.getValue()).thenApply(values -> null));
                }
                return chain.thenApply(ignored -> total);
            });
        }).whenComplete((total, error) -> {
            if (error != null || total == null || !(total > 0D)) return;
            this.module.core().scheduler().entity(player, () -> this.sendSold(player, total));
        });
    }

    private void returnItems(@NotNull Player player, @NotNull List<ItemStack> items) {
        for (ItemStack item : items) {
            var leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private void sendSold(@NotNull Player player, double total) {
        String standard = MoneyFormat.formatStandard(total);
        String compact = MoneyFormat.formatCompact(total);
        var lang = this.module.core().lang();
        lang.send(player, "economy.sell.sold.chat", Placeholder.unparsed("money", standard), Placeholder.unparsed("money_formatted", compact),
            Placeholder.unparsed("total", standard), Placeholder.unparsed("total_formatted", compact));
        player.sendActionBar(lang.component("economy.sell.sold.actionbar", player,
            Placeholder.unparsed("money", standard), Placeholder.unparsed("money_formatted", compact),
            Placeholder.unparsed("total", standard), Placeholder.unparsed("total_formatted", compact)));
    }

    private Component buildTitle(@NotNull Player player, double total) {
        String standard = MoneyFormat.formatStandard(total);
        String compact = MoneyFormat.formatCompact(total);
        return this.module.core().lang().parse(this.module.guiConfig().title(), player,
            Placeholder.unparsed("total", standard), Placeholder.unparsed("total_formatted", compact),
            Placeholder.unparsed("money", standard), Placeholder.unparsed("money_formatted", compact));
    }

    private Component buildProgressTitle(@NotNull Player player, @NotNull SellCategory category, int page, int maxPage) {
        SellGuiConfig.CategoryButton button = this.module.guiConfig().categoryButton(category);
        String sectionName = button != null && !button.item().name().isBlank()
            ? button.item().name() : SellProgress.categoryLabel(category);
        return this.module.core().lang().parse(this.module.guiConfig().progress().title(), player,
            Placeholder.parsed("section", sectionName),
            Placeholder.unparsed("current_page", Integer.toString(page)),
            Placeholder.unparsed("max_page", Integer.toString(maxPage)));
    }

}
