package sealmc.swe3tie.sealcore.core.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LangFlattenTest {

    @Test
    void flattensNestedKeysAndLists() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            config-version: 1
            core:
              help:
                - 'line one'
                - 'line two'
              reload:
                success: 'ok'
            empty: ''
            """);

        Map<String, List<String>> flat = LangService.flatten(yaml);

        assertEquals(List.of("line one", "line two"), flat.get("core.help"));
        assertEquals(List.of("ok"), flat.get("core.reload.success"));
        assertEquals(List.of(""), flat.get("empty"));
        assertFalse(flat.containsKey("config-version"), "version marker is not a message");
        assertFalse(flat.containsKey("core"), "sections are not messages");
    }

    @Test
    void bundledLanguagesHaveEveryEnglishKey() throws Exception {
        YamlConfiguration en = load("/languages/en.yml");
        YamlConfiguration vi = load("/languages/vi.yml");
        Map<String, List<String>> enKeys = LangService.flatten(en);
        Map<String, List<String>> viKeys = LangService.flatten(vi);

        // vi may be partial (it falls back to English), but must not define unknown keys.
        for (String key : viKeys.keySet()) {
            assertEquals(true, enKeys.containsKey(key), "vi.yml has key not in en.yml: " + key);
        }
    }

    private static YamlConfiguration load(String resource) throws Exception {
        try (var in = LangFlattenTest.class.getResourceAsStream(resource)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
