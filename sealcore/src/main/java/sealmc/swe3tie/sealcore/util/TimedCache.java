package sealmc.swe3tie.sealcore.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cache for expensive lookups.
 *
 * <p>Placeholder strings are re-parsed on every HUD tick for every viewer, so
 * the parsed {@code net.kyori.adventure.text.Component} is cached behind a short
 * TTL and can be invalidated the moment a producer reports a new value.
 */
public final class TimedCache<K, V> {

    private final long ttlMillis;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TimedCache() {
        this(1_000L);
    }

    public TimedCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public V get(K key, Function<K, V> producer) {
        long now = System.currentTimeMillis();
        Entry<V> cached = entries.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached.value;
        }
        V value = producer.apply(key);
        entries.put(key, new Entry<>(value, now + ttlMillis));
        return value;
    }

    public void invalidate(K key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    private static final class Entry<V> {
        private final V value;
        private final long expiresAt;

        private Entry(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
