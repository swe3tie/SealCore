package sealmc.swe3tie.sealcore.config

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import sealmc.swe3tie.sealcore.text.Text
import java.io.File

/**
 * Every player facing string, in one file per language.
 *
 * `config.yml` names the active file with `lang`, for example `lang: en.yml`.
 * Keys are namespaced by module, so `jobs.miner.payout-received` is the
 * namespace and the key together and two modules can never collide.
 *
 * A key missing from the selected language falls back to `en.yml` before it
 * falls back to rendering the key itself, so a partially translated language
 * still shows English rather than blanks in game. Both the miss and a missing
 * key are logged once, not once per player per frame.
 */
class Messages(
    val language: String,
    val fallbackLanguage: String?,
    private val primary: YamlConfiguration,
    private val fallback: YamlConfiguration?,
) {

    private val missing = mutableSetOf<String>()

    fun has(path: String): Boolean = primary.isSet(path) || fallback?.isSet(path) == true

    fun component(path: String, vararg pairs: Pair<String, String>): Component {
        val template = primary.getString(path)
            ?: fallback?.getString(path)
            ?: run {
                if (missing.add(path)) {
                    System.err.println("[SealCore] Missing message key '$path' in $language.")
                }
                return Component.text(path)
            }
        return Text.render(template, *pairs)
    }

    fun text(path: String, vararg pairs: Pair<String, String>): String = Text.plain(component(path, *pairs))

    fun send(sender: CommandSender, path: String, vararg pairs: Pair<String, String>) {
        sender.sendMessage(component(path, *pairs))
    }

    /** Keys present in the language file, useful for spotting stale translations. */
    fun keys(): Set<String> = primary.getKeys(true)

    companion object {

        const val DIRECTORY = "languages"

        /** The language every other file falls back to. */
        const val DEFAULT_LANGUAGE = "en.yml"

        private const val SUFFIX = ".yml"

        /**
         * Turns `en`, `EN`, `en.yml` and `en-US.yml` into a file name.
         *
         * Case is preserved on purpose: language files are matched against jar
         * resources, which is case sensitive, so `en-US.yml` must not quietly
         * become `en-us.yml`.
         */
        fun normalise(language: String?): String {
            val trimmed = language?.trim().orEmpty()
            if (trimmed.isEmpty()) return DEFAULT_LANGUAGE
            val withSuffix = if (trimmed.endsWith(SUFFIX, ignoreCase = true)) {
                trimmed.dropLast(SUFFIX.length) + SUFFIX
            } else {
                trimmed + SUFFIX
            }
            return File(withSuffix).name
        }

        fun load(plugin: Plugin, language: String?): Messages {
            val directory = File(plugin.dataFolder, DIRECTORY)
            if (!directory.exists()) directory.mkdirs()

            val requested = normalise(language)
            val chosen = fileFor(plugin, requested) ?: requested
            val primary = YamlConfiguration.loadConfiguration(File(directory, chosen))

            val fallbackFile = when {
                chosen == DEFAULT_LANGUAGE -> null
                else -> fileFor(plugin, DEFAULT_LANGUAGE)
            }
            val fallback = fallbackFile?.let { YamlConfiguration.loadConfiguration(File(directory, it)) }
            if (fallbackFile == null && chosen != DEFAULT_LANGUAGE) {
                plugin.logger.warning("No languages/$DEFAULT_LANGUAGE to fall back on; missing keys will render raw.")
            }
            return Messages(chosen, fallbackFile, primary, fallback)
        }

        /**
         * Finds a language file, first by the exact name, then case
         * insensitively, then in the jar. A language is a human typed value, so
         * `EN`, `en` and `en-US` all have to land on the file the operator meant
         * rather than silently falling back to English.
         */
        private fun fileFor(plugin: Plugin, name: String): String? {
            val directory = File(plugin.dataFolder, DIRECTORY)
            if (File(directory, name).exists()) return name
            val insensitive = directory.listFiles()
                ?.firstOrNull { it.name.equals(name, ignoreCase = true) && it.isFile }
                ?.name
            if (insensitive != null) return insensitive
            if (plugin.getResource("$DIRECTORY/$name") != null) {
                plugin.saveResource("$DIRECTORY/$name", false)
                return name
            }
            return null
        }
    }
}
