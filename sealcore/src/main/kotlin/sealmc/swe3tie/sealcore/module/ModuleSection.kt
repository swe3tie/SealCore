package sealmc.swe3tie.sealcore.module

import org.bukkit.configuration.ConfigurationSection

/**
 * Typed, forgiving reader over one module file.
 *
 * Every getter takes a default, so a partial file is always usable: a key the
 * operator deleted falls back to the shipped default instead of failing. Values
 * that are present but wrong are recorded as a problem and the default is
 * returned, so one typo surfaces as one clear message naming the file and key
 * rather than a stack trace.
 */
class ModuleSection(
    val id: String,
    val fileName: String,
    private val raw: ConfigurationSection,
) {

    private val problems = mutableListOf<String>()

    val hasProblems: Boolean get() = problems.isNotEmpty()

    fun problems(): List<String> = problems.toList()

    /** Top level keys of this file, used for warning about unknown keys. */
    fun keys(): Set<String> = raw.getKeys(false)

    fun contains(path: String): Boolean = raw.isSet(path)

    /** Raw value as written, or null when the key is absent. */
    fun rawValue(path: String): Any? = raw.get(path)

    fun string(path: String, default: String): String {
        val value = raw.get(path) ?: return default
        if (value is String) return value
        problem(path, "text", value)
        return default
    }

    fun bool(path: String, default: Boolean): Boolean {
        val value = raw.get(path) ?: return default
        if (value is Boolean) return value
        problem(path, "true or false", value)
        return default
    }

    fun int(path: String, default: Int, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
        val number = raw.get(path) ?: return default
        if (number !is Number) {
            problem(path, "a whole number", number)
            return default
        }
        val value = number.toInt()
        if (value < min || value > max) {
            problem(path, "a whole number in $min..$max", value)
            return default
        }
        return value
    }

    fun long(path: String, default: Long, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): Long {
        val number = raw.get(path) ?: return default
        if (number !is Number) {
            problem(path, "a whole number", number)
            return default
        }
        val value = number.toLong()
        if (value < min || value > max) {
            problem(path, "a whole number in $min..$max", value)
            return default
        }
        return value
    }

    fun double(path: String, default: Double, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): Double {
        val number = raw.get(path) ?: return default
        if (number !is Number) {
            problem(path, "a number", number)
            return default
        }
        val value = number.toDouble()
        if (value.isNaN() || value.isInfinite()) {
            problem(path, "a finite number", value)
            return default
        }
        if (value < min || value > max) {
            problem(path, "a number in $min..$max", value)
            return default
        }
        return value
    }

    /** Enum lookup, case insensitive, so `type: SQLite` and `type: sqlite` agree. */
    fun <E : Enum<E>> enumValue(path: String, default: E, values: Array<E>): E {
        val text = raw.get(path)?.toString() ?: return default
        val match = values.firstOrNull { it.name.equals(text, ignoreCase = true) }
        if (match == null) {
            problem(path, "one of ${values.joinToString(", ")}", text)
            return default
        }
        return match
    }

    fun stringList(path: String): List<String> {
        val value = raw.get(path) ?: return emptyList()
        if (value !is List<*>) {
            problem(path, "a list of text values", value)
            return emptyList()
        }
        return value.filterIsInstance<String>()
    }

    fun stringMap(path: String): Map<String, String> {
        val section = raw.getConfigurationSection(path) ?: return emptyMap()
        val result = LinkedHashMap<String, String>(section.getKeys(false).size)
        for (key in section.getKeys(false)) {
            val value = section.get(key)
            if (value == null) continue
            if (value !is String && value !is Number && value !is Boolean) {
                problem("$path.$key", "a simple value", value)
                continue
            }
            result[key] = value.toString()
        }
        return result
    }

    fun doubleMap(path: String, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): Map<String, Double> {
        val result = LinkedHashMap<String, Double>()
        for ((key, text) in stringMap(path)) {
            val value = text.toDoubleOrNull()
            if (value == null || value.isNaN() || value.isInfinite()) {
                problem("$path.$key", "a number", text)
                continue
            }
            if (value < min || value > max) {
                problem("$path.$key", "a number in $min..$max", value)
                continue
            }
            result[key] = value
        }
        return result
    }

    private fun problem(path: String, expected: String, actual: Any?) {
        val shown = if (actual == null) "nothing" else "\"$actual\""
        problems += "$fileName :: $path -> expected $expected, got $shown"
    }
}
