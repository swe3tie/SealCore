package su.nightexpress.excellenteconomy.config;

import static su.nightexpress.excellenteconomy.EconomyPlaceholders.CURRENCY_PREFIX;
import static su.nightexpress.excellenteconomy.EconomyPlaceholders.WIKI_PLACEHOLDERS;
import static su.nightexpress.nightcore.util.text.night.wrapper.TagWrappers.BOLD;
import static su.nightexpress.nightcore.util.text.night.wrapper.TagWrappers.DARK_GRAY;
import static su.nightexpress.nightcore.util.text.night.wrapper.TagWrappers.SOFT_YELLOW;

import su.nightexpress.nightcore.config.ConfigValue;

public class Config {

    public static final ConfigValue<Boolean> INTEGRATION_VAULT_ENABLED = ConfigValue.create("Integration.Vault.Enabled",
        true,
        "Enables Vault integration."
    );

    public static final ConfigValue<String> INTEGRATION_VAULT_ECONOMY_CURRENCY = ConfigValue.create(
        "Integration.Vault.EconomyCurrency",
        "money",
        "Sets a currency used as primary sever economy using the Vault API."
    );

    public static final ConfigValue<Boolean> TOPS_ENABLED = ConfigValue.create("Top.Enabled",
        true,
        "Turns the entire leaderboard system on or off.",
        "[*] This feature is required for the 'server balance' placeholders to work."
    );

    public static final ConfigValue<Boolean> TOPS_USE_GUI = ConfigValue.create("Top.Use_GUI",
        true,
        "Enables a visual menu for players to view rankings."
    );

    public static final ConfigValue<Integer> TOPS_ENTRIES_PER_PAGE = ConfigValue.create("Top.Entries_Per_Page",
        10,
        "Sets how many players are shown on a single page in the chat-based rankings.",
        "For the GUI, there is an explicit slots setting in the GUI config."
    );

    public static final ConfigValue<Integer> TOPS_UPDATE_INTERVAL = ConfigValue.create("Top.Update_Interval",
        900,
        "Controls how often the plugin recalculates the top player list.",
        "[Asynchronous]",
        "[Default is 900 (15 minutes)]"
    );

    public static final ConfigValue<Boolean> CURRENCY_PREFIX_ENABLED = ConfigValue.create("Currency.Prefix.Enabled",
        true,
        "Controls whether or not currency messages will use custom prefix instead of the plugin's one."
    );

    public static final ConfigValue<String> CURRENCY_PREFIX_FORMAT = ConfigValue.create("Currency.Prefix.Format",
        SOFT_YELLOW.wrap(BOLD.wrap(CURRENCY_PREFIX)) + DARK_GRAY.wrap(" » "),
        "Sets custom prefix format for currency messages.",
        "You can use 'Currency' placeholders: " + WIKI_PLACEHOLDERS
    );

    public static final ConfigValue<Boolean> WALLET_ENABLED = ConfigValue.create("Wallet.Enabled",
        true,
        "Controls whether Wallet feature is enabled."
    );

    public static final ConfigValue<String[]> WALLET_ALIASES = ConfigValue.create("Wallet.Command_Aliases",
        new String[]{"wallet"},
        "Command aliases for the Wallet feature."
    );

    public static final ConfigValue<Boolean> MIGRATION_ENABLED = ConfigValue.create("Migration.Enabled",
        true,
        "Controls whether Migration feature is available.",
        "Disable if you don't plan to migrate from other plugins to save some RAM."
    );

    public static final ConfigValue<Boolean> LOGS_TO_CONSOLE = ConfigValue.create("Logs.Enabled.Console",
        false,
        "Controls whether currency operations will be logged to console."
    );

    public static final ConfigValue<Boolean> LOGS_TO_FILE = ConfigValue.create("Logs.Enabled.File",
        true,
        "Controls whether currency operations will be logged to a file."
    );

    public static final ConfigValue<String> LOGS_DATE_FORMAT = ConfigValue.create("Logs.DateFormat",
        "dd/MM/yyyy HH:mm:ss",
        "Logs date format."
    );

    public static final ConfigValue<Integer> LOGS_WRITE_INTERVAL = ConfigValue.create("Logs.Write_Interval",
        5,
        "Controls how often currency operations writes to the log file."
    );

    public static boolean isTopsEnabled() {
        return TOPS_ENABLED.get();
    }

    public static boolean isWalletEnabled() {
        return WALLET_ENABLED.get();
    }

    public static boolean isMigrationEnabled() {
        return MIGRATION_ENABLED.get();
    }
}
