package sealmc.swe3tie.sealcore.module;

import sealmc.swe3tie.sealcore.command.CommandNode;

import java.util.function.Function;

/**
 * A command a feature module owns.
 *
 * <p>The name has to exist in {@code plugin.yml} as well, because that is where
 * the server looks it up; the tree is built here so permissions, behaviour and
 * the tab completer all come from the same place. The builder is called on
 * every bind, so it may read the spec that is active right now.
 */
public final class SealCommand {

    private final SealModule<?> module;
    private final String name;
    private final String description;
    private final String usage;
    private final Function<ModuleContext, CommandNode> build;

    public SealCommand(
        SealModule<?> module,
        String name,
        String description,
        String usage,
        Function<ModuleContext, CommandNode> build
    ) {
        this.module = module;
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.build = build;
    }

    public SealModule<?> module() {
        return module;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String usage() {
        return usage;
    }

    public CommandNode build(ModuleContext context) {
        return build.apply(context);
    }
}
