package sealmc.swe3tie.sealcore.service

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Minimal service locator.
 *
 * SealCore is a single plugin with one enable/disable cycle, so a full DI
 * container would be more ceremony than it is worth. Services are registered
 * once during enable and looked up by type afterwards.
 */
class ServiceRegistry {

    private val services = ConcurrentHashMap<KClass<*>, Any>()

    fun <T : Any> register(type: KClass<T>, instance: T): T {
        services[type] = instance
        return instance
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> require(type: KClass<T>): T =
        services[type] as? T ?: error("Service ${type.simpleName} is not registered")

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> find(type: KClass<T>): T? = services[type] as? T

    fun contains(type: KClass<*>): Boolean = services.containsKey(type)

    fun unregister(type: KClass<*>) {
        services.remove(type)
    }

    fun clear() {
        services.clear()
    }

    fun names(): List<String> = services.keys.map { it.simpleName ?: it.toString() }.sorted()
}

inline fun <reified T : Any> ServiceRegistry.register(instance: T): T = register(T::class, instance)

inline fun <reified T : Any> ServiceRegistry.require(): T = require(T::class)

inline fun <reified T : Any> ServiceRegistry.find(): T? = find(T::class)
