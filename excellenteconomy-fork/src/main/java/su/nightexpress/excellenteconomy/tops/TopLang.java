package su.nightexpress.excellenteconomy.tops;

import su.nightexpress.nightcore.locale.LangContainer;
import su.nightexpress.nightcore.locale.LangEntry;
import su.nightexpress.nightcore.locale.entry.IconLocale;

import static su.nightexpress.excellenteconomy.EconomyPlaceholders.*;
import static su.nightexpress.nightcore.util.placeholder.CommonPlaceholders.*;
import static su.nightexpress.nightcore.util.text.night.wrapper.TagWrappers.*;

public class TopLang implements LangContainer {

    public static final IconLocale UI_LEADERBOARD_ENTRY = LangEntry.iconBuilder("Leaderboards.UI.Leaderboard.Entry")
        .rawName(YELLOW.wrap("#" + GENERIC_POS) + " " + WHITE.wrap(PLAYER_NAME))
        .rawLore(GREEN.wrap(GENERIC_BALANCE))
        .build();
}
