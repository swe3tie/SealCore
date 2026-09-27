package sealmc.swe3tie.sealcore.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The {@code <accent>} tag every SealCore string leans on. */
class TextTest {

    @BeforeEach
    void setUp() {
        Text.setAccent(Text.DEFAULT_ACCENT);
    }

    @AfterEach
    void tearDown() {
        Text.setAccent(Text.DEFAULT_ACCENT);
    }

    @Test
    void accentResolvesToTheShippedColour() {
        assertEquals(TextColor.fromHexString("#9CC0D9"), Text.accent());
        assertEquals(TextColor.fromHexString("#9CC0D9"), Text.parse("<accent>SealCore").color());
    }

    @Test
    void theAccentCanBeChangedForTheWholePlugin() {
        assertTrue(Text.setAccent("#ff5555"));

        assertEquals(TextColor.fromHexString("#ff5555"), Text.accent());
        assertEquals(TextColor.fromHexString("#ff5555"), Text.parse("<accent>x").color());
    }

    @Test
    void anUnusableColourIsRefusedAndTheLastGoodOneStays() {
        assertFalse(Text.setAccent("not a colour"));
        assertFalse(Text.setAccent(""));
        assertEquals(TextColor.fromHexString("#9CC0D9"), Text.accent());
    }

    @Test
    void renderingStillSubstitutesPlaceholdersAndStripsColours() {
        var rendered = Text.render("<white>Bạn có <green><money>", "money", "$89.89M");

        assertEquals("Bạn có $89.89M", Text.plain(rendered));
    }
}
