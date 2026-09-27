package sealmc.swe3tie.sealcore.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
    void aBracePlaceholderIsSubstitutedAndColoursAreStripped() {
        var rendered = Text.render("<white>Bạn có <green>{money}", "money", "$89.89M");

        assertEquals("Bạn có $89.89M", Text.plain(rendered));
    }

    @Test
    void aValueWhoseNameIsAColourIsNotReadAsATag() {
        // The reason for braces. With <white> a value called "white" was
        // swallowed as a tag and left a hole in the message.
        var rendered = Text.render("<white>Bạn có <green>{white}", "white", "$89.89M");

        assertEquals("Bạn có $89.89M", Text.plain(rendered));
    }

    @Test
    void aBracePlaceholderKeepsTheColourOnTheTextAroundIt() {
        var rendered = Text.render("<white>Bạn có <green>{money}", "money", "$89.89M");

        assertEquals(List.of("white=Bạn có ", "green=$89.89M"), leaves(rendered, null));
    }

    /**
     * Flattens a component into {@code colour=text} segments, so an assertion about
     * colours does not depend on how MiniMessage happens to nest its output. Colour
     * is inherited, because that is how the player sees it.
     */
    private static List<String> leaves(Component component, TextColor inherited) {
        TextColor colour = component.color() != null ? component.color() : inherited;
        List<String> out = new ArrayList<>();
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            out.add(colour + "=" + text.content());
        }
        for (Component child : component.children()) {
            out.addAll(leaves(child, colour));
        }
        return out;
    }

    @Test
    void aValueCannotSmuggleMarkupOfItsOwn() {
        var rendered = Text.render("<white>Tiêu: {player}", "player", "<red><bold>free money");

        assertEquals("Tiêu: <red><bold>free money", Text.plain(rendered));
    }

    @Test
    void aValueThatLooksLikeARealTagStillStaysLiteral() {
        // /balance [player] arrives as a value, so its angle brackets have to
        // survive MiniMessage instead of colouring the rest of the line.
        var rendered = Text.render("<gray>Cách dùng: <white>{usage}", "usage", "/balance <player>");

        assertEquals("Cách dùng: /balance <player>", Text.plain(rendered));
    }

    @Test
    void aLanguageFileWrittenBeforeBracesStillWorks() {
        var rendered = Text.render("<white>Bạn có <green><money>", "money", "$89.89M");

        assertEquals("Bạn có $89.89M", Text.plain(rendered));
    }

    @Test
    void aTemplateWithNoPlaceholderAtAllIsJustParsed() {
        assertEquals("You have $89.89M", Text.plain(Text.render("<red>You have $89.89M")));
    }

    @Test
    void anOddNumberOfArgumentsIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class,
            () -> Text.render("<white>{money}", "money"));
    }
}
