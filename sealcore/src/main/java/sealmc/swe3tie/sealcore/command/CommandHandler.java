package sealmc.swe3tie.sealcore.command;

/** What a command node does when it is reached. */
@FunctionalInterface
public interface CommandHandler {

    void handle(CommandContext context);
}
