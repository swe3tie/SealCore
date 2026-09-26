package sealmc.swe3tie.sealcore.module

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The registry needs a data folder, a resource lookup, `saveResource` and a
 * logger, and nothing else, so a no-op proxy covers the rest of [Plugin].
 */
class FakePlugin(private val root: Path) : Plugin by noOpProxy() {

    private val logger: Logger = Logger.getLogger("SealCoreTest").apply { level = Level.OFF }

    /** Module defaults the registry is allowed to copy out, by relative path. */
    val resources: MutableMap<String, String> = linkedMapOf()

    val savedResources = mutableListOf<String>()

    override fun getDataFolder(): File = root.toFile()

    override fun getLogger(): Logger = logger

    override fun getResource(name: String): InputStream? =
        resources[name]?.let { ByteArrayInputStream(it.toByteArray()) }

    override fun saveResource(name: String, replace: Boolean) {
        val body = resources[name] ?: return
        savedResources += name
        val target = root.resolve(name)
        Files.createDirectories(target.parent)
        Files.writeString(target, body)
    }

    /** Writes a module file the way an operator would have it on disk. */
    fun writeModuleFile(name: String, yaml: String) {
        Files.createDirectories(root.resolve("modules"))
        Files.writeString(root.resolve("modules").resolve(name), yaml)
    }

    fun readModuleFile(name: String): YamlConfiguration =
        YamlConfiguration.loadConfiguration(root.resolve("modules").resolve(name).toFile())

    companion object {
        fun create(): FakePlugin = FakePlugin(Files.createTempDirectory("sealcore-modules"))

        /** Covers the parts of [Plugin] the registry never touches. */
        private fun noOpProxy(): Plugin = Proxy.newProxyInstance(
            Plugin::class.java.classLoader,
            arrayOf(Plugin::class.java),
        ) { proxy, method, _ ->
            when (method.name) {
                "toString" -> "FakePlugin"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> null
            }
        } as Plugin
    }
}
