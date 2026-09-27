package su.nightexpress.excellenteconomy.command.currency;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class RegisterContext {

    private final Supplier<CurrencyCommand>    command;
    private final CommandDefinition            definition;
    private final Predicate<ExcellentCurrency> predicate;

    private CommandDefinition configuredDefinition;

    public RegisterContext(@NonNull Supplier<CurrencyCommand> command, @NonNull CommandDefinition definition,
                           @Nullable Predicate<ExcellentCurrency> predicate) {
        this.command = command;
        this.definition = definition;
        this.predicate = predicate;
    }

    public boolean isAvailable(@NonNull ExcellentCurrency currency) {
        return this.predicate == null || this.predicate.test(currency);
    }

    @NonNull
    public CurrencyCommand createCommand() {
        return this.command.get();
    }

    @NonNull
    public CommandDefinition getDefinitionOrDefault() {
        return this.configuredDefinition == null ? this.definition : this.configuredDefinition;
    }

    @NonNull
    public CommandDefinition getDefaultDefinition() {
        return this.definition;
    }

    @Nullable
    public CommandDefinition getConfiguredDefinition() {
        return this.configuredDefinition;
    }

    public void setConfiguredDefinition(@NonNull CommandDefinition configuredDefinition) {
        this.configuredDefinition = configuredDefinition;
    }
}
