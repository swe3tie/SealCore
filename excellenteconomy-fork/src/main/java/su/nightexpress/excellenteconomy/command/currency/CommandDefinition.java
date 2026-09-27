package su.nightexpress.excellenteconomy.command.currency;

import org.jspecify.annotations.NonNull;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.config.Writeable;
import su.nightexpress.nightcore.configuration.ConfigTypes;

public record CommandDefinition(
                                boolean childrenEnabled,
                                @NonNull String childrenAlias,
                                boolean standaloneEnabled,
                                @NonNull String[] standaloneAliases) implements Writeable {

    @NonNull
    public static CommandDefinition read(@NonNull FileConfig config, @NonNull String path) {
        if (config.contains(path + ".Dedicated")) {
            boolean dState = config.getBoolean(path + ".Dedicated.Enabled");
            String[] dAliases = config.get(ConfigTypes.STRING_ARRAY, path + ".Dedicated.Aliases", new String[0]);
            config.set(path + ".Standalone.Enabled", dState);
            config.setStringArray(path + ".Standalone.Aliases", dAliases);
            config.remove(path + ".Dedicated");
        }
        if (config.contains(path + ".Children.Aliases")) {
            String[] dAliases = config.get(ConfigTypes.STRING_ARRAY, path + ".Children.Aliases", new String[0]);
            if (dAliases.length > 0) {
                config.set(path + ".Children.Alias", dAliases[0]);
            }
            config.remove(path + ".Children.Aliases");
        }

        boolean childrenEnabled = config.get(ConfigTypes.BOOLEAN, path + ".Children.Enabled", true);
        String childrenAlias = config.get(ConfigTypes.STRING, path + ".Children.Alias", "null");
        boolean standaloneEnabled = config.get(ConfigTypes.BOOLEAN, path + ".Standalone.Enabled", true);
        String[] standaloneAliases = config.get(ConfigTypes.STRING_ARRAY, path + ".Standalone.Aliases", new String[0]);

        return new CommandDefinition(childrenEnabled, childrenAlias, standaloneEnabled, standaloneAliases);
    }

    @NonNull
    public static CommandDefinition allEnabled(@NonNull String childrenAlias, @NonNull String... standaloneAliases) {
        return new CommandDefinition(true, childrenAlias, true, standaloneAliases);
    }

    @NonNull
    public static CommandDefinition childOnly(@NonNull String childrenAlias, @NonNull String... standaloneAliases) {
        return new CommandDefinition(true, childrenAlias, false, standaloneAliases);
    }

    @NonNull
    public static CommandDefinition standaloneOnly(@NonNull String childrenAlias,
                                                   @NonNull String... standaloneAliases) {
        return new CommandDefinition(false, childrenAlias, false, standaloneAliases);
    }

    @Override
    public void write(@NonNull FileConfig config, @NonNull String path) {
        config.set(path + ".Children.Enabled", this.childrenEnabled);
        config.set(path + ".Children.Alias", this.childrenAlias);
        config.set(path + ".Standalone.Enabled", this.standaloneEnabled);
        config.setStringArray(path + ".Standalone.Aliases", this.standaloneAliases);
    }
}
