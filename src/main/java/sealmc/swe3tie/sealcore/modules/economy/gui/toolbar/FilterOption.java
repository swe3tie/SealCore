package sealmc.swe3tie.sealcore.modules.economy.gui.toolbar;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/** Option for filtering items in a paged GUI. */
public record FilterOption<T>(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull Predicate<T> predicate
) {
}
