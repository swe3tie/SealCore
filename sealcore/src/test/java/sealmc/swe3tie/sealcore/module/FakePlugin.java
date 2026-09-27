package sealmc.swe3tie.sealcore.module;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * The registry needs a data folder, a resource lookup, {@code saveResource} and a
 * logger, and nothing else. Bukkit's {@code Plugin} declares far more than that, so
 * this fake answers the handful the registry calls and returns type-appropriate
 * defaults for the rest.
 */
public final class FakePlugin {

    private final Path root;
    private final Logger logger = Logger.getLogger("SealCoreTest");
    private final Plugin plugin;

    /** Module defaults the registry is allowed to copy out, by relative path. */
    public final Map<String, String> resources = new LinkedHashMap<>();

    public final List<String> savedResources = new ArrayList<>();

    private FakePlugin(Path root) {
        this.root = root;
        logger.setLevel(Level.OFF);
        this.plugin = (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            this::invoke);
    }

    public static FakePlugin create() {
        try {
            return new FakePlugin(Files.createTempDirectory("sealcore-modules"));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** The {@code Plugin} to hand to the code under test. */
    public Plugin asPlugin() {
        return plugin;
    }

    public Logger getLogger() {
        return logger;
    }

    public File getDataFolder() {
        return root.toFile();
    }

    public InputStream getResource(String name) {
        String body = resources.get(name);
        return body == null
            ? null
            : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    public void saveResource(String name, boolean replace) {
        String body = resources.get(name);
        if (body == null) {
            return;
        }
        savedResources.add(name);
        try {
            Path target = root.resolve(name);
            Files.createDirectories(target.getParent());
            Files.writeString(target, body);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** Writes a module file the way an operator would have it on disk. */
    public void writeModuleFile(String name, String yaml) {
        try {
            Path modules = root.resolve("modules");
            Files.createDirectories(modules);
            Files.writeString(modules.resolve(name), yaml);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    public YamlConfiguration readModuleFile(String name) {
        return YamlConfiguration.loadConfiguration(root.resolve("modules").resolve(name).toFile());
    }

    private Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
        return switch (method.getName()) {
            case "getDataFolder" -> getDataFolder();
            case "getLogger" -> getLogger();
            case "getResource" -> getResource((String) args[0]);
            case "saveResource" -> {
                saveResource((String) args[0], (Boolean) args[1]);
                yield null;
            }
            case "isEnabled" -> Boolean.TRUE;
            case "getName" -> "SealCore";
            case "toString" -> "FakePlugin";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    /** Enough of a zero for whatever the registry pokes at on the way past. */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        return 0;
    }
}
