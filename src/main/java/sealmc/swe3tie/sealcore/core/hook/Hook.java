package sealmc.swe3tie.sealcore.core.hook;

import org.jetbrains.annotations.NotNull;

/** External plugins SealCore integrates with. Modules declare which ones they require. */
public enum Hook {

    ECONOMY("ExcellentEconomy"),
    PLACEHOLDERAPI("PlaceholderAPI"),
    PACKETEVENTS("packetevents");

    private final String pluginName;

    Hook(String pluginName) {
        this.pluginName = pluginName;
    }

    @NotNull
    public String pluginName() {
        return this.pluginName;
    }
}
