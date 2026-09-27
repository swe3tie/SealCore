package su.nightexpress.excellenteconomy.command;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import su.nightexpress.excellenteconomy.EconomyConfigTypes;
import su.nightexpress.excellenteconomy.EconomyFiles;
import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.command.currency.CommandDefinition;
import su.nightexpress.excellenteconomy.command.currency.CurrencyCommand;
import su.nightexpress.excellenteconomy.command.currency.RegisterContext;
import su.nightexpress.excellenteconomy.config.Lang;
import su.nightexpress.excellenteconomy.config.Perms;
import su.nightexpress.excellenteconomy.currency.CurrencyRegistry;
import su.nightexpress.nightcore.commands.Commands;
import su.nightexpress.nightcore.commands.NodeExecutor;
import su.nightexpress.nightcore.commands.builder.ExecutableNodeBuilder;
import su.nightexpress.nightcore.commands.command.NightCommand;
import su.nightexpress.nightcore.commands.tree.ExecutableNode;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.manager.SimpleManager;
import su.nightexpress.nightcore.util.LowerCase;
import su.nightexpress.nightcore.util.placeholder.PlaceholderContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CommandManager extends SimpleManager<EconomyPlugin> {

    private final CurrencyRegistry currencyRegistry;

    private final Set<ExecutableNode> childrens;
    private final Set<NightCommand>   standalones;

    private final Map<String, RegisterContext>   currencyRegisterMap;
    private final Map<String, Set<NightCommand>> currencyCommandMap;

    private NightCommand rootCommand;

    public CommandManager(@NotNull EconomyPlugin plugin, @NotNull CurrencyRegistry currencyRegistry) {
        super(plugin);
        this.currencyRegistry = currencyRegistry;

        this.childrens = new HashSet<>();
        this.standalones = new HashSet<>();

        this.currencyRegisterMap = new HashMap<>();
        this.currencyCommandMap = new HashMap<>();
    }

    @Override
    protected void onLoad() {
        this.addDefaultPluginCommands();
    }

    @Override
    protected void onShutdown() {
        this.unregisterCommands();
    }

    private void loadCommandDefinitions() {
        FileConfig config = FileConfig.load(this.plugin.getDataFolder().toPath().resolve(EconomyFiles.FILE_COMMANDS));

        this.currencyRegisterMap.forEach((id, context) -> {
            String path = "Commands." + id;
            CommandDefinition defaultDefinition = context.getDefaultDefinition();
            CommandDefinition definition;

            if (!config.contains(path)) {
                config.set(path, defaultDefinition);
                definition = defaultDefinition;
            }
            else {
                definition = config.get(EconomyConfigTypes.COMMAND_DEFINITION, path, defaultDefinition);
            }

            context.setConfiguredDefinition(definition);
        });

        config.saveChanges();
    }

    private void addDefaultPluginCommands() {
        this.addPluginCommand(Commands.literal("reload")
            .description(CoreLang.COMMAND_RELOAD_DESC)
            .permission(Perms.COMMAND_RELOAD)
            .executes((context, arguments) -> {
                this.plugin.doReload(context.getSender());
                return true;
            })
        );
    }

    public boolean hasRegisteredCommands(@NonNull ExcellentCurrency currency) {
        return this.currencyCommandMap.containsKey(currency.getId());
    }

    public <N extends ExecutableNode, B extends ExecutableNodeBuilder<N, B>> void addPluginCommand(@NonNull ExecutableNodeBuilder<N, B> node) {
        this.addPluginCommand(node.build());
    }

    public void addPluginCommand(@NonNull ExecutableNode node) {
        this.childrens.add(node);
    }

    public void addStandaloneCommand(@NonNull NightCommand command) {
        this.standalones.add(command);
    }

    public void addCurrencyCommand(@NonNull String id, @NonNull Supplier<CurrencyCommand> supplier,
                                   @NonNull CommandDefinition definition) {
        this.addCurrencyCommand(id, supplier, definition, null);
    }

    public void addCurrencyCommand(@NonNull String id, @NonNull Supplier<CurrencyCommand> supplier,
                                   @NonNull CommandDefinition definition,
                                   @Nullable Predicate<ExcellentCurrency> predicate) {
        this.currencyRegisterMap.put(id, new RegisterContext(supplier, definition, predicate));
    }

    public void registerCommands() {
        this.registerPluginCommands();
        this.registerStandaloneCommands();

        this.loadCommandDefinitions();
        this.registerCurrencyCommands();
    }

    public void registerPluginCommands() {
        this.rootCommand = NightCommand.forPlugin(this.plugin, root -> {
            root.branch(this.childrens.toArray(new ExecutableNode[0]));
        });
        if (!this.claimable(this.rootCommand)) return;
        this.rootCommand.register();
    }

    public void registerStandaloneCommands() {
        this.standalones.removeIf(command -> !this.claimable(command));
        this.standalones.forEach(NightCommand::register);
    }

    public void registerCurrencyCommands() {
        this.currencyRegistry.getCurrencies().forEach(this::registerCurrencyCommands);
    }

    public void registerCurrencyCommands(@NonNull ExcellentCurrency currency) {
        this.registerCurrencyCommand(currency, NightCommand.hub(this.plugin, currency.getCommandAliases(),
            rootBuilder -> {
                rootBuilder.localized(currency.getName());
                rootBuilder.permission(currency.isPermissionRequired() ? currency.getPermission() : null);
                rootBuilder.description(PlaceholderContext.builder().with(currency.placeholders()).build().apply(
                    Lang.COMMAND_CURRENCY_ROOT_DESC.text()));

                this.currencyRegisterMap.forEach((id, registerContext) -> {
                    if (!registerContext.isAvailable(currency)) return;

                    CommandDefinition definition = registerContext.getDefinitionOrDefault();

                    if (!definition.childrenEnabled() && !definition.standaloneEnabled()) return;

                    CurrencyCommand command = registerContext.createCommand();
                    NodeExecutor executor = (context, arguments) -> command.execute(context, arguments, currency);

                    if (command.isFallback()) {
                        rootBuilder.executes(executor);
                    }

                    if (definition.childrenEnabled()) {
                        rootBuilder.branch(Commands.literal(definition.childrenAlias(), builder -> command.build(builder
                            .executes(executor), currency)));
                    }

                    if (definition.standaloneEnabled() && currency.isPrimary()) {
                        this.registerCurrencyCommand(currency, NightCommand.literal(this.plugin, definition
                            .standaloneAliases(), builder -> command.build(builder.executes(executor), currency)));
                    }
                });
            }));
    }

    public boolean registerCurrencyCommand(@NonNull ExcellentCurrency currency, @NonNull NightCommand command) {
        if (!this.claimable(command)) return false;
        return command.register() && this.currencyCommandMap.computeIfAbsent(currency.getId(), k -> new HashSet<>())
            .add(command);
    }

    /**
     * Whether this plugin may register a command under these names.
     *
     * <p>Upstream registers straight into the command map, which overwrites whatever
     * is already there, and a plugin cannot get a name back afterwards: the server
     * builds its command tree as plugins load, so whichever registered first is the
     * one that answers, and rewriting the map later changes nothing. So the name is
     * left alone instead of taken.
     *
     * <p>The comparison is against what other plugins <em>declare</em> in their own
     * descriptor rather than against who currently holds the name, and that is what
     * makes it independent of load order. A plugin that lists {@code pay} in its
     * plugin.yml meant it, whether or not it has registered yet. A bukkit plugin is
     * loaded before this paper plugin enables, so the declarations are all already
     * readable here.
     */
    private boolean claimable(@NotNull NightCommand command) {
        Map<String, String> declared = this.declaredElsewhere();

        // Bukkit's Command takes the first label as the name and everything after it
        // as aliases, so a command declared only as ["pay"] has an empty alias list
        // and a check on the aliases alone would not see it at all.
        Map<String, String> conflicts = new LinkedHashMap<>();
        this.addIfDeclaredElsewhere(conflicts, declared, command.getName());
        for (String alias : command.getAliases()) {
            this.addIfDeclaredElsewhere(conflicts, declared, alias);
        }
        if (conflicts.isEmpty()) return true;

        List<String> owners = new ArrayList<>(conflicts.size());
        conflicts.forEach((name, owner) -> owners.add("'" + name + "' to " + owner));
        this.plugin.warn("Leaving " + String.join(" and ", owners) + ". That plugin is installed and"
            + " declares the name, so this plugin's own version of that command is not registered."
            + " Move money through the other plugin instead.");
        return false;
    }

    private void addIfDeclaredElsewhere(@NonNull Map<String, String> conflicts,
                                        @NonNull Map<String, String> declared, @NotNull String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (declared.containsKey(lower)) {
            conflicts.putIfAbsent(lower, declared.get(lower));
        }
    }

    /**
     * Every command name and alias another installed plugin declares in its own
     * descriptor, mapped to the plugin that declared it.
     *
     * <p>Read fresh on each call rather than cached. It costs a few hundred string
     * comparisons and runs only while commands are being registered, and not
     * caching means a plugin that loads part way through cannot be missed.
     */
    private Map<String, String> declaredElsewhere() {
        Map<String, String> declared = new HashMap<>();
        for (Plugin other : this.plugin.getServer().getPluginManager().getPlugins()) {
            if (other == this.plugin) continue;

            PluginDescriptionFile meta = other.getDescription();
            if (meta == null) continue;

            String owner = meta.getName();
            Map<String, Map<String, Object>> commands = meta.getCommands();
            if (commands == null) continue;

            commands.forEach((name, section) -> {
                this.declare(declared, name, owner);
                // The usual way an alias is written: a list under the command it
                // belongs to. The old top level commandAliases key is not exposed
                // by the API on any of the supported versions, so it is not read.
                Object aliases = section == null ? null : section.get("aliases");
                if (aliases instanceof Iterable<?> list) {
                    list.forEach(alias -> this.declare(declared, String.valueOf(alias), owner));
                }
            });
        }
        return declared;
    }

    private void declare(@NonNull Map<String, String> declared, @Nullable String name, @NotNull String owner) {
        if (name != null && !name.isEmpty()) {
            declared.putIfAbsent(name.toLowerCase(Locale.ROOT), owner);
        }
    }

    public void unregisterCommands() {
        this.unregisterPluginCommand();
        this.unregisterStandaloneCommands();
        this.unregisterCurrencyCommands();
    }

    public void unregisterPluginCommand() {
        if (this.rootCommand != null) {
            this.rootCommand.unregister();
            this.rootCommand = null;
        }
    }

    public void unregisterStandaloneCommands() {
        this.standalones.forEach(NightCommand::unregister);
        this.standalones.clear();
    }

    public void unregisterCurrencyCommands() {
        this.currencyRegistry.getCurrencies().forEach(this::unregisterCurrencyCommands);
    }

    public void unregisterCurrencyCommands(@NonNull ExcellentCurrency currency) {
        this.unregisterCurrencyCommands(currency.getId());
    }

    public void unregisterCurrencyCommands(@NonNull String currencyId) {
        Set<NightCommand> commands = this.currencyCommandMap.remove(LowerCase.INTERNAL.apply(currencyId));
        if (commands == null) return;

        commands.forEach(NightCommand::unregister);
    }
}
