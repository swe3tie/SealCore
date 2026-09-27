package sealmc.swe3tie.sealcore;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sealmc.swe3tie.sealcore.gui.GuiContext;
import sealmc.swe3tie.sealcore.gui.GuiSession;
import sealmc.swe3tie.sealcore.platform.CancellableTask;
import sealmc.swe3tie.sealcore.platform.TaskScheduler;
import sealmc.swe3tie.sealcore.text.Text;

/** Shared fakes for the test suite. */
public final class TestFixtures {

    private TestFixtures() {
    }

    /**
     * Bukkit's {@code Player} is an interface with a few hundred methods, so the
     * tests hand out a no-op proxy rather than a stub implementation. The code under
     * test only ever passes the instance around or reads a couple of getters.
     */
    public static Player fakePlayer() {
        return fakePlayer("Tester", UUID.randomUUID());
    }

    public static Player fakePlayer(String name, UUID id) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "getUniqueId" -> id;
                case "isOnline" -> true;
                case "toString" -> "FakePlayer(" + name + ")";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args == null ? null : args[0]);
                default -> null;
            });
    }

    public static GuiContext fakeContext() {
        return fakeContext(fakePlayer());
    }

    public static GuiContext fakeContext(Player player) {
        return new GuiContext(
            player,
            player.getUniqueId(),
            new GuiSession(player, player.getUniqueId(), 1),
            null,
            Runnable::run);
    }

    public static MessageRecorder recordingSender(String name, Set<String> permissions) {
        return new MessageRecorder(name, permissions, null);
    }

    public static MessageRecorder recordingPlayer(String name) {
        return recordingPlayer(name, UUID.randomUUID(), Set.of());
    }

    public static MessageRecorder recordingPlayer(String name, UUID id, Set<String> permissions) {
        return new MessageRecorder(name, permissions, id);
    }

    /**
     * Records what a command sent, so a test can assert on the exact text a player
     * would see. Bukkit's sender and player types are interfaces with hundreds of
     * methods, so the subject under test gets a proxy and the interesting calls are
     * captured here.
     */
    public static final class MessageRecorder {

        private final String name;
        private final Set<String> permissions;
        private final UUID id;

        public final List<Component> messages = new ArrayList<>();
        public final List<Component> actionbars = new ArrayList<>();

        public MessageRecorder(String name, Set<String> permissions, UUID id) {
            this.name = name;
            this.permissions = permissions;
            this.id = id;
        }

        public List<String> plain() {
            return messages.stream().map(Text::plain).toList();
        }

        public List<String> plainActionbars() {
            return actionbars.stream().map(Text::plain).toList();
        }

        public void clear() {
            messages.clear();
            actionbars.clear();
        }

        public CommandSender asSender() {
            return proxy(CommandSender.class);
        }

        public Player asPlayer() {
            return proxy(Player.class);
        }

        @SuppressWarnings("unchecked")
        private <T> T proxy(Class<T> type) {
            return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "sendMessage" -> record(messages, args);
                    case "sendActionBar" -> record(actionbars, args);
                    case "getName" -> name;
                    case "getUniqueId" -> id;
                    case "hasPermission" -> permissions.contains(args == null ? null : args[0]);
                    case "isOnline" -> true;
                    case "toString" -> "Recording(" + type.getSimpleName() + ":" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> null;
                });
        }

        /** Adventure's {@code Component} overload and Bukkit's {@code String} one. */
        private Object record(List<Component> into, Object[] args) {
            if (args == null || args.length == 0) {
                return null;
            }
            if (args[0] instanceof Component component) {
                into.add(component);
            } else if (args[0] instanceof String text) {
                into.add(Component.text(text));
            }
            return null;
        }
    }

    /** Runs everything inline, so an async command has finished by the time it returns. */
    public static final class InlineScheduler implements TaskScheduler {

        @Override
        public void sync(Runnable task) {
            task.run();
        }

        @Override
        public void syncLater(long delayTicks, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncLater(long delayTicks, Runnable task) {
            task.run();
        }

        @Override
        public CancellableTask repeating(long delayTicks, long periodTicks, Runnable task) {
            task.run();
            return () -> { };
        }

        @Override
        public void player(Player player, Runnable task, Runnable onRetired) {
            task.run();
        }

        @Override
        public void playerLater(Player player, long delayTicks, Runnable task, Runnable onRetired) {
            task.run();
        }

        @Override
        public void playerAsync(Player player, Runnable task) {
            task.run();
        }

        @Override
        public void region(Location location, Runnable task) {
            task.run();
        }

        @Override
        public void regionLater(Location location, long delayTicks, Runnable task) {
            task.run();
        }

        @Override
        public void cancelAll() {
            // Nothing is ever queued.
        }
    }
}
