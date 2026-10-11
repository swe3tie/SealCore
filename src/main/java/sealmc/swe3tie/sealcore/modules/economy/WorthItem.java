package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Represents a sellable item loaded from {@code prices.yml}. */
public record WorthItem(
    @NotNull String id,
    @NotNull Material material,
    @Nullable String potionType,
    @NotNull Map<String, Integer> enchantments,
    double basePrice,
    @Nullable SellCategory category,
    @NotNull String displayName
) {

    public WorthItem {
        enchantments = Map.copyOf(enchantments);
    }

    /** Formats a material name into a clean, human-readable display title. */
    @NotNull
    public static String formatMaterialName(@NotNull Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            if (i > 0) builder.append(' ');
            builder.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return builder.toString();
    }
}
