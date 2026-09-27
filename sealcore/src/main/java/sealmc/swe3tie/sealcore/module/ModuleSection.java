package sealmc.swe3tie.sealcore.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Typed, forgiving reader over one module file.
 *
 * <p>Every getter takes a default, so a partial file is always usable: a key
 * the operator deleted falls back to the shipped default instead of failing.
 * Values that are present but wrong are recorded as a problem and the default
 * is returned, so one typo surfaces as one clear message naming the file and
 * key rather than a stack trace.
 */
public final class ModuleSection {

    private final String id;
    private final String fileName;
    private final ConfigurationSection raw;

    private final List<String> problems = new ArrayList<>();

    public ModuleSection(String id, String fileName, ConfigurationSection raw) {
        this.id = id;
        this.fileName = fileName;
        this.raw = raw;
    }

    public String id() {
        return id;
    }

    public String fileName() {
        return fileName;
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    /** Top level keys of this file, used for warning about unknown keys. */
    public Set<String> keys() {
        return raw.getKeys(false);
    }

    public boolean contains(String path) {
        return raw.isSet(path);
    }

    /** Raw value as written, or null when the key is absent. */
    public Object rawValue(String path) {
        return raw.get(path);
    }

    public String string(String path, String fallback) {
        Object value = raw.get(path);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text) {
            return text;
        }
        problem(path, "text", value);
        return fallback;
    }

    public boolean bool(String path, boolean fallback) {
        Object value = raw.get(path);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        problem(path, "true or false", value);
        return fallback;
    }

    public int integer(String path, int fallback) {
        return integer(path, fallback, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public int integer(String path, int fallback, int min, int max) {
        Object value = raw.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            problem(path, "a whole number", value);
            return fallback;
        }
        int result = number.intValue();
        if (result < min || result > max) {
            problem(path, "a whole number in " + min + ".." + max, result);
            return fallback;
        }
        return result;
    }

    public long number(String path, long fallback) {
        return number(path, fallback, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public long number(String path, long fallback, long min, long max) {
        Object value = raw.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            problem(path, "a whole number", value);
            return fallback;
        }
        long result = number.longValue();
        if (result < min || result > max) {
            problem(path, "a whole number in " + min + ".." + max, result);
            return fallback;
        }
        return result;
    }

    public double decimal(String path, double fallback) {
        return decimal(path, fallback, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public double decimal(String path, double fallback, double min, double max) {
        Object value = raw.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            problem(path, "a number", value);
            return fallback;
        }
        double result = number.doubleValue();
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            problem(path, "a finite number", result);
            return fallback;
        }
        if (result < min || result > max) {
            problem(path, "a number in " + min + ".." + max, result);
            return fallback;
        }
        return result;
    }

    /** Enum lookup, case insensitive, so {@code type: SQLite} and {@code type: sqlite} agree. */
    public <E extends Enum<E>> E enumValue(String path, E fallback, E[] values) {
        Object value = raw.get(path);
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        for (E candidate : values) {
            if (candidate.name().equalsIgnoreCase(text)) {
                return candidate;
            }
        }
        List<String> names = new ArrayList<>(values.length);
        for (E candidate : values) {
            names.add(candidate.name());
        }
        problem(path, "one of " + String.join(", ", names), text);
        return fallback;
    }

    public List<String> stringList(String path) {
        Object value = raw.get(path);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            problem(path, "a list of text values", value);
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof String text) {
                result.add(text);
            }
        }
        return result;
    }

    public Map<String, String> stringMap(String path) {
        ConfigurationSection section = raw.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value == null) {
                continue;
            }
            if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                problem(path + "." + key, "a simple value", value);
                continue;
            }
            result.put(key, value.toString());
        }
        return result;
    }

    public Map<String, Double> doubleMap(String path, double min, double max) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : stringMap(path).entrySet()) {
            String key = entry.getKey();
            String text = entry.getValue();
            double value;
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException notANumber) {
                problem(path + "." + key, "a number", text);
                continue;
            }
            if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
                problem(path + "." + key, "a number in " + min + ".." + max, text);
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    private void problem(String path, String expected, Object actual) {
        String shown = actual == null ? "nothing" : "\"" + actual + "\"";
        problems.add(fileName + " :: " + path + " -> expected " + expected + ", got " + shown);
    }
}
