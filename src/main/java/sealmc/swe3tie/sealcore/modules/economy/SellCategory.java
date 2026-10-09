package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Price categories used for independent player sell progression. */
public enum SellCategory {
    CROPS("crops", Material.WHEAT),
    ORES("ores", Material.DIAMOND),
    MOBS("mobs", Material.BONE),
    NATURAL("natural", Material.OAK_LEAVES),
    TOOLS("tools", Material.NETHERITE_HELMET),
    FISH("fish", Material.TROPICAL_FISH),
    BOOKS("books", Material.BOOK),
    POTIONS("potions", Material.BREWING_STAND),
    BLOCKS("blocks", Material.BRICK);

    private final String id;
    private final Material representative;

    SellCategory(@NotNull String id, @NotNull Material representative) {
        this.id = id;
        this.representative = representative;
    }

    @NotNull
    public String id() {
        return this.id;
    }

    @NotNull
    public Material representative() {
        return this.representative;
    }

    @Nullable
    public static SellCategory fromId(@Nullable String id) {
        if (id == null) return null;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (SellCategory category : values()) {
            if (category.id.equals(normalized)) return category;
        }
        return null;
    }
}
