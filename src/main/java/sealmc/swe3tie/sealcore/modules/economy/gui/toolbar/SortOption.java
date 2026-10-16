package sealmc.swe3tie.sealcore.modules.economy.gui.toolbar;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/** Option for sorting items in a paged GUI. */
public record SortOption<T>(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull Comparator<T> comparator
) {
}
