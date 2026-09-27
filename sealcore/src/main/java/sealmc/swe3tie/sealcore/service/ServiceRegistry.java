package sealmc.swe3tie.sealcore.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal service locator.
 *
 * <p>SealCore is a single plugin with one enable/disable cycle, so a full DI
 * container would be more ceremony than it is worth. Services are registered
 * once during enable and looked up by type afterwards.
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> T register(Class<T> type, T instance) {
        services.put(type, instance);
        return instance;
    }

    public <T> T require(Class<T> type) {
        T instance = find(type);
        if (instance == null) {
            throw new IllegalStateException("Service " + type.getSimpleName() + " is not registered");
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public <T> T find(Class<T> type) {
        return (T) services.get(type);
    }

    public boolean contains(Class<?> type) {
        return services.containsKey(type);
    }

    public void unregister(Class<?> type) {
        services.remove(type);
    }

    public void clear() {
        services.clear();
    }

    public List<String> names() {
        List<String> names = new ArrayList<>(services.size());
        for (Class<?> type : services.keySet()) {
            names.add(type.getSimpleName());
        }
        names.sort(String::compareTo);
        return names;
    }
}
