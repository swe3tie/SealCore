package sealmc.swe3tie.sealcore.command;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.text.Text;

/** Everything a command handler needs, plus typed argument access. */
public final class CommandContext {

    private final CommandSender sender;
    private final String label;
    private final List<String> args;

    public CommandContext(CommandSender sender, String label, List<String> args) {
        this.sender = sender;
        this.label = label;
        this.args = args;
    }

    public CommandSender sender() {
        return sender;
    }

    public String label() {
        return label;
    }

    public List<String> args() {
        return args;
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    public Player player() {
        return sender instanceof Player player ? player : null;
    }

    public String arg(int index) {
        return index < args.size() ? args.get(index) : null;
    }

    public String string(int index, String fallback) {
        String value = arg(index);
        return value == null ? fallback : value;
    }

    public Integer integer(int index) {
        String value = arg(index);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    public Long number(int index) {
        String value = arg(index);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    public Double decimal(int index) {
        String value = arg(index);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    public String join(int from) {
        return String.join(" ", args.subList(Math.min(from, args.size()), args.size()));
    }

    public void reply(Component message) {
        sender.sendMessage(message);
    }

    public void reply(String text) {
        sender.sendMessage(Text.parse(text));
    }

    public void replySuccess(String text) {
        reply(Text.parse("<green>" + text));
    }

    public void replyError(String text) {
        reply(Text.parse("<red>" + text));
    }
}
