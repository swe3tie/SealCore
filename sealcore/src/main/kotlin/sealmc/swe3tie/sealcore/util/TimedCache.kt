package sealmc.swe3tie.sealcore.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for expensive placeholder lookups.
 *
 * Placeholder strings are re-parsed on every HUD tick for every viewer, so the
 * parsed `net.kyori.adventure.text.Component` is cached behind a short TTL and
 * can be invalidated the moment a producer reports a new value.
 */
class TimedCache<K : Any, V : Any>(
    private val ttlMillis: Long = 1_000L,
) {
    private val entries = ConcurrentHashMap<K, Entry<V>>()

    private class Entry<V>(val value: V, val expiresAt: Long)

    fun get(key: K, producer: (K) -> V): V {
        val now = System.currentTimeMillis()
        val cached = entries[key]
        if (cached != null && cached.expiresAt > now) return cached.value
        val value = producer(key)
        entries[key] = Entry(value, now + ttlMillis)
        return value
    }

    fun invalidate(key: K) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }
}
