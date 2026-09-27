package su.nightexpress.excellenteconomy.command;

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

import java.util.HashMap;
import java.util.HashSet;
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
        this.rootCommand.register();
    }

    public void registerStandaloneCommands() {
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
        return command.register() && this.currencyCommandMap.computeIfAbsent(currency.getId(), k -> new HashSet<>())
            .add(command);
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
