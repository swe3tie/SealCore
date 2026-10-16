package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Represents a single record of sold items. */
public record SellHistoryEntry(
    long id,
    @NotNull UUID playerUuid,
    @NotNull Material material,
    @Nullable String customName,
    @Nullable String potionType,
    @Nullable SellCategory category,
    int amount,
    double totalPrice,
    long soldAt
) {

    public SellHistoryEntry(long id, @NotNull UUID playerUuid, @NotNull Material material,
                            @Nullable String customName, @Nullable SellCategory category,
                            int amount, double totalPrice, long soldAt) {
        this(id, playerUuid, material, customName, null, category, amount, totalPrice, soldAt);
    }

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yy—HH:mm")
        .withZone(ZoneId.systemDefault());

    @NotNull
    public String formattedDate() {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(this.soldAt));
    }

    @NotNull
    public String displayName() {
        if (this.customName != null && !this.customName.isBlank()) {
            return this.customName;
        }
        if (this.potionType != null && !this.potionType.isBlank()) {
            return WorthItem.formatMaterialName(this.material) + " (" + formatPotionName(this.potionType) + ")";
        }
        return WorthItem.formatMaterialName(this.material);
    }

    @NotNull
    public static String formatPotionName(@NotNull String name) {
        String clean = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = clean.split(" ");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            if (i > 0) builder.append(' ');
            builder.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return builder.toString();
    }
}
