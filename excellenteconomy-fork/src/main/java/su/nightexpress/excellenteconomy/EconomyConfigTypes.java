package su.nightexpress.excellenteconomy;

import su.nightexpress.excellenteconomy.command.currency.CommandDefinition;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.ConfigType;

public class EconomyConfigTypes {

    public static final ConfigType<CommandDefinition> COMMAND_DEFINITION = ConfigType.of(CommandDefinition::read,
        FileConfig::set);
}
