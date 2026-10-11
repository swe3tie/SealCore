package sealmc.swe3tie.sealcore.modules.economy.util;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.modules.economy.ItemPriceService;
import sealmc.swe3tie.sealcore.modules.economy.SellCategory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Utilities for inspecting, extracting, and emptying Shulker Boxes and Bundles. */
public final class ContainerHelper {

    private ContainerHelper() {
    }

    public static boolean isAirMaterial(@Nullable Material material) {
        if (material == null) return true;
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    public static boolean isEmptyOrAir(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return true;
        Material type = item.getType();
        return isAirMaterial(type);
    }

    public static boolean isShulkerBox(@Nullable Material material) {
        if (material == null || isAirMaterial(material)) return false;
        return material.name().endsWith("SHULKER_BOX");
    }

    public static boolean isShulkerBox(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || isAirMaterial(item.getType())) return false;
        return isShulkerBox(item.getType());
    }

    public static boolean isBundle(@Nullable Material material) {
        if (material == null || isAirMaterial(material)) return false;
        return material.name().endsWith("BUNDLE");
    }

    public static boolean isBundle(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || isAirMaterial(item.getType())) return false;
        return isBundle(item.getType());
    }

    public static boolean isContainer(@Nullable Material material) {
        return isShulkerBox(material) || isBundle(material);
    }

    public static boolean isContainer(@Nullable ItemStack item) {
        return isShulkerBox(item) || isBundle(item);
    }

    /** Extracts contents from a Shulker Box ItemStack without mutating it. */
    @NotNull
    public static List<ItemStack> getShulkerItems(@NotNull ItemStack item) {
        if (!isShulkerBox(item)) return List.of();
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
                ItemStack[] contents = shulker.getInventory().getContents();
                List<ItemStack> list = new ArrayList<>();
                for (ItemStack s : contents) {
                    if (!isEmptyOrAir(s)) {
                        list.add(s);
                    }
                }
                return list;
            }
        } catch (Throwable ignored) {
            // Server registry not available in unit test environments
        }
        return List.of();
    }

    /** Sets contents of a Shulker Box ItemStack. */
    public static void setShulkerItems(@NotNull ItemStack shulkerItem, @NotNull List<ItemStack> remaining) {
        if (!isShulkerBox(shulkerItem)) return;
        try {
            ItemMeta meta = shulkerItem.getItemMeta();
            if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
                ItemStack[] contents = new ItemStack[27];
                for (int i = 0; i < Math.min(27, remaining.size()); i++) {
                    contents[i] = remaining.get(i);
                }
                shulker.getInventory().setContents(contents);
                bsm.setBlockState(shulker);
                shulkerItem.setItemMeta(bsm);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Extracts items from a Bundle ItemStack. */
    @NotNull
    public static List<ItemStack> getBundleItems(@NotNull ItemStack item) {
        if (!isBundle(item)) return List.of();
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BundleMeta bundle) {
                List<ItemStack> items = bundle.getItems();
                List<ItemStack> list = new ArrayList<>();
                for (ItemStack s : items) {
                    if (!isEmptyOrAir(s)) {
                        list.add(s);
                    }
                }
                return list;
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    /** Sets items in a Bundle ItemStack. */
    public static void setBundleItems(@NotNull ItemStack bundleItem, @NotNull List<ItemStack> remaining) {
        if (!isBundle(bundleItem)) return;
        try {
            ItemMeta meta = bundleItem.getItemMeta();
            if (meta instanceof BundleMeta bundle) {
                bundle.setItems(remaining);
                bundleItem.setItemMeta(bundle);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Recursively processes container contents: sells sellable items, accumulates
     * totals by category, and keeps unsellable items inside.
     */
    @NotNull
    public static ContainerResult processContainerContents(@NotNull List<ItemStack> items,
                                                           @NotNull ItemPriceService priceService) {
        EnumMap<SellCategory, Double> totals = new EnumMap<>(SellCategory.class);
        List<ItemStack> remaining = new ArrayList<>();

        for (ItemStack item : items) {
            if (isEmptyOrAir(item)) continue;

            if (isShulkerBox(item)) {
                List<ItemStack> inner = getShulkerItems(item);
                if (!inner.isEmpty()) {
                    ContainerResult innerResult = processContainerContents(inner, priceService);
                    innerResult.totals().forEach((cat, amt) -> totals.merge(cat, amt, Double::sum));
                    ItemStack returnShulker = item.clone();
                    setShulkerItems(returnShulker, innerResult.remainingUnsellable());
                    remaining.add(returnShulker);
                    continue;
                }
            } else if (isBundle(item)) {
                List<ItemStack> inner = getBundleItems(item);
                if (!inner.isEmpty()) {
                    ContainerResult innerResult = processContainerContents(inner, priceService);
                    innerResult.totals().forEach((cat, amt) -> totals.merge(cat, amt, Double::sum));
                    ItemStack returnBundle = item.clone();
                    setBundleItems(returnBundle, innerResult.remainingUnsellable());
                    remaining.add(returnBundle);
                    continue;
                }
            }

            // Normal item or empty container:
            double price = priceService.getItemPrice(item);
            SellCategory category = priceService.category(item);
            if (price > 0D && category != null) {
                totals.merge(category, price, Double::sum);
            } else {
                remaining.add(item);
            }
        }

        return new ContainerResult(totals, remaining);
    }

    /** Result of processing container items. */
    public record ContainerResult(
        @NotNull Map<SellCategory, Double> totals,
        @NotNull List<ItemStack> remainingUnsellable
    ) {
    }
}
